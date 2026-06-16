package com.erfangholami.solidshare.presentation.main

import android.webkit.MimeTypeMap
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.file.FileRepository
import com.erfangholami.solidshare.data.repo.file.ResourceAccessException
import com.erfangholami.solidshare.data.repo.notifications.NotificationsBadgeStore
import com.erfangholami.solidshare.data.repo.notifications.NotificationsRepository
import com.erfangholami.solidshare.data.repo.sharing.ReceivedSharesSignal
import com.erfangholami.solidshare.data.repo.sharing.SharingError
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.data.repo.sharing.toSharingError
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.PublicProfile
import com.erfangholami.solidshare.domain.model.ReceivedShare
import com.erfangholami.solidshare.domain.model.ResourceAccess
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareReceiver
import com.erfangholami.solidshare.domain.model.getResourceType
import com.erfangholami.solidshare.presentation.sharing.displayNameForUri
import com.erfangholami.solidshare.presentation.sharing.isContainerUri
import com.erfangholami.solidshare.presentation.sharing.toSharingErrorMessage
import com.erfangholami.solidshare.util.MIME_TYPE_OCTET_STREAM
import com.erfangholami.solidshare.util.StringProvider
import com.erfangholami.solidshare.worker.DownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
import javax.inject.Inject

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val stringProvider: StringProvider,
    private val authRepository: AuthRepository,
    private val sharingRepository: SharingRepository,
    private val notificationsRepository: NotificationsRepository,
    private val fileRepository: FileRepository,
    private val badgeStore: NotificationsBadgeStore,
    private val receivedSharesSignal: ReceivedSharesSignal,
    private val workManager: WorkManager,
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
    )

    sealed interface OpenEvent {
        data class OpenFile(val file: File, val mimeType: String) : OpenEvent
        data class BrowseContainer(
            val containerUrl: String,
            val ownerWebId: String,
        ) : OpenEvent
    }

    data class ReshareLink(
        val resourceUri: String,
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

    private val _ownedResource = MutableStateFlow<String?>(null)
    val ownedResource: StateFlow<String?> = _ownedResource.asStateFlow()

    private var pendingRetry: (suspend () -> Unit)? = null

    val activeProfile: StateFlow<PublicProfile?> = authRepository.activeProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var lastWebId: String? = null

    private var loadJob: Job? = null

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
                    }
                    load()
                }
        }
        viewModelScope.launch {
            receivedSharesSignal.changed.collect { reloadReceivedSilently() }
        }
    }

    fun load() {
        badgeStore.refresh()
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                fail(e, retry = ::load)
            }
        }
    }

    fun refresh() {
        badgeStore.refresh()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            try {
                val given = sharingRepository.refreshGivenShares(webId)
                val received = sharingRepository.refreshReceivedShares(webId)
                _uiState.value = _uiState.value.copy(
                    given = given,
                    received = received,
                    isRefreshing = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
                fail(e, retry = ::refresh)
            }
        }
    }

    private fun reloadReceivedSilently() {
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            val received = runCatching {
                sharingRepository.getStoredReceivedShares(webId)
            }.getOrNull() ?: return@launch
            if (lastWebId != webId) return@launch
            _uiState.value = _uiState.value.copy(received = received)
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

    fun dismissNoAccessShare() {
        _noAccessShare.value = null
    }

    fun dismissOwnedResource() {
        _ownedResource.value = null
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

    fun reshareReceivedShare(share: ReceivedShare) {
        viewModelScope.launch {
            _reshareLink.emit(
                ReshareLink(
                    resourceUri = share.resourceUri,
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

    fun downloadReceivedShare(share: ReceivedShare) {
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            val fileName = displayNameForUri(share.resourceUri)
            val mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase())
                ?: MIME_TYPE_OCTET_STREAM
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(
                    workDataOf(
                        DownloadWorker.KEY_WEB_ID to webId,
                        DownloadWorker.KEY_FILE_URL to share.resourceUri,
                        DownloadWorker.KEY_FILE_NAME to fileName,
                        DownloadWorker.KEY_MIME_TYPE to mimeType,
                    ),
                )
                .build()
            workManager.enqueue(request)
        }
    }

    fun deleteReceivedShare(share: ReceivedShare) {
        if (share.mode != ShareMode.WRITE) {
            _uiState.value = _uiState.value.copy(
                notice = stringProvider.getString(R.string.error_no_permission_for_action),
            )
            return
        }
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            try {
                fileRepository.deleteResource(
                    webId, share.resourceUri, isContainerUri(share.resourceUri),
                )
                sharingRepository.removeReceivedShare(webId, share.resourceUri, share.ownerWebId)
                load()
            } catch (e: Exception) {
                fail(e, retry = { deleteReceivedShare(share) })
            }
        }
    }

    /** A minimal [ContainerItem] for the Info page of a received (non-owned) resource. */
    fun detailsItemFor(share: ReceivedShare): ContainerItem {
        val isContainer = isContainerUri(share.resourceUri)
        val name = displayNameForUri(share.resourceUri)
        val extension = if (isContainer) {
            null
        } else {
            name.substringAfterLast('.', "").lowercase().ifBlank { null }
        }
        return ContainerItem(
            identifier = share.resourceUri,
            isContainer = isContainer,
            name = name,
            extension = extension,
            mimeType = null,
            resourceType = getResourceType(isContainer, null, extension),
            resourceTypes = emptyList(),
            sizeBytes = null,
            lastModified = null,
            etag = null,
            access = accessFor(share.mode),
        )
    }

    /** Fetches a resource's size/modified/item-count and wraps it in a [ContainerItem] for a
     *  ⋮ sheet header (shared resources carry only a URL). Returns null on failure. */
    suspend fun resourceMetaItem(resourceUri: String): ContainerItem? {
        val webId = authRepository.getActiveWebId() ?: return null
        val meta = runCatching { fileRepository.getResourceMeta(webId, resourceUri) }
            .getOrNull() ?: return null
        val isContainer = isContainerUri(resourceUri)
        val name = displayNameForUri(resourceUri)
        val extension = if (isContainer) {
            null
        } else {
            name.substringAfterLast('.', "").lowercase().ifBlank { null }
        }
        return ContainerItem(
            identifier = resourceUri,
            isContainer = isContainer,
            name = name,
            extension = extension,
            mimeType = null,
            resourceType = getResourceType(isContainer, null, extension),
            resourceTypes = emptyList(),
            sizeBytes = meta.sizeBytes,
            lastModified = meta.lastModified,
            etag = null,
            itemCount = meta.itemCount,
        )
    }

    private fun accessFor(mode: ShareMode): ResourceAccess = ResourceAccess(
        canWrite = mode == ShareMode.WRITE,
        canControl = false,
        publicCanRead = false,
        canAppend = mode == ShareMode.WRITE || mode == ShareMode.APPEND,
    )

    /** Clears the lost-access dialog WITHOUT removing the share — used when opening the
     *  Confirm Access sheet to re-request access (the stale entry stays until re-granted). */
    fun clearLostAccessShare() {
        _lostAccessShare.value = null
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
