package com.erfangholami.solidshare.worker

import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.notifications.NotificationsRepository
import com.erfangholami.solidshare.data.repo.settings.SettingsRepository
import com.erfangholami.solidshare.domain.model.NotificationItem
import com.erfangholami.solidshare.domain.model.NotificationKind
import com.erfangholami.solidshare.notification.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.net.URI
import java.time.Instant

@HiltWorker
class NotificationPollingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val notificationsRepository: NotificationsRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    private val nm by lazy {
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override suspend fun doWork(): Result {
        val webIds = authRepository.loggedInProfilesFlow.first().map { it.webId }
        webIds.forEach { webId -> runCatching { pollAccount(webId) } }
        return Result.success()
    }

    private suspend fun pollAccount(webId: String) {
        val items = notificationsRepository.listFeed(webId)
        val threshold = listOfNotNull(
            parseInstant(settingsRepository.getNotificationsLastSeen(webId).first()),
            parseInstant(settingsRepository.getNotificationsLastNotified(webId).first()),
        ).maxOrNull()

        if (threshold == null) {
            settingsRepository.setNotificationsLastNotified(webId, Instant.now().toString())
            return
        }

        val fresh = items
            .mapNotNull { item -> parseInstant(item.publishedAt)?.let { ts -> item to ts } }
            .filter { it.second.isAfter(threshold) }
        if (fresh.isEmpty()) return

        fresh.forEach { (item, _) -> if (item.kind.isAlertable()) notify(item) }
        settingsRepository.setNotificationsLastNotified(webId, fresh.maxOf { it.second }.toString())
    }

    private fun notify(item: NotificationItem) {
        val title = applicationContext.getString(
            titleResFor(item.kind),
            shortWebId(item.counterpartWebId),
        )
        nm.notify(
            item.id,
            NotificationHelper.NOTIFICATION_ID_SHARING,
            NotificationHelper.buildSharingNotification(
                context = applicationContext,
                title = title,
                text = resourceName(item.resourceUri),
                highPriority = item.kind == NotificationKind.ACCESS_REQUEST,
            ),
        )
    }

    private fun NotificationKind.isAlertable(): Boolean =
        this != NotificationKind.DECISION_GRANTED && this != NotificationKind.DECISION_REJECTED

    private fun titleResFor(kind: NotificationKind): Int = when (kind) {
        NotificationKind.ACCESS_OFFER -> R.string.notifications_offer_title
        NotificationKind.ACCESS_REVOKED -> R.string.notifications_revoked_title
        NotificationKind.REQUEST_REJECTED -> R.string.notifications_rejected_title
        NotificationKind.REQUEST_ACCEPTED -> R.string.notifications_accepted_title
        NotificationKind.ACCESS_REQUEST -> R.string.notifications_request_title
        NotificationKind.DECISION_GRANTED -> R.string.notifications_decision_granted_title
        NotificationKind.DECISION_REJECTED -> R.string.notifications_decision_rejected_title
    }

    private fun parseInstant(iso: String?): Instant? =
        iso?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun shortWebId(webId: String): String =
        runCatching { URI.create(webId).host ?: webId }.getOrDefault(webId)

    private fun resourceName(uri: String): String {
        val trimmed = uri.trimEnd('/')
        return trimmed.substringAfterLast('/', trimmed).ifBlank { uri }
    }

    companion object {
        const val WORK_NAME = "solidshare-notif-poll"
    }
}
