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

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                solidAccountManager.requestSync(webId)
                contactsRepository.getOverview(webId)
            }.onSuccess { overview ->
                _state.update {
                    it.copy(
                        loading = false,
                        books = overview.books,
                        entries = overview.entries,
                        selectedBookUri = it.selectedBookUri
                            ?.takeIf { selected -> overview.books.any { b -> b.uri == selected } },
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
