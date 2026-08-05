package com.erfangholami.solidshare.presentation.sharing

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.error.AppError
import com.erfangholami.solidshare.domain.error.asException
import com.erfangholami.solidshare.domain.error.AppOperation
import com.erfangholami.solidshare.domain.error.ErrorPresenter
import com.erfangholami.solidshare.domain.error.UiError
import com.erfangholami.solidshare.domain.error.rethrowIfCancellation
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareReceiver
import com.erfangholami.solidshare.presentation.navigation.ManageSharingRoute
import com.erfangholami.solidshare.util.NetworkMonitor
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageSharingViewModel @Inject constructor(
    private val stringProvider: StringProvider,
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val sharingRepository: SharingRepository,
    private val networkMonitor: NetworkMonitor,
    private val errors: ErrorPresenter,
) : ViewModel() {

    @Immutable
    data class OwnerInfo(val webId: String, val name: String?)

    sealed interface UiState {
        data object Loading : UiState
        data object Offline : UiState

        @Immutable
        data class Loaded(
            val owner: OwnerInfo?,
            val shares: List<GivenShare>,
        ) : UiState

        data class Error(val error: UiError) : UiState
    }

    private val route = savedStateHandle.toRoute<ManageSharingRoute>()
    val resourceUri: String = route.resourceUri
    val canManage: Boolean = route.canManage
    val resourceSubtitle: String? = route.resourceSubtitle

    private var ownerWebId: String? = null

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        load()
    }

    fun load() {
        if (!networkMonitor.currentlyOnline()) {
            _uiState.value = UiState.Offline
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val webId = authRepository.getActiveWebId() ?: run {
                    _uiState.value = UiState.Error(
                        errors.present(AppError.NoActiveAccount, AppOperation.LOAD_SHARES),
                    )
                    return@launch
                }
                ownerWebId = webId
                val ownerName = runCatching { authRepository.activeProfileFlow.first() }
                    .getOrNull()
                    ?.name
                    ?.takeIf { it.isNotBlank() }
                _uiState.value = UiState.Loaded(
                    owner = OwnerInfo(webId = webId, name = ownerName),
                    shares = sharingRepository.getGivenSharesForResource(webId, resourceUri),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    errors.present(e, AppOperation.LOAD_SHARES, origin = resourceUri),
                )
            }
        }
    }

    fun revoke(share: GivenShare) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val webId = requireActiveWebId()
                sharingRepository.revokeShare(webId, resourceUri, share.receiver)
                _messages.emit(stringProvider.getString(R.string.access_revoked))
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                _messages.emit(errors.message(e, AppOperation.REVOKE_SHARE, origin = resourceUri))
            }
            load()
        }
    }

    fun changeMode(share: GivenShare, mode: ShareMode) {
        if (share.mode == mode) return
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val webId = requireActiveWebId()
                sharingRepository.updateShare(webId, resourceUri, mode, share.receiver)
                _messages.emit(stringProvider.getString(R.string.access_updated))
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                _messages.emit(
                    errors.message(e, AppOperation.UPDATE_SHARE_ACCESS, origin = resourceUri),
                )
            }
            load()
        }
    }

    suspend fun createShareSuspend(
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
    ): GivenShare {
        val webId = requireActiveWebId()
        val share = sharingRepository.createShare(webId, resourceUri, mode, receiver)
        load()
        return share
    }

    private suspend fun requireActiveWebId(): String =
        authRepository.getActiveWebId() ?: throw AppError.NoActiveAccount.asException()

    fun deepLinkFor(resourceUri: String): String =
        sharingRepository.deepLinkFor(resourceUri, ownerWebId)

    fun bareUrlFor(resourceUri: String): String =
        sharingRepository.bareUrlFor(resourceUri)
}
