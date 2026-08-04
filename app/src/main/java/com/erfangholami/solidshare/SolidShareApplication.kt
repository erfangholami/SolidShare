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
import com.erfangholami.solidshare.sync.ContactsAccountManager
import com.erfangholami.solidshare.telemetry.AuthAnalytics
import com.erfangholami.solidshare.telemetry.TelemetryInstaller
import com.erfangholami.solidshare.worker.ModuleOutboxWorker
import com.erfangholami.solidshare.worker.NotificationPollingWorker
import com.erfangholami.solidshare.worker.OutboxWorker
import com.erfangholami.solidshare.worker.PassRefreshWorker
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
    lateinit var contactsAccountManager: Lazy<ContactsAccountManager>

    @Inject
    lateinit var telemetryInstaller: Lazy<TelemetryInstaller>

    @Inject
    lateinit var authAnalytics: Lazy<AuthAnalytics>

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        runCatching { telemetryInstaller.get().install() }
        NotificationHelper.createChannels(this)
        scheduleNotificationPolling()
        scheduleOutboxDrain()
        ModuleOutboxWorker.enqueue(workManager.get())
        PassRefreshWorker.schedulePeriodic(workManager.get())
        reconcileSolidAccounts()
        watchSessionExpiry()
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
                    contactsAccountManager.get().reconcile(webIds)
                }
            }
        }
    }

    private fun watchSessionExpiry() {
        applicationScope.launch {
            var knownExpired: Set<String>? = null
            combine(
                authRepository.get().loggedInProfilesFlow,
                authRepository.get().expiredProfilesFlow,
            ) { loggedIn, expired -> loggedIn to expired }.collect { (loggedIn, expired) ->
                runCatching {
                    authAnalytics.get().accountsSnapshot(loggedIn.size, expired.size)
                    val expiredIds = expired.map { it.webId }.toSet()
                    val previous = knownExpired
                    knownExpired = expiredIds
                    if (previous == null) return@runCatching
                    expired.filter { it.webId in expiredIds - previous }.forEach { profile ->
                        authAnalytics.get().sessionExpired(
                            AuthAnalytics.issuerHost(profile.oidcIssuer),
                            profile.sessionError,
                        )
                    }
                }
            }
        }
    }
}
