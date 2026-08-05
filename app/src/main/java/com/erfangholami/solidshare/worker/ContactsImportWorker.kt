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
import com.erfangholami.solidshare.data.repo.contacts.ContactMergeEngine
import com.erfangholami.solidshare.data.repo.contacts.ContactsRepository
import com.erfangholami.solidshare.domain.error.AppOperation
import com.erfangholami.solidshare.domain.error.ErrorPresenter
import com.erfangholami.solidshare.domain.error.rethrowIfCancellation
import com.erfangholami.solidshare.notification.NotificationHelper
import com.erfangholami.solidshare.sync.ContactsAccountManager
import com.erfangholami.solidshare.util.VCardReader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ContactsImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val contactsRepository: ContactsRepository,
    private val mergeEngine: ContactMergeEngine,
    private val contactsAccountManager: ContactsAccountManager,
    private val errors: ErrorPresenter,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_WEB_ID = "webId"
        const val KEY_SOURCE_URI = "sourceUri"
    }

    private val nm by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override suspend fun doWork(): Result {
        val webId = inputData.getString(KEY_WEB_ID) ?: return Result.failure()
        val sourceUri = inputData.getString(KEY_SOURCE_URI)?.let(Uri::parse)
            ?: return Result.failure()

        setForeground(foregroundInfo(webId, indeterminateText(), 0, 0))

        val text = runCatching {
            applicationContext.contentResolver.openInputStream(sourceUri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull()
        val cards = text?.let { runCatching { VCardReader.parse(it) }.getOrNull() }.orEmpty()
        if (cards.isEmpty()) {
            postComplete(
                webId,
                applicationContext.getString(R.string.contacts_import_complete_title),
                applicationContext.getString(R.string.contacts_vcf_invalid),
                openContacts = false,
            )
            return Result.success()
        }

        return try {
            val bookUri = contactsRepository.ensureDefaultAddressBook(webId)
            val existing = runCatching { contactsRepository.getAllContacts(webId, bookUri) }
                .getOrDefault(emptyList())
            val existingIdentities = existing
                .map { it to mergeEngine.matchKeys(it) }
                .filter { it.second.isNotEmpty() }
                .map { identityKey(it.first.fullName, it.second) }
                .toSet()

            var imported = 0
            var skipped = 0
            cards.forEachIndexed { index, card ->
                val draft = card.draft
                val keys = mergeEngine.matchKeys(draft)
                val identity = identityKey(displayName(draft.fullName, draft.givenName, draft.familyName), keys)
                if (keys.isNotEmpty() && identity in existingIdentities) {
                    skipped++
                } else {
                    runCatching {
                        val created = contactsRepository.createContact(webId, bookUri, draft)
                        if (card.photo != null) {
                            contactsRepository.setContactPhoto(
                                webId,
                                created.uri,
                                card.photo,
                                card.photoMime ?: "image/jpeg",
                            )
                        }
                        imported++
                    }
                }
                setForeground(
                    foregroundInfo(
                        webId,
                        applicationContext.getString(
                            R.string.contacts_import_progress,
                            index + 1,
                            cards.size,
                        ),
                        index + 1,
                        cards.size,
                    ),
                )
            }

            contactsAccountManager.requestSync(webId)
            val duplicates = runCatching {
                contactsRepository.findMergeSuggestions(webId).size
            }.getOrDefault(0)

            val summary = applicationContext.getString(R.string.contacts_import_done, imported, skipped)
            val completeText = if (duplicates > 0) {
                summary + " · " + applicationContext.getString(R.string.contacts_merge_found, duplicates)
            } else {
                summary
            }
            postComplete(
                webId,
                applicationContext.getString(R.string.contacts_import_complete_title),
                completeText,
                openContacts = duplicates > 0,
            )
            Result.success()
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            val failure = errors.present(e, AppOperation.IMPORT_CONTACTS)
            postComplete(webId, failure.title, failure.message, openContacts = false)
            Result.failure()
        }
    }

    private fun displayName(fullName: String?, given: String?, family: String?): String? =
        fullName?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(given, family).joinToString(" ").takeIf { it.isNotBlank() }

    private fun identityKey(name: String?, keys: Set<String>): String =
        mergeEngine.normalizeName(name) + "|" + keys.sorted().joinToString(",")

    private fun indeterminateText(): String =
        applicationContext.getString(R.string.contacts_import_progress, 0, 0)

    private fun postComplete(webId: String, title: String, text: String, openContacts: Boolean) {
        if (!NotificationHelper.canPost(applicationContext)) return
        nm.notify(
            NotificationHelper.idFor(NotificationHelper.NOTIFICATION_ID_CONTACTS_IMPORT_COMPLETE, webId),
            NotificationHelper.buildContactsCompleteNotification(
                applicationContext, title, text, openContacts, webId,
            ),
        )
    }

    private fun foregroundInfo(webId: String, text: String, current: Int, total: Int): ForegroundInfo {
        val notification = NotificationHelper.buildContactsProgressNotification(
            applicationContext,
            applicationContext.getString(R.string.contacts_import_notification_title),
            text,
            current,
            total,
            webId,
        )
        val id = NotificationHelper.idFor(
            NotificationHelper.NOTIFICATION_ID_CONTACTS_IMPORT_PROGRESS,
            webId,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }
}
