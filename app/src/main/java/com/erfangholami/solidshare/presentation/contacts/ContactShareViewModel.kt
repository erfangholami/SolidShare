package com.erfangholami.solidshare.presentation.contacts

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.contacts.ContactsRepository
import com.erfangholami.solidshare.data.repo.sharing.SharedEntityTypes
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.error.AppError
import com.erfangholami.solidshare.domain.error.AppOperation
import com.erfangholami.solidshare.domain.error.ErrorPresenter
import com.erfangholami.solidshare.domain.error.UiError
import com.erfangholami.solidshare.domain.error.asException
import com.erfangholami.solidshare.domain.error.rethrowIfCancellation
import com.erfangholami.solidshare.domain.model.ContactDetail
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareReceiver
import com.erfangholami.solidshare.presentation.navigation.ContactSharingRoute
import com.erfangholami.solidshare.util.NetworkMonitor
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ContactShareViewModel @Inject constructor(
    private val stringProvider: StringProvider,
    private val errors: ErrorPresenter,
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val sharingRepository: SharingRepository,
    private val contactsRepository: ContactsRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data object Offline : UiState

        @Immutable
        data class Loaded(
            val contact: ContactDetail,
            val shareTarget: String,
            val people: List<GivenShare>,
        ) : UiState

        data class Error(val error: UiError) : UiState
    }

    private val route = savedStateHandle.toRoute<ContactSharingRoute>()

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
                val webId = authRepository.getActiveWebId()
                    ?: throw AppError.NoActiveAccount.asException()
                ownerWebId = webId
                val contact = contactsRepository.getContact(webId, route.contactUri)
                val shareTarget = contactsRepository.contactShareTarget(contact.uri)
                val people = sharingRepository.getGivenSharesForResource(webId, shareTarget)
                    .filter { it.receiver is ShareReceiver.WebIdReceiver }
                _uiState.value = UiState.Loaded(
                    contact = contact,
                    shareTarget = shareTarget,
                    people = people,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    errors.present(e, AppOperation.LOAD_SHARES, origin = route.contactUri),
                )
            }
        }
    }

    suspend fun addPersonSuspend(receiverWebId: String): GivenShare {
        val webId = authRepository.getActiveWebId()
                    ?: throw AppError.NoActiveAccount.asException()
        val loaded = _uiState.value as? UiState.Loaded ?: error("Not loaded")
        val share = sharingRepository.createShare(
            webId = webId,
            resourceUri = loaded.shareTarget,
            mode = ShareMode.READ,
            receiver = ShareReceiver.WebIdReceiver(receiverWebId),
            resourceType = SharedEntityTypes.CONTACT,
            resourceName = loaded.contact.fullName,
        )
        load()
        return share
    }

    fun revoke(share: GivenShare) {
        viewModelScope.launch {
            val loaded = _uiState.value as? UiState.Loaded ?: return@launch
            _uiState.value = UiState.Loading
            try {
                val webId = authRepository.getActiveWebId()
                    ?: throw AppError.NoActiveAccount.asException()
                sharingRepository.revokeShare(webId, loaded.shareTarget, share.receiver)
                _messages.emit(stringProvider.getString(R.string.access_revoked))
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                _messages.emit(
                    errors.message(e, AppOperation.REVOKE_SHARE, origin = route.contactUri),
                )
            }
            load()
        }
    }

    fun deepLinkFor(resourceUri: String): String =
        sharingRepository.deepLinkFor(resourceUri, ownerWebId, SharedEntityTypes.CONTACT)

    fun bareUrlFor(resourceUri: String): String =
        sharingRepository.bareUrlFor(resourceUri)
}
