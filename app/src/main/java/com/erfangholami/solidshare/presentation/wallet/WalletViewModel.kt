package com.erfangholami.solidshare.presentation.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.tickets.TicketsRepository
import com.erfangholami.solidshare.domain.model.TicketSummaryItem
import com.erfangholami.solidshare.util.StringProvider
import com.erfangholami.solidshare.worker.PassRefreshWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
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
    private val workManager: WorkManager,
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

    private var observeJob: Job? = null
    private var observedWebId: String? = null

    fun prepareImport(bytes: ByteArray, fileName: String? = null) {
        importHolder.stashImport(bytes, fileName)
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun load() {
        viewModelScope.launch {
            runCatching { requireNotNull(authRepository.getActiveWebId()) }
                .onSuccess { webId ->
                    startObserving(webId)
                    PassRefreshWorker.enqueue(workManager)
                    refresh(webId)
                }
                .onFailure { error ->
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

    private fun startObserving(webId: String) {
        if (observedWebId == webId && observeJob?.isActive == true) return
        observeJob?.cancel()
        observedWebId = webId
        observeJob = viewModelScope.launch {
            ticketsRepository.observeTickets(webId).collect { tickets ->
                val now = Instant.now()
                val (upcoming, past) = tickets.partition { ticket ->
                    val reference = ticketInstantOrNull(ticket.eventStart)
                        ?: ticketInstantOrNull(ticket.validThrough)
                    reference == null || !reference.isBefore(now)
                }
                _state.update {
                    it.copy(
                        loading = it.loading && tickets.isEmpty(),
                        error = null,
                        upcoming = upcoming.sortedBy { t ->
                            ticketInstantOrNull(t.eventStart) ?: Instant.MAX
                        },
                        past = past.sortedByDescending { t ->
                            ticketInstantOrNull(t.eventStart) ?: Instant.MIN
                        },
                    )
                }
            }
        }
    }

    private suspend fun refresh(webId: String) {
        runCatching { ticketsRepository.refreshTickets(webId) }
            .onSuccess {
                _state.update { it.copy(loading = false, error = null) }
            }
            .onFailure { error ->
                val hadContent = !_state.value.isEmpty
                _state.update {
                    it.copy(
                        loading = false,
                        error = if (hadContent) {
                            null
                        } else {
                            error.message
                                ?: stringProvider.getString(R.string.error_something_went_wrong)
                        },
                    )
                }
                if (hadContent) {
                    _message.value = stringProvider.getString(R.string.wallet_refresh_failed)
                }
            }
    }
}
