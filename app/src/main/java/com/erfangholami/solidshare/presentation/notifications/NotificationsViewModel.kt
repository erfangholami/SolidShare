package com.erfangholami.solidshare.presentation.notifications

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.notifications.NotificationsBadgeStore
import com.erfangholami.solidshare.data.repo.notifications.NotificationsRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.model.AccessGrantDirection
import com.erfangholami.solidshare.domain.model.NotificationItem
import com.erfangholami.solidshare.domain.model.NotificationKind
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareRequest
import com.erfangholami.solidshare.presentation.sharing.toSharingErrorMessage
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val stringProvider: StringProvider,
    private val authRepository: AuthRepository,
    private val notificationsRepository: NotificationsRepository,
    private val sharingRepository: SharingRepository,
    private val badgeStore: NotificationsBadgeStore,
) : ViewModel() {

    data class Row(
        val item: NotificationItem,
        val alreadyGranted: Boolean,
    )

    @Immutable
    data class UiState(
        val rows: List<Row> = emptyList(),
        val pendingUris: Set<String> = emptySet(),
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val infoMessage: String? = null,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var currentWebId: String? = null
    private var loadJob: Job? = null
    private val ensuredInboxes = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            authRepository.activeWebIdFlow
                .filterNotNull()
                .distinctUntilChanged()
                .collect { webId ->
                    val isSwitch = currentWebId != null && currentWebId != webId
                    currentWebId = webId
                    if (isSwitch) _uiState.value = UiState()
                    load(webId, isRefresh = false)
                }
        }
    }

    fun refresh() {
        val webId = currentWebId ?: return
        load(webId, isRefresh = true)
    }

    private fun load(webId: String, isRefresh: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = !isRefresh,
                isRefreshing = isRefresh,
                error = null,
            )
            try {
                ensureInbox(webId)
                val items = notificationsRepository.listFeed(webId)
                val grantedRequestKeys = sharingRepository.getAccessGrants(webId)
                    .filter { it.direction == AccessGrantDirection.GIVEN }
                    .map { requestKey(it.counterpartWebId, it.resourceUri) }
                    .toSet()
                val rows = items.map { item ->
                    Row(
                        item = item,
                        alreadyGranted = item.kind == NotificationKind.ACCESS_REQUEST &&
                                requestKey(
                                    item.counterpartWebId,
                                    item.resourceUri
                                ) in grantedRequestKeys,
                    )
                }.sortedByDescending { it.actionNeeded() }
                _uiState.value = _uiState.value.copy(
                    rows = rows,
                    isLoading = false,
                    isRefreshing = false,
                )
                markSeen(webId, items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.toSharingErrorMessage(stringProvider),
                )
            }
        }
    }

    fun accept(item: NotificationItem) {
        val webId = currentWebId ?: return
        val request = item.toShareRequest() ?: return
        viewModelScope.launch {
            markPending(item.id, true)
            try {
                sharingRepository.acceptShareRequest(webId, request)
                removeFromInboxAndState(webId, item.id)
                _uiState.value = _uiState.value.copy(
                    infoMessage = stringProvider.getString(
                        R.string.notif_granted_access,
                        request.requestedMode.name.lowercase(),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toSharingErrorMessage(stringProvider))
            } finally {
                markPending(item.id, false)
            }
        }
    }

    fun reject(item: NotificationItem, reason: String?) {
        val webId = currentWebId ?: return
        val request = item.toShareRequest() ?: return
        viewModelScope.launch {
            markPending(item.id, true)
            try {
                sharingRepository.rejectShareRequest(webId, request, reason)
                removeFromInboxAndState(webId, item.id)
                _uiState.value =
                    _uiState.value.copy(infoMessage = stringProvider.getString(R.string.notif_request_rejected))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toSharingErrorMessage(stringProvider))
            } finally {
                markPending(item.id, false)
            }
        }
    }

    fun dismiss(item: NotificationItem) {
        val webId = currentWebId ?: return
        viewModelScope.launch {
            markPending(item.id, true)
            try {
                notificationsRepository.deleteNotification(webId, item.id)
                _uiState.value = _uiState.value.copy(
                    rows = _uiState.value.rows.filterNot { it.item.id == item.id },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toSharingErrorMessage(stringProvider))
            } finally {
                markPending(item.id, false)
            }
        }
    }

    private suspend fun removeFromInboxAndState(webId: String, id: String) {
        runCatching { notificationsRepository.deleteNotification(webId, id) }
        _uiState.value = _uiState.value.copy(
            rows = _uiState.value.rows.filterNot { it.item.id == id },
        )
    }

    private suspend fun ensureInbox(webId: String) {
        if (webId in ensuredInboxes) return
        runCatching { notificationsRepository.ensureInbox(webId) }
            .onSuccess { ensuredInboxes.add(webId) }
            .onFailure { e ->
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(error = e.toSharingErrorMessage(stringProvider))
            }
    }

    private suspend fun markSeen(webId: String, items: List<NotificationItem>) {
        try {
            badgeStore.markSeen(webId, items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = e.toSharingErrorMessage(stringProvider))
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun markPending(id: String, pending: Boolean) {
        val current = _uiState.value.pendingUris
        _uiState.value = _uiState.value.copy(
            pendingUris = if (pending) current + id else current - id,
        )
    }

    private fun Row.actionNeeded(): Boolean =
        item.kind == NotificationKind.ACCESS_REQUEST && !alreadyGranted

    private fun NotificationItem.toShareRequest(): ShareRequest? {
        val uri = requestUri ?: return null
        return ShareRequest(
            requestUri = uri,
            requesterWebId = counterpartWebId,
            resourceUri = resourceUri,
            requestedMode = mode ?: ShareMode.READ,
            summary = summary,
            publishedAt = publishedAt,
        )
    }

    private fun requestKey(counterpartWebId: String, resourceUri: String): String =
        "$counterpartWebId|$resourceUri"
}
