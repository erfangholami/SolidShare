package com.erfangholami.solidshare.worker

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.erfangholami.solidshare.data.repo.outbox.OutboxQueue
import com.erfangholami.solidshare.data.repo.outbox.OutboxTrigger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerOutboxTrigger @Inject constructor(
    private val workManager: WorkManager,
) : OutboxTrigger {

    override fun requestDrain(queue: OutboxQueue) {
        when (queue) {
            OutboxQueue.FILES -> enqueue<OutboxWorker>(OutboxWorker.WORK_NAME, ExistingWorkPolicy.KEEP)
            OutboxQueue.DATA_MODULES -> enqueue<ModuleOutboxWorker>(
                ModuleOutboxWorker.WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
            )
        }
    }

    private inline fun <reified T : androidx.work.ListenableWorker> enqueue(
        workName: String,
        policy: ExistingWorkPolicy,
    ) {
        val request = OneTimeWorkRequestBuilder<T>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        workManager.enqueueUniqueWork(workName, policy, request)
    }
}
