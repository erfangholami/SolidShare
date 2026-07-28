package com.erfangholami.solidshare

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.notification.NotificationHelper
import com.erfangholami.solidshare.sync.SolidAccountManager
import com.erfangholami.solidshare.worker.ContactOutboxWorker
import com.erfangholami.solidshare.worker.NotificationPollingWorker
import com.erfangholami.solidshare.worker.OutboxWorker
import com.erfangholami.solidshare.worker.PassRefreshWorker
import com.erfangholami.solidshare.worker.TicketOutboxWorker
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@HiltAndroidApp
class SolidShareApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var workManager: Lazy<WorkManager>

    @Inject
    lateinit var authRepository: Lazy<AuthRepository>

    @Inject
    lateinit var solidAccountManager: Lazy<SolidAccountManager>

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        scheduleNotificationPolling()
        scheduleOutboxDrain()
        ContactOutboxWorker.enqueue(workManager.get())
        TicketOutboxWorker.enqueue(workManager.get())
        PassRefreshWorker.schedulePeriodic(workManager.get())
        reconcileSolidAccounts()
    }

    private fun scheduleNotificationPolling() {
        val request = PeriodicWorkRequestBuilder<NotificationPollingWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        workManager.get().enqueueUniquePeriodicWork(
            NotificationPollingWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun scheduleOutboxDrain() {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        workManager.get().enqueueUniqueWork(
            OutboxWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun reconcileSolidAccounts() {
        applicationScope.launch {
            combine(
                authRepository.get().loggedInProfilesFlow,
                authRepository.get().expiredProfilesFlow,
            ) { loggedIn, expired ->
                (loggedIn + expired).map { it.webId }.distinct()
            }.collect { webIds ->
                runCatching {
                    solidAccountManager.get().reconcile(webIds)
                }
            }
        }
    }
}
