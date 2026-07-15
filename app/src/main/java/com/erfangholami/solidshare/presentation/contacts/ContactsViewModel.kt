package com.erfangholami.solidshare.presentation.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.contacts.ContactsRepository
import com.erfangholami.solidshare.domain.model.AddressBook
import com.erfangholami.solidshare.domain.model.ContactListEntry
import com.erfangholami.solidshare.sync.SolidAccountManager
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val contactsRepository: ContactsRepository,
    private val solidAccountManager: SolidAccountManager,
    private val stringProvider: StringProvider,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val books: List<AddressBook> = emptyList(),
        val entries: List<ContactListEntry> = emptyList(),
        val query: String = "",
        val selectedBookUri: String? = null,
        val mergeCount: Int = 0,
    ) {
        val filteredEntries: List<ContactListEntry>
            get() = entries
                .filter { selectedBookUri == null || it.bookUri == selectedBookUri }
                .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    }

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private var observeJob: Job? = null

    private var observedWebId: String? = null

    fun load() {
        viewModelScope.launch {
            val webId = runCatching { authRepository.getActiveWebId() }.getOrNull() ?: run {
                _state.update {
                    it.copy(
                        loading = false,
                        error = stringProvider.getString(R.string.error_no_active_user),
                    )
                }
                return@launch
            }
            if (observedWebId != webId) {
                observedWebId = webId
                observeJob?.cancel()
                _state.update {
                    it.copy(
                        loading = true,
                        error = null,
                        books = emptyList(),
                        entries = emptyList(),
                        selectedBookUri = null,
                    )
                }
                observeJob = viewModelScope.launch {
                    contactsRepository.observeContacts(webId).collect { entries ->
                        _state.update {
                            it.copy(
                                entries = entries,
                                loading = it.loading && entries.isEmpty(),
                                error = if (entries.isEmpty()) it.error else null,
                            )
                        }
                    }
                }
            }
            refresh(webId)
        }
    }

    private suspend fun refresh(webId: String) {
        solidAccountManager.requestSync(webId)
        try {
            val overview = contactsRepository.refreshContacts(webId)
            _state.update { it.copy(loading = false, error = null, books = overview.books) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val hasCachedEntries = _state.value.entries.isNotEmpty()
            _state.update { current ->
                if (hasCachedEntries) {
                    current.copy(loading = false)
                } else {
                    current.copy(
                        loading = false,
                        error = e.message ?: stringProvider.getString(R.string.error_unknown),
                    )
                }
            }
            if (hasCachedEntries) {
                _message.value = stringProvider.getString(R.string.contacts_refresh_failed)
            }
        }
        refreshMergeCount()
    }

    private fun refreshMergeCount() {
        viewModelScope.launch {
            val count = runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                contactsRepository.findMergeSuggestions(webId).size
            }.getOrDefault(0)
            _state.update { it.copy(mergeCount = count) }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun onBookSelected(bookUri: String?) {
        _state.update { it.copy(selectedBookUri = bookUri) }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
