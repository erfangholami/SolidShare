package com.erfangholami.solidshare.data.repo.notifications

import android.util.Log
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.settings.SettingsRepository
import com.erfangholami.solidshare.domain.model.NotificationItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class NotificationsBadgeState(
    val unreadCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class NotificationsBadgeStore @Inject constructor(
    private val authRepository: AuthRepository,
    private val notificationsRepository: NotificationsRepository,
    private val settingsRepository: SettingsRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshTrigger = MutableStateFlow(0L)
    private val ensuredInboxes = mutableSetOf<String>()

    val state: StateFlow<NotificationsBadgeState> =
        combine(itemsFlow(), lastSeenFlow()) { items, lastSeen ->
            NotificationsBadgeState(unreadCount = unreadCount(items, lastSeen))
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000L), NotificationsBadgeState())

    fun refresh() {
        refreshTrigger.value += 1L
    }

    suspend fun markSeen(webId: String, items: List<NotificationItem>) {
        settingsRepository.setNotificationsLastSeen(webId, seenStampFor(items))
    }

    private fun itemsFlow(): Flow<List<NotificationItem>> =
        combine(authRepository.activeWebIdFlow, refreshTrigger) { webId, _ -> webId }
            .flatMapLatest { webId ->
                if (webId == null) {
                    flowOf(emptyList())
                } else {
                    flow {
                        ensureInbox(webId)
                        emit(notificationsRepository.listFeed(webId))
                    }
                        .catch { throwable ->
                            if (throwable is CancellationException) throw throwable
                            Log.w(
                                LOG_TAG,
                                "Failed to pull notifications for $webId; the badge stays at its " +
                                        "last value until the next refresh.",
                                throwable,
                            )
                            emit(emptyList())
                        }
                }
            }

    private suspend fun ensureInbox(webId: String) {
        if (!ensuredInboxes.add(webId)) return
        runCatching { notificationsRepository.ensureInbox(webId) }
            .onFailure { throwable ->
                ensuredInboxes.remove(webId)
                if (throwable is CancellationException) throw throwable
                Log.w(
                    LOG_TAG,
                    "ensureInbox failed for $webId; the inbox may be unprovisioned, so share " +
                            "notifications can't be sent to or received by this account.",
                    throwable,
                )
            }
    }

    private fun lastSeenFlow(): Flow<String?> =
        authRepository.activeWebIdFlow
            .flatMapLatest { webId ->
                if (webId == null) {
                    flowOf(null)
                } else {
                    settingsRepository.getNotificationsLastSeen(webId)
                        .catch { throwable ->
                            if (throwable is CancellationException) throw throwable
                            Log.w(
                                LOG_TAG,
                                "Failed to read the last-seen marker for $webId; treating every " +
                                        "notification as unread so the badge errs visible.",
                                throwable,
                            )
                            emit(null)
                        }
                }
            }

    private fun unreadCount(items: List<NotificationItem>, lastSeenIso: String?): Int {
        val lastSeen = parseInstantOrNull(lastSeenIso)
        return items.count { item ->
            val published = parseInstantOrNull(item.publishedAt)
            when {
                published == null -> lastSeen == null
                lastSeen == null -> true
                else -> published.isAfter(lastSeen)
            }
        }
    }

    private fun seenStampFor(items: List<NotificationItem>): String {
        val now = Instant.now()
        val newest = items.mapNotNull { parseInstantOrNull(it.publishedAt) }.maxOrNull()
        val stamp = if (newest != null && newest.isAfter(now)) newest else now
        return stamp.toString()
    }

    private fun parseInstantOrNull(iso: String?): Instant? =
        iso?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private companion object {
        private const val LOG_TAG = "NotificationsBadge"
    }
}
