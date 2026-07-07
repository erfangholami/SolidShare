package com.erfangholami.solidshare.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.erfangholami.solidshare.data.repo.outbox.OutboxRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class OutboxWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val outboxRepository: OutboxRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            outboxRepository.drain()
            if (outboxRepository.hasUnfinishedWork()) Result.retry() else Result.success()
        } catch (exception: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "solidshare-outbox"
    }
}
