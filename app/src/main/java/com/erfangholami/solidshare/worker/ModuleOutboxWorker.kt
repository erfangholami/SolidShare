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
import com.erfangholami.solidshare.data.repo.datamodule.DataModuleRegistry
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ModuleOutboxWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val registry: DataModuleRegistry,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        if (registry.drainPending()) Result.success() else Result.retry()

    companion object {
        const val WORK_NAME = "module_outbox"

        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<ModuleOutboxWorker>()
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
