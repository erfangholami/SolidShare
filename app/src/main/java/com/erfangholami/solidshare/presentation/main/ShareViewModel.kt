package com.erfangholami.solidshare.presentation.main

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.file.FileRepository
import com.erfangholami.solidshare.data.repo.file.ResourceAccessException
import com.erfangholami.solidshare.data.repo.notifications.NotificationsRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingError
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.data.repo.sharing.toSharingError
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.PublicProfile
import com.erfangholami.solidshare.domain.model.ReceivedShare
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareReceiver
import com.erfangholami.solidshare.presentation.sharing.displayNameForUri
import com.erfangholami.solidshare.presentation.sharing.isContainerUri
import com.erfangholami.solidshare.presentation.sharing.toSharingErrorMessage
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val stringProvider: StringProvider,
    private val authRepository: AuthRepository,
    private val sharingRepository: SharingRepository,
    private val notificationsRepository: NotificationsRepository,
    private val fileRepository: FileRepository,
) : ViewModel() {

    data class UiError(
        val message: String,
        val action: ErrorAction? = null,
    )

    sealed interface ErrorAction {
        data object Retry : ErrorAction
        data class RequestAccess(
            val resourceUri: String,
            val ownerWebId: String?,
        ) : ErrorAction
    }

    @Immutable
    data class UiState(
        val given: List<GivenShare> = emptyList(),
        val received: List<ReceivedShare> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val error: UiError? = null,
        val notice: String? = null,
        val reshareable: Set<String> = emptySet(),
    )

    sealed interface OpenEvent {
        data class OpenFile(val file: File, val mimeType: String) : OpenEvent
        data class BrowseContainer(
            val containerUrl: String,
            val ownerWebId: String,
        ) : OpenEvent
    }

    data class ReshareLink(
        val title: String,
        val deepLink: String,
        val bareUrl: String,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _openEvent = MutableSharedFlow<OpenEvent>()
    val openEvent: SharedFlow<OpenEvent> = _openEvent.asSharedFlow()

    private val _reshareLink = MutableSharedFlow<ReshareLink>()
    val reshareLink: SharedFlow<ReshareLink> = _reshareLink.asSharedFlow()

    private val _isOpening = MutableStateFlow(false)
    val isOpening: StateFlow<Boolean> = _isOpening.asStateFlow()

    private val _lostAccessShare = MutableStateFlow<ReceivedShare?>(null)
    val lostAccessShare: StateFlow<ReceivedShare?> = _lostAccessShare.asStateFlow()

    data class NoAccessTarget(val resourceUri: String, val ownerWebId: String?)

    private val _noAccessShare = MutableStateFlow<NoAccessTarget?>(null)
    val noAccessShare: StateFlow<NoAccessTarget?> = _noAccessShare.asStateFlow()

    private var pendingRetry: (suspend () -> Unit)? = null

    val activeProfile: StateFlow<PublicProfile?> = authRepository.activeProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var lastWebId: String? = null

    private var loadJob: Job? = null

    private val reshareableProbe = ConcurrentHashMap<String, Boolean>()

    init {
        viewModelScope.launch {
            authRepository.activeWebIdFlow
                .filterNotNull()
                .distinctUntilChanged()
                .collect { webId ->
                    val isSwitch = lastWebId != null && lastWebId != webId
                    lastWebId = webId
                    if (isSwitch) {
                        _uiState.value = UiState()
                        reshareableProbe.clear()
                    }
                    load()
                }
        }
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val given = sharingRepository.getStoredGivenShares(webId)
                val received = sharingRepository.getStoredReceivedShares(webId)
                _uiState.value = _uiState.value.copy(
                    given = given,
                    received = received,
                    isLoading = false,
                )
                refreshReshareable(webId, received)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                fail(e, retry = ::load)
            }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            reshareableProbe.clear()
            try {
                val given = sharingRepository.refreshGivenShares(webId)
                val received = sharingRepository.refreshReceivedShares(webId)
                _uiState.value = _uiState.value.copy(
                    given = given,
                    received = received,
                    isRefreshing = false,
                )
                refreshReshareable(webId, received)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
                fail(e, retry = ::refresh)
            }
        }
    }

    fun rebuildIndex() {
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            try {
                val given = sharingRepository.rebuildGivenIndex(webId)
                val received = sharingRepository.getStoredReceivedShares(webId)
                _uiState.value = _uiState.value.copy(
                    given = given,
                    received = received,
                    isRefreshing = false,
                    notice = stringProvider.getString(R.string.notice_index_rebuilt),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
                fail(e, retry = ::rebuildIndex)
            }
        }
    }

    suspend fun createShareSuspend(
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
    ): GivenShare {
        val webId = authRepository.getActiveWebId() ?: error("Not signed in")
        val share = sharingRepository.createShare(webId, resourceUri, mode, receiver)
        load()
        return share
    }

    fun revokeShare(share: GivenShare) {
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            try {
                sharingRepository.revokeShare(webId, share.resourceUri, share.receiver)
                load()
            } catch (e: Exception) {
                fail(e, retry = { revokeShare(share) })
            }
        }
    }

    fun addReceivedShareFromUrl(rawUrl: String) {
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            val parsed = sharingRepository.parseDeepLink(rawUrl)
            val resourceUri = parsed?.resourceUri ?: rawUrl
            try {
                val received =
                    sharingRepository.addReceivedShare(webId, resourceUri, parsed?.ownerWebId)
                if (received != null) {
                    load()
                    _uiState.value = _uiState.value.copy(
                        error = null,
                        notice = stringProvider.getString(R.string.added_to_shares),
                    )
                } else {
                    _noAccessShare.value = NoAccessTarget(resourceUri, parsed?.ownerWebId)
                }
            } catch (e: SharingError.AccessDenied) {
                _noAccessShare.value = NoAccessTarget(e.resourceUri, e.ownerWebId)
            } catch (e: Exception) {
                fail(e, retry = { addReceivedShareFromUrl(rawUrl) })
            }
        }
    }

    fun confirmRequestAccessForNoAccess() {
        val target = _noAccessShare.value ?: return
        _noAccessShare.value = null
        requestAccess(target.resourceUri, target.ownerWebId)
    }

    fun dismissNoAccessShare() {
        _noAccessShare.value = null
    }

    fun removeReceivedShare(share: ReceivedShare) {
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            try {
                sharingRepository.removeReceivedShare(
                    webId, share.resourceUri, share.ownerWebId,
                )
                load()
            } catch (e: Exception) {
                fail(e, retry = { removeReceivedShare(share) })
            }
        }
    }

    private fun refreshReshareable(webId: String, shares: List<ReceivedShare>) {
        viewModelScope.launch {
            val eligible = shares.map { share ->
                async {
                    val canShare = reshareableProbe[share.resourceUri] ?: run {
                        runCatching {
                            fileRepository.probeAccess(webId, share.resourceUri).canShareOnward
                        }.getOrNull()?.also { reshareableProbe[share.resourceUri] = it } ?: false
                    }
                    share.resourceUri.takeIf { canShare }
                }
            }.awaitAll().filterNotNull().toSet()
            if (lastWebId == webId) {
                _uiState.value = _uiState.value.copy(reshareable = eligible)
            }
        }
    }

    fun reshareReceivedShare(share: ReceivedShare) {
        viewModelScope.launch {
            _reshareLink.emit(
                ReshareLink(
                    title = displayNameForUri(share.resourceUri),
                    deepLink = reshareLinkFor(share.resourceUri, share.ownerWebId),
                    bareUrl = bareUrlFor(share.resourceUri),
                ),
            )
        }
    }

    fun openReceivedShare(share: ReceivedShare) {
        if (_isOpening.value) return
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            _isOpening.value = true
            try {
                if (isContainerUri(share.resourceUri)) {
                    fileRepository.probeAccess(webId, share.resourceUri)
                    _openEvent.emit(
                        OpenEvent.BrowseContainer(share.resourceUri, share.ownerWebId),
                    )
                } else {
                    val downloaded = fileRepository.downloadFile(webId, share.resourceUri)
                    _openEvent.emit(OpenEvent.OpenFile(File(downloaded.path), downloaded.mimeType))
                }
            } catch (e: ResourceAccessException.AccessDenied) {
                _lostAccessShare.value = share
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    notice = e.message ?: stringProvider.getString(R.string.couldnt_open_item),
                )
            } finally {
                _isOpening.value = false
            }
        }
    }

    fun confirmRequestAccessForLostShare() {
        val share = _lostAccessShare.value ?: return
        _lostAccessShare.value = null
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            try {
                notificationsRepository.sendRequest(
                    requesterWebId = webId,
                    ownerWebId = share.ownerWebId,
                    resourceUri = share.resourceUri,
                    requestedMode = share.mode,
                )
                dropReceivedShare(webId, share)
                _uiState.value = _uiState.value.copy(
                    error = null,
                    notice = stringProvider.getString(R.string.access_request_sent_and_removed),
                )
            } catch (e: Exception) {
                dropReceivedShare(webId, share)
                fail(e, retry = null)
            }
        }
    }

    fun dismissLostAccessShare() {
        val share = _lostAccessShare.value ?: return
        _lostAccessShare.value = null
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            dropReceivedShare(webId, share)
            _uiState.value = _uiState.value.copy(notice = stringProvider.getString(R.string.removed_from_your_list))
        }
    }

    private suspend fun dropReceivedShare(webId: String, share: ReceivedShare) {
        _uiState.value = _uiState.value.copy(
            received = _uiState.value.received.filterNot {
                it.resourceUri == share.resourceUri && it.ownerWebId == share.ownerWebId
            },
        )
        runCatching {
            sharingRepository.removeReceivedShare(webId, share.resourceUri, share.ownerWebId)
        }
    }

    fun retry() {
        val op = pendingRetry ?: return
        pendingRetry = null
        _uiState.value = _uiState.value.copy(error = null)
        viewModelScope.launch { op() }
    }

    fun requestAccess(resourceUri: String, ownerWebId: String?) {
        if (ownerWebId == null) return
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            try {
                notificationsRepository.sendRequest(
                    requesterWebId = webId,
                    ownerWebId = ownerWebId,
                    resourceUri = resourceUri,
                    requestedMode = ShareMode.READ,
                )
                _uiState.value = _uiState.value.copy(
                    error = null,
                    notice = stringProvider.getString(R.string.access_request_sent),
                )
            } catch (e: Exception) {
                fail(e, retry = { requestAccess(resourceUri, ownerWebId) })
            }
        }
    }

    fun deepLinkFor(resourceUri: String): String =
        sharingRepository.deepLinkFor(resourceUri, lastWebId)

    fun reshareLinkFor(resourceUri: String, ownerWebId: String?): String =
        sharingRepository.deepLinkFor(resourceUri, ownerWebId)

    fun bareUrlFor(resourceUri: String): String =
        sharingRepository.bareUrlFor(resourceUri)

    fun clearError() {
        pendingRetry = null
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearNotice() {
        _uiState.value = _uiState.value.copy(notice = null)
    }

    fun showNotice(message: String) {
        _uiState.value = _uiState.value.copy(notice = message)
    }

    private fun fail(e: Throwable, retry: (suspend () -> Unit)?) {
        val action = when (val error = e.toSharingError()) {
            is SharingError.AccessDenied ->
                ErrorAction.RequestAccess(error.resourceUri, error.ownerWebId)

            is SharingError.StaleAcl,
            is SharingError.AccessIndeterminate,
            is SharingError.IncompleteScan ->
                retry?.let { ErrorAction.Retry }

            else -> null
        }
        pendingRetry = if (action is ErrorAction.Retry) retry else null
        _uiState.value = _uiState.value.copy(
            error = UiError(e.toSharingErrorMessage(stringProvider), action),
        )
    }
}
