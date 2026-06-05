package com.erfangholami.solidshare.presentation.sharing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.file.FileRepository
import com.erfangholami.solidshare.data.repo.file.ResourceAccessException
import com.erfangholami.solidshare.data.repo.notifications.NotificationsRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingError
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.presentation.navigation.ConfirmAccessRoute
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConfirmAccessViewModel @Inject constructor(
    private val stringProvider: StringProvider,
    private val authRepository: AuthRepository,
    private val sharingRepository: SharingRepository,
    private val fileRepository: FileRepository,
    private val notificationsRepository: NotificationsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ConfirmAccessRoute>()
    val resourceUri: String = route.resourceUri
    val ownerWebId: String? = route.ownerWebId

    sealed class State {
        data object Checking : State()
        data object Owned : State()
        data object HasAccess : State()
        data object Adding : State()
        data object Added : State()
        data class NoAccess(val ownerWebId: String?) : State()
        data object RequestSent : State()
        data class Failure(val message: String, val canRetry: Boolean) : State()
    }

    private val _state = MutableStateFlow<State>(State.Checking)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        check()
    }

    fun check() {
        viewModelScope.launch {
            _state.value = State.Checking
            val webId = authRepository.getActiveWebId()
            if (webId == null) {
                _state.value = State.Failure(
                    stringProvider.getString(R.string.not_signed_in),
                    canRetry = false,
                )
                return@launch
            }
            if (authRepository.ownsResource(webId, resourceUri)) {
                _state.value = State.Owned
                return@launch
            }
            try {
                fileRepository.probeAccess(webId, resourceUri)
                _state.value = State.HasAccess
            } catch (_: ResourceAccessException.AccessDenied) {
                _state.value = State.NoAccess(ownerWebId)
            } catch (e: Exception) {
                _state.value = State.Failure(e.toSharingErrorMessage(stringProvider), canRetry = true)
            }
        }
    }

    fun addToShares() {
        if (_state.value is State.Adding) return
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId()
            if (webId == null) {
                _state.value = State.Failure(
                    stringProvider.getString(R.string.not_signed_in),
                    canRetry = false,
                )
                return@launch
            }
            _state.value = State.Adding
            try {
                val received = sharingRepository.addReceivedShare(webId, resourceUri, ownerWebId)
                _state.value = if (received != null) State.Added else State.NoAccess(ownerWebId)
            } catch (e: SharingError.AccessDenied) {
                _state.value = State.NoAccess(e.ownerWebId ?: ownerWebId)
            } catch (e: Exception) {
                _state.value = State.Failure(e.toSharingErrorMessage(stringProvider), canRetry = true)
            }
        }
    }

    fun requestAccess(owner: String) {
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            _state.value = State.Adding
            try {
                notificationsRepository.sendRequest(
                    requesterWebId = webId,
                    ownerWebId = owner,
                    resourceUri = resourceUri,
                    requestedMode = ShareMode.READ,
                )
                _state.value = State.RequestSent
            } catch (e: Exception) {
                _state.value = State.Failure(e.toSharingErrorMessage(stringProvider), canRetry = false)
            }
        }
    }
}
