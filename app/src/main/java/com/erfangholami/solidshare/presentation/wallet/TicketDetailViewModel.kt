package com.erfangholami.solidshare.presentation.wallet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.passimport.PassImages
import com.erfangholami.solidshare.data.passimport.PkpassImages
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.tickets.TicketsRepository
import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketFile
import com.erfangholami.solidshare.domain.model.TicketSource
import com.erfangholami.solidshare.presentation.navigation.TicketDetailRoute
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class TicketDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val ticketsRepository: TicketsRepository,
    private val stringProvider: StringProvider,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val ticket: Ticket? = null,
        val busy: Boolean = false,
    )

    private val route = savedStateHandle.toRoute<TicketDetailRoute>()
    val ticketUri: String = route.ticketUri

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _visuals = MutableStateFlow<PassImages?>(null)
    val visuals = _visuals.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                ticketsRepository.getTicket(webId, ticketUri)
            }.onSuccess { ticket ->
                _state.update { it.copy(loading = false, ticket = ticket) }
                loadVisuals(ticket)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = error.message
                            ?: stringProvider.getString(R.string.error_something_went_wrong),
                    )
                }
            }
        }
    }

    private fun loadVisuals(ticket: Ticket) {
        if (ticket.source != TicketSource.PKPASS) return
        viewModelScope.launch {
            _visuals.value = runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                ticketsRepository.getTicketImages(webId, ticket.uri, ticket.images)
                    ?: ticket.artifactUri?.let { artifactUri ->
                        val file = ticketsRepository.getTicketArtifact(webId, ticket.uri, artifactUri)
                        withContext(Dispatchers.Default) { PkpassImages.forTicketFile(file.bytes) }
                    }
            }.getOrNull()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                ticketsRepository.queueDelete(webId, ticketUri)
            }.onSuccess {
                _state.update { it.copy(busy = false) }
                onDeleted()
            }.onFailure {
                _state.update { it.copy(busy = false) }
                _message.value = stringProvider.getString(R.string.error_something_went_wrong)
            }
        }
    }

    fun fetchArtifact(onReady: (TicketFile) -> Unit) {
        val artifactUri = _state.value.ticket?.artifactUri ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                ticketsRepository.getTicketArtifact(webId, ticketUri, artifactUri)
            }.onSuccess { file ->
                _state.update { it.copy(busy = false) }
                onReady(file)
            }.onFailure {
                _state.update { it.copy(busy = false) }
                _message.value = stringProvider.getString(R.string.error_something_went_wrong)
            }
        }
    }

    fun notifyNoViewer() {
        _message.value = stringProvider.getString(R.string.ticket_no_viewer)
    }

    fun consumeMessage() {
        _message.value = null
    }
}
