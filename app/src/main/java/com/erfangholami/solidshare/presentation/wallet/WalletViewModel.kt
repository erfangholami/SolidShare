package com.erfangholami.solidshare.presentation.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.tickets.TicketsRepository
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketSummaryItem
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val ticketsRepository: TicketsRepository,
    private val importHolder: TicketImportHolder,
    private val stringProvider: StringProvider,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val upcoming: List<TicketSummaryItem> = emptyList(),
        val past: List<TicketSummaryItem> = emptyList(),
    ) {
        val isEmpty: Boolean get() = upcoming.isEmpty() && past.isEmpty()
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun importPass(bytes: ByteArray, fileName: String? = null, onDraft: (TicketDraft) -> Unit) {
        val parsed = ticketsRepository.parseTicketFile(bytes, fileName)
        if (parsed == null) {
            _message.value = stringProvider.getString(R.string.wallet_import_unreadable)
            return
        }
        val (draft, artifact) = parsed
        importHolder.stash(artifact)
        onDraft(draft)
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                ticketsRepository.getTickets(webId)
            }.onSuccess { tickets ->
                val now = Instant.now()
                val (upcoming, past) = tickets.partition { ticket ->
                    val reference = ticketInstantOrNull(ticket.eventStart)
                        ?: ticketInstantOrNull(ticket.validThrough)
                    reference == null || !reference.isBefore(now)
                }
                _state.update {
                    it.copy(
                        loading = false,
                        upcoming = upcoming.sortedBy {
                            t -> ticketInstantOrNull(t.eventStart) ?: Instant.MAX
                        },
                        past = past.sortedByDescending {
                            t -> ticketInstantOrNull(t.eventStart) ?: Instant.MIN
                        },
                    )
                }
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
}
