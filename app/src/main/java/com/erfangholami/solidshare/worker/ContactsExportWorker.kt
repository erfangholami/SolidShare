package com.erfangholami.solidshare.worker

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.contacts.ContactsRepository
import com.erfangholami.solidshare.domain.model.ContactDetail
import com.erfangholami.solidshare.notification.NotificationHelper
import com.erfangholami.solidshare.util.VCardWriter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ContactsExportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val contactsRepository: ContactsRepository,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_WEB_ID = "webId"
        const val KEY_DEST_URI = "destUri"
    }

    private val nm by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override suspend fun doWork(): Result {
        val webId = inputData.getString(KEY_WEB_ID) ?: return Result.failure()
        val destUri = inputData.getString(KEY_DEST_URI)?.let(Uri::parse) ?: return Result.failure()

        setForeground(foregroundInfo(0, 0))

        return try {
            val overview = contactsRepository.getOverview(webId)
            val contacts = overview.books.flatMap { book ->
                runCatching { contactsRepository.getAllContacts(webId, book.uri) }
                    .getOrDefault(emptyList())
            }
            val withPhotos = mutableListOf<Pair<ContactDetail, ByteArray?>>()
            contacts.forEachIndexed { index, contact ->
                val photo = contact.photoUri?.let { uri ->
                    runCatching { contactsRepository.getContactPhoto(webId, uri) }.getOrNull()
                }
                withPhotos.add(contact to photo)
                setForeground(foregroundInfo(index + 1, contacts.size))
            }
            val vcard = VCardWriter.write(withPhotos)
            val written = applicationContext.contentResolver.openOutputStream(destUri)?.use {
                it.write(vcard.toByteArray(Charsets.UTF_8))
                true
            } ?: false
            if (!written) error("no stream")
            postComplete(
                applicationContext.getString(R.string.contacts_export_complete_title),
                applicationContext.getString(R.string.contacts_export_saved),
            )
            Result.success()
        } catch (e: Exception) {
            postComplete(
                applicationContext.getString(R.string.contacts_export_complete_title),
                applicationContext.getString(R.string.contacts_export_failed),
            )
            Result.failure()
        }
    }

    private fun postComplete(title: String, text: String) {
        if (!NotificationHelper.canPost(applicationContext)) return
        nm.notify(
            NotificationHelper.NOTIFICATION_ID_CONTACTS_EXPORT_COMPLETE,
            NotificationHelper.buildContactsCompleteNotification(
                applicationContext, title, text, openContacts = false,
            ),
        )
    }

    private fun foregroundInfo(current: Int, total: Int): ForegroundInfo {
        val notification = NotificationHelper.buildContactsProgressNotification(
            applicationContext,
            applicationContext.getString(R.string.contacts_export_notification_title),
            applicationContext.getString(R.string.contacts_export_progress, current, total),
            current,
            total,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NotificationHelper.NOTIFICATION_ID_CONTACTS_EXPORT_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NotificationHelper.NOTIFICATION_ID_CONTACTS_EXPORT_PROGRESS, notification)
        }
    }
}
