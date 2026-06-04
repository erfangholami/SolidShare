package com.erfangholami.solidshare.presentation.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.notifications.NotificationsRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingError
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanReceivedShareViewModel @Inject constructor(
    private val stringProvider: StringProvider,
    private val authRepository: AuthRepository,
    private val sharingRepository: SharingRepository,
    private val notificationsRepository: NotificationsRepository,
) : ViewModel() {

    sealed class State {
        data object Scanning : State()
        data object Verifying : State()
        data class Granted(val resourceUri: String, val mode: String, val ownerWebId: String) :
            State()

        data class NotGranted(val resourceUri: String, val ownerWebId: String?) : State()
        data class RequestSent(val resourceUri: String) : State()
        data class Failure(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Scanning)
    val state: StateFlow<State> = _state.asStateFlow()

    fun verify(rawUrl: String) {
        if (_state.value is State.Verifying) return
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId()
            if (webId == null) {
                _state.value = State.Failure(stringProvider.getString(R.string.not_signed_in))
                return@launch
            }
            val parsed = sharingRepository.parseDeepLink(rawUrl)
            val resourceUri = parsed?.resourceUri ?: rawUrl
            _state.value = State.Verifying
            try {
                val received =
                    sharingRepository.addReceivedShare(webId, resourceUri, parsed?.ownerWebId)
                _state.value = if (received != null) {
                    State.Granted(
                        resourceUri = received.resourceUri,
                        mode = received.mode.name.lowercase(),
                        ownerWebId = received.ownerWebId,
                    )
                } else {
                    State.NotGranted(resourceUri, ownerWebId = null)
                }
            } catch (e: SharingError.AccessDenied) {
                _state.value = State.NotGranted(resourceUri, ownerWebId = e.ownerWebId)
            } catch (e: Exception) {
                _state.value = State.Failure(e.toSharingErrorMessage(stringProvider))
            }
        }
    }

    fun requestAccess(resourceUri: String, ownerWebId: String) {
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            _state.value = State.Verifying
            try {
                notificationsRepository.sendRequest(
                    requesterWebId = webId,
                    ownerWebId = ownerWebId,
                    resourceUri = resourceUri,
                    requestedMode = ShareMode.READ,
                )
                _state.value = State.RequestSent(resourceUri)
            } catch (e: Exception) {
                _state.value = State.Failure(e.toSharingErrorMessage(stringProvider))
            }
        }
    }

    fun resetToScanning() {
        _state.value = State.Scanning
    }
}
