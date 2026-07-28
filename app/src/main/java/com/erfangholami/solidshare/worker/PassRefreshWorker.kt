package com.erfangholami.solidshare.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.erfangholami.solidshare.data.local.cache.TicketDao
import com.erfangholami.solidshare.data.repo.tickets.TicketsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class PassRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val ticketsRepository: TicketsRepository,
    private val ticketDao: TicketDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val webIds = runCatching { ticketDao.webIds() }.getOrDefault(emptyList())
        webIds.forEach { webId ->
            runCatching { ticketsRepository.refreshIssuerPasses(webId) }
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "pass_refresh"
        private const val PERIODIC_WORK_NAME = "pass_refresh_periodic"

        private fun connected(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<PassRefreshWorker>()
                .setConstraints(connected())
                .build()
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        fun schedulePeriodic(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<PassRefreshWorker>(12, TimeUnit.HOURS)
                .setConstraints(connected())
                .build()
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
