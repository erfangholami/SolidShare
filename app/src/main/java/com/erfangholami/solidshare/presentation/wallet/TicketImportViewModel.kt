package com.erfangholami.solidshare.presentation.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.tickets.TicketsRepository
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketFile
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TicketImportViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val ticketsRepository: TicketsRepository,
    private val importHolder: TicketImportHolder,
    private val stringProvider: StringProvider,
) : ViewModel() {

    sealed interface ImportState {
        data object Loading : ImportState
        data class Found(val draft: TicketDraft) : ImportState
        data object NotFound : ImportState
    }

    private val _state = MutableStateFlow<ImportState>(ImportState.Loading)
    val state = _state.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private var artifact: TicketFile? = null
    private var started = false

    fun load() {
        if (started) return
        started = true
        val pending = importHolder.consumeImport()
        if (pending == null) {
            _state.value = ImportState.NotFound
            return
        }
        viewModelScope.launch {
            _state.value = ImportState.Loading
            val result = runCatching {
                ticketsRepository.parseTicketFile(pending.bytes, pending.fileName)
            }.getOrNull()
            if (result == null) {
                _state.value = ImportState.NotFound
            } else {
                artifact = result.second
                _state.value = ImportState.Found(result.first)
            }
        }
    }

    fun add(onAdded: () -> Unit) {
        val draft = withFallbackTitle((_state.value as? ImportState.Found)?.draft ?: return)
        viewModelScope.launch {
            _saving.value = true
            runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                ticketsRepository.createTicket(webId, draft, artifact)
            }.onSuccess {
                _saving.value = false
                onAdded()
            }.onFailure {
                _saving.value = false
                _message.value = stringProvider.getString(R.string.error_something_went_wrong)
            }
        }
    }

    fun prepareEdit(): TicketDraft? {
        val draft = (_state.value as? ImportState.Found)?.draft ?: return null
        importHolder.stash(artifact)
        return draft
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun withFallbackTitle(draft: TicketDraft): TicketDraft =
        if (draft.title.isNotBlank()) {
            draft
        } else {
            draft.copy(title = stringProvider.getString(R.string.ticket_untitled))
        }
}
