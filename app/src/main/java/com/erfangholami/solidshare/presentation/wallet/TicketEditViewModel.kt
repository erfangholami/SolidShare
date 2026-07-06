package com.erfangholami.solidshare.presentation.wallet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.tickets.TicketsRepository
import com.erfangholami.solidshare.data.repo.tickets.toDraft
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.presentation.navigation.TicketEditRoute
import com.erfangholami.solidshare.presentation.navigation.ticketEditTypeMap
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TicketEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val ticketsRepository: TicketsRepository,
    private val importHolder: TicketImportHolder,
    private val stringProvider: StringProvider,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val saving: Boolean = false,
        val isEditing: Boolean = false,
        val draft: TicketDraft = TicketDraft(),
    ) {
        val canSave: Boolean get() = draft.title.isNotBlank()
    }

    private val route = savedStateHandle.toRoute<TicketEditRoute>(ticketEditTypeMap)
    private val ticketUri: String? = route.ticketUri

    private val _state = MutableStateFlow(
        UiState(isEditing = ticketUri != null, draft = route.draft),
    )
    val state = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private var loaded = false

    fun load() {
        val uri = ticketUri ?: return
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                ticketsRepository.getTicket(webId, uri)
            }.onSuccess { ticket ->
                _state.update { it.copy(loading = false, draft = ticket.toDraft()) }
            }.onFailure {
                _state.update { state -> state.copy(loading = false) }
                _message.value = stringProvider.getString(R.string.error_something_went_wrong)
            }
        }
    }

    fun onDraftChange(draft: TicketDraft) {
        _state.update { it.copy(draft = draft) }
    }

    fun save(onSaved: () -> Unit) {
        val draft = _state.value.draft
        if (draft.title.isBlank()) {
            _message.value = stringProvider.getString(R.string.ticket_form_needs_title)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                if (ticketUri == null) {
                    ticketsRepository.createTicket(webId, draft, importHolder.consume())
                } else {
                    ticketsRepository.updateTicket(webId, ticketUri, draft)
                }
            }.onSuccess {
                _state.update { it.copy(saving = false) }
                onSaved()
            }.onFailure {
                _state.update { it.copy(saving = false) }
                _message.value = stringProvider.getString(R.string.error_something_went_wrong)
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
