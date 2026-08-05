package com.erfangholami.solidshare.presentation.wallet

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.sharing.SharedEntityTypes
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.data.repo.tickets.TicketsRepository
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareReceiver
import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.presentation.navigation.TicketSharingRoute
import com.erfangholami.solidshare.util.NetworkMonitor
import com.erfangholami.solidshare.domain.error.AppError
import com.erfangholami.solidshare.domain.error.AppOperation
import com.erfangholami.solidshare.domain.error.ErrorPresenter
import com.erfangholami.solidshare.domain.error.UiError
import com.erfangholami.solidshare.domain.error.asException
import com.erfangholami.solidshare.domain.error.rethrowIfCancellation
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class TicketShareViewModel @Inject constructor(
    private val stringProvider: StringProvider,
    private val errors: ErrorPresenter,
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val sharingRepository: SharingRepository,
    private val ticketsRepository: TicketsRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    enum class PublicAvailability { AVAILABLE, NO_ARTIFACT, PROHIBITED }

    sealed interface UiState {
        data object Loading : UiState
        data object Offline : UiState

        @Immutable
        data class Loaded(
            val ticket: Ticket,
            val shareTarget: String,
            val people: List<GivenShare>,
            val publicEnabled: Boolean,
            val publicAvailability: PublicAvailability,
        ) : UiState

        data class Error(val error: UiError) : UiState
    }

    private val route = savedStateHandle.toRoute<TicketSharingRoute>()

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
                _uiState.value = loadedState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    errors.present(e, AppOperation.LOAD_SHARES),
                )
            }
        }
    }

    private suspend fun loadedState(): UiState.Loaded {
        val webId = authRepository.getActiveWebId()
                    ?: throw AppError.NoActiveAccount.asException()
        ownerWebId = webId
        val ticket = resolveTicket(webId, route.target)
        val shareTarget = ticketsRepository.ticketShareTarget(ticket.uri)
        val people = sharingRepository.getGivenSharesForResource(webId, shareTarget)
            .filter { it.receiver is ShareReceiver.WebIdReceiver }
        val publicAvailability = when {
            ticket.artifactUri == null -> PublicAvailability.NO_ARTIFACT
            ticket.passInfo?.sharingProhibited == true -> PublicAvailability.PROHIBITED
            else -> PublicAvailability.AVAILABLE
        }
        val publicEnabled = ticket.artifactUri?.let { artifactUri ->
            runCatching {
                sharingRepository.getGivenSharesForResource(webId, artifactUri)
                    .any { it.receiver is ShareReceiver.Public }
            }.getOrDefault(false)
        } ?: false
        return UiState.Loaded(
            ticket = ticket,
            shareTarget = shareTarget,
            people = people,
            publicEnabled = publicEnabled,
            publicAvailability = publicAvailability,
        )
    }

    private suspend fun resolveTicket(webId: String, target: String): Ticket {
        if (target.contains('#')) return ticketsRepository.getTicket(webId, target)
        val container = if (target.endsWith("/")) target else target.substringBeforeLast('/') + "/"
        val candidates = ticketsRepository.observeTickets(webId).first()
            .filter { it.uri.startsWith(container) }
        for (candidate in candidates) {
            val ticket = runCatching { ticketsRepository.getTicket(webId, candidate.uri) }
                .getOrNull() ?: continue
            if (target.endsWith("/") || ticket.artifactUri == target) return ticket
        }
        return ticketsRepository.getSharedTicket(webId, container)
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
            resourceType = SharedEntityTypes.TICKET,
            resourceName = loaded.ticket.title,
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
                _messages.emit(errors.message(e, AppOperation.REVOKE_SHARE))
            }
            load()
        }
    }

    fun setPublicPassLink(enabled: Boolean) {
        viewModelScope.launch {
            val loaded = _uiState.value as? UiState.Loaded ?: return@launch
            val artifactUri = loaded.ticket.artifactUri ?: return@launch
            _uiState.value = UiState.Loading
            try {
                val webId = authRepository.getActiveWebId()
                    ?: throw AppError.NoActiveAccount.asException()
                if (enabled) {
                    sharingRepository.createShare(
                        webId = webId,
                        resourceUri = artifactUri,
                        mode = ShareMode.READ,
                        receiver = ShareReceiver.Public,
                        notifyReceiver = false,
                        resourceType = SharedEntityTypes.TICKET,
                        resourceName = loaded.ticket.title,
                    )
                    _messages.emit(stringProvider.getString(R.string.public_pass_enabled))
                } else {
                    sharingRepository.revokeShare(webId, artifactUri, ShareReceiver.Public)
                    _messages.emit(stringProvider.getString(R.string.public_pass_disabled))
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                _messages.emit(errors.message(e, AppOperation.CREATE_SHARE))
            }
            load()
        }
    }

    fun deepLinkFor(resourceUri: String): String =
        sharingRepository.deepLinkFor(resourceUri, ownerWebId, SharedEntityTypes.TICKET)

    fun bareUrlFor(resourceUri: String): String =
        sharingRepository.bareUrlFor(resourceUri)
}
