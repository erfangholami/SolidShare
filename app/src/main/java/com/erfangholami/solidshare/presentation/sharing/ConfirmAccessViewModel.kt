package com.erfangholami.solidshare.presentation.sharing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.file.FileRepository
import com.erfangholami.solidshare.data.repo.notifications.NotificationsRepository
import com.erfangholami.solidshare.data.repo.sharing.SharedEntityTypes
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.error.AppError
import com.erfangholami.solidshare.domain.error.AppOperation
import com.erfangholami.solidshare.domain.error.ErrorPresenter
import com.erfangholami.solidshare.domain.error.UiError
import com.erfangholami.solidshare.domain.error.rethrowIfCancellation
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.presentation.navigation.ConfirmAccessRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConfirmAccessViewModel @Inject constructor(
    private val errors: ErrorPresenter,
    private val authRepository: AuthRepository,
    private val sharingRepository: SharingRepository,
    private val fileRepository: FileRepository,
    private val notificationsRepository: NotificationsRepository,
    private val entityRegistry: SharedEntityRegistry,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ConfirmAccessRoute>()
    val resourceUri: String = route.resourceUri
    val ownerWebId: String? = route.ownerWebId
    val resourceType: String? = route.resourceType

    sealed class State {
        data object Checking : State()
        data object Owned : State()
        data object HasAccess : State()
        data object Adding : State()
        data object Added : State()
        data class NoAccess(val ownerWebId: String?) : State()
        data object RequestSent : State()
        data class Failure(val error: UiError) : State()
    }

    private val _state = MutableStateFlow<State>(State.Checking)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _requestedMode = MutableStateFlow(ShareMode.READ)
    val requestedMode: StateFlow<ShareMode> = _requestedMode.asStateFlow()

    fun setRequestedMode(mode: ShareMode) {
        _requestedMode.value = mode
    }

    init {
        check()
    }

    fun check() {
        viewModelScope.launch {
            _state.value = State.Checking
            val webId = authRepository.getActiveWebId()
            if (webId == null) {
                _state.value = State.Failure(
                    errors.present(AppError.NoActiveAccount, AppOperation.CHECK_ACCESS),
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
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                _state.value = when (val error = errors.classify(e, resourceUri)) {
                    is AppError.PermissionDenied ->
                        State.NoAccess(error.ownerWebId ?: ownerWebId)

                    else -> State.Failure(
                        errors.present(error, AppOperation.CHECK_ACCESS),
                    )
                }
            }
        }
    }

    fun addToShares() {
        if (_state.value is State.Adding) return
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId()
            if (webId == null) {
                _state.value = State.Failure(
                    errors.present(AppError.NoActiveAccount, AppOperation.ADD_RECEIVED_SHARE),
                )
                return@launch
            }
            _state.value = State.Adding
            try {
                val resourceName = resolveEntityName(webId)
                val received = sharingRepository.addReceivedShare(
                    webId, resourceUri, ownerWebId, resourceType, resourceName,
                )
                _state.value = if (received != null) State.Added else State.NoAccess(ownerWebId)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                _state.value = when (val error = errors.classify(e, resourceUri)) {
                    is AppError.PermissionDenied ->
                        State.NoAccess(error.ownerWebId ?: ownerWebId)

                    else -> State.Failure(
                        errors.present(error, AppOperation.ADD_RECEIVED_SHARE),
                    )
                }
            }
        }
    }

    private suspend fun resolveEntityName(webId: String): String? =
        entityRegistry.forType(resourceType)?.resolveName(webId, resourceUri)

    fun requestAccess(owner: String) {
        viewModelScope.launch {
            val webId = authRepository.getActiveWebId() ?: return@launch
            _state.value = State.Adding
            try {
                notificationsRepository.sendRequest(
                    requesterWebId = webId,
                    ownerWebId = owner,
                    resourceUri = resourceUri,
                    requestedMode = _requestedMode.value,
                )
                _state.value = State.RequestSent
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                _state.value = State.Failure(
                    errors.present(
                        e,
                        AppOperation.REQUEST_ACCESS,
                        subject = owner,
                        origin = resourceUri,
                        allowRetry = false,
                    ),
                )
            }
        }
    }
}
