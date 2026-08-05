package com.erfangholami.solidshare.presentation.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.contacts.ContactsRepository
import com.erfangholami.solidshare.domain.error.AppError
import com.erfangholami.solidshare.domain.error.AppOperation
import com.erfangholami.solidshare.domain.error.ErrorPresenter
import com.erfangholami.solidshare.domain.error.UiError
import com.erfangholami.solidshare.domain.error.asException
import com.erfangholami.solidshare.domain.error.rethrowIfCancellation
import com.erfangholami.solidshare.domain.model.AddressBook
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AddressBooksViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val contactsRepository: ContactsRepository,
    private val stringProvider: StringProvider,
    private val errors: ErrorPresenter,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: UiError? = null,
        val books: List<AddressBook> = emptyList(),
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                contactsRepository.getOverview(webId).books
            }.onSuccess { books ->
                _state.update { it.copy(loading = false, books = books) }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = errors.present(error, AppOperation.LOAD_ADDRESS_BOOKS),
                    )
                }
            }
        }
    }

    fun create(title: String, isPrivate: Boolean) {
        runBookOperation { webId ->
            contactsRepository.createAddressBook(webId, title.trim(), isPrivate)
        }
    }

    fun rename(book: AddressBook, newName: String) {
        runBookOperation { webId ->
            contactsRepository.renameAddressBook(webId, book.uri, newName.trim())
        }
    }

    fun delete(book: AddressBook) {
        runBookOperation { webId ->
            contactsRepository.deleteAddressBook(webId, book.uri)
        }
    }

    private fun runBookOperation(operation: suspend (String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                operation(webId)
            }.onSuccess {
                _state.update { it.copy(busy = false) }
                load()
            }.onFailure { error ->
                _state.update { it.copy(busy = false) }
                _message.value = errors.message(error, AppOperation.LOAD_ADDRESS_BOOKS)
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
