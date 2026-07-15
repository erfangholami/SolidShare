package com.erfangholami.solidshare.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.erfangholami.solidshare.data.local.cache.ContactOutboxDao
import com.erfangholami.solidshare.data.repo.contacts.ContactsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ContactOutboxWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val contactsRepository: ContactsRepository,
    private val contactOutboxDao: ContactOutboxDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val webIds = runCatching { contactOutboxDao.pendingWebIds() }.getOrDefault(emptyList())
        if (webIds.isEmpty()) return Result.success()
        var allOk = true
        webIds.forEach { webId ->
            val ok = runCatching { contactsRepository.drainContactOutbox(webId) }.getOrDefault(false)
            if (!ok) allOk = false
        }
        return if (allOk) Result.success() else Result.retry()
    }

    companion object {
        const val WORK_NAME = "contact_outbox"

        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<ContactOutboxWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
