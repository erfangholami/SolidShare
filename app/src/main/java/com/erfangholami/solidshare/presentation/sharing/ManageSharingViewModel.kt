package com.erfangholami.solidshare.presentation.sharing

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareReceiver
import com.erfangholami.solidshare.presentation.navigation.ManageSharingRoute
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageSharingViewModel @Inject constructor(
    private val stringProvider: StringProvider,
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val sharingRepository: SharingRepository,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState

        @Immutable
        data class Loaded(val shares: List<GivenShare>) : UiState
        data class Error(val message: String) : UiState
    }

    private val route = savedStateHandle.toRoute<ManageSharingRoute>()
    val resourceUri: String = route.resourceUri
    val canManage: Boolean = route.canManage

    private var ownerWebId: String? = null

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val webId = authRepository.getActiveWebId() ?: error("Not signed in")
                ownerWebId = webId
                _uiState.value =
                    UiState.Loaded(sharingRepository.getGivenSharesForResource(webId, resourceUri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: stringProvider.getString(R.string.manage_load_failed))
            }
        }
    }

    fun revoke(share: GivenShare) {
        viewModelScope.launch {
            try {
                val webId = authRepository.getActiveWebId() ?: error("Not signed in")
                sharingRepository.revokeShare(webId, resourceUri, share.receiver)
                _messages.emit(stringProvider.getString(R.string.access_revoked))
                load()
            } catch (e: Exception) {
                _messages.emit(e.message ?: stringProvider.getString(R.string.error_revoke_access))
            }
        }
    }

    fun changeMode(share: GivenShare, mode: ShareMode) {
        if (share.mode == mode) return
        viewModelScope.launch {
            try {
                val webId = authRepository.getActiveWebId() ?: error("Not signed in")
                sharingRepository.updateShare(webId, resourceUri, mode, share.receiver)
                _messages.emit(stringProvider.getString(R.string.access_updated))
                load()
            } catch (e: Exception) {
                _messages.emit(e.message ?: stringProvider.getString(R.string.error_update_access))
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

    fun deepLinkFor(resourceUri: String): String =
        sharingRepository.deepLinkFor(resourceUri, ownerWebId)
}
