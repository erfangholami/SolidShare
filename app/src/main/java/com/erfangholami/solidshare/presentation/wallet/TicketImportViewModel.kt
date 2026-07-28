package com.erfangholami.solidshare.presentation.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.passimport.PassImages
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.tickets.ParsedTicketFile
import com.erfangholami.solidshare.data.repo.tickets.TicketsRepository
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TicketImportViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val ticketsRepository: TicketsRepository,
    private val importHolder: TicketImportHolder,
    private val stringProvider: StringProvider,
) : ViewModel() {

    data class FoundPass(
        val draft: TicketDraft,
        val visuals: PassImages?,
    )

    sealed interface ImportState {
        data object Loading : ImportState
        data class Found(val items: List<FoundPass>) : ImportState
        data object NotFound : ImportState
    }

    private val _state = MutableStateFlow<ImportState>(ImportState.Loading)
    val state = _state.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    private val _added = MutableStateFlow<Set<Int>>(emptySet())
    val added = _added.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private var parsed: List<ParsedTicketFile> = emptyList()
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
            parsed = runCatching {
                ticketsRepository.parseTicketFile(pending.bytes, pending.fileName)
            }.getOrDefault(emptyList())
            _state.value = if (parsed.isEmpty()) {
                ImportState.NotFound
            } else {
                ImportState.Found(parsed.map { FoundPass(it.draft, it.visuals) })
            }
        }
    }

    fun add(index: Int, onAllAdded: () -> Unit) {
        addAll(listOf(index), onAllAdded)
    }

    fun addAll(onAllAdded: () -> Unit) {
        addAll(parsed.indices.filter { it !in _added.value }, onAllAdded)
    }

    private fun addAll(indices: List<Int>, onAllAdded: () -> Unit) {
        if (indices.isEmpty() || _saving.value) return
        viewModelScope.launch {
            _saving.value = true
            runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                indices.forEach { index ->
                    val item = parsed[index]
                    ticketsRepository.queueCreate(webId, withFallbackTitle(item.draft), item.artifact)
                    _added.update { it + index }
                }
            }.onFailure {
                _message.value = stringProvider.getString(R.string.error_something_went_wrong)
            }
            _saving.value = false
            if (_added.value.size == parsed.size && parsed.isNotEmpty()) {
                onAllAdded()
            }
        }
    }

    fun prepareEdit(index: Int): TicketDraft? {
        val item = parsed.getOrNull(index) ?: return null
        importHolder.stash(item.artifact)
        return item.draft
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
