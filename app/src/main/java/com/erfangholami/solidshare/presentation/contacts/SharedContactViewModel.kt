package com.erfangholami.solidshare.presentation.contacts

import android.graphics.BitmapFactory
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.contacts.ContactsRepository
import com.erfangholami.solidshare.domain.model.ContactDetail
import com.erfangholami.solidshare.presentation.navigation.SharedContactRoute
import com.erfangholami.solidshare.util.NetworkMonitor
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
import kotlinx.coroutines.launch

@HiltViewModel
class SharedContactViewModel @Inject constructor(
    private val stringProvider: StringProvider,
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val contactsRepository: ContactsRepository,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState
        data object Offline : UiState

        @Immutable
        data class Loaded(
            val contact: ContactDetail,
            val photo: ImageBitmap?,
            val added: Boolean,
            val adding: Boolean,
        ) : UiState

        data class Error(val message: String) : UiState
    }

    private val route = savedStateHandle.toRoute<SharedContactRoute>()
    val ownerWebId: String? = route.ownerWebId

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
                val webId = authRepository.getActiveWebId() ?: error("Not signed in")
                val contact = contactsRepository.getSharedContact(webId, route.resourceUri)
                val photo = contact.photoUri
                    ?.let { contactsRepository.getSharedContactPhoto(webId, it) }
                    ?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                _uiState.value = UiState.Loaded(
                    contact = contact,
                    photo = photo,
                    added = false,
                    adding = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    e.message ?: stringProvider.getString(R.string.entity_share_load_failed),
                )
            }
        }
    }

    fun addToContacts() {
        val loaded = _uiState.value as? UiState.Loaded ?: return
        if (loaded.added || loaded.adding) return
        viewModelScope.launch {
            _uiState.value = loaded.copy(adding = true)
            try {
                val webId = authRepository.getActiveWebId() ?: error("Not signed in")
                val sharedWebId = loaded.contact.webId
                if (sharedWebId != null && sharedWebId == webId) {
                    _uiState.value = loaded.copy(adding = false)
                    _messages.emit(stringProvider.getString(R.string.contact_add_own_profile))
                    return@launch
                }
                if (sharedWebId != null) {
                    val match = contactsRepository.findContactByWebId(webId, sharedWebId)
                    if (match.exists) {
                        _uiState.value = loaded.copy(added = true, adding = false)
                        _messages.emit(
                            match.contact?.fullName
                                ?.takeIf { it.isNotBlank() }
                                ?.let {
                                    stringProvider.getString(
                                        R.string.contact_add_already_exists_named,
                                        it,
                                    )
                                }
                                ?: stringProvider.getString(R.string.contact_add_already_exists),
                        )
                        return@launch
                    }
                }
                contactsRepository.addSharedContactToBook(webId, loaded.contact)
                _uiState.value = loaded.copy(added = true, adding = false)
                _messages.emit(stringProvider.getString(R.string.contact_add_added))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = loaded.copy(adding = false)
                _messages.emit(
                    e.message ?: stringProvider.getString(R.string.error_something_went_wrong),
                )
            }
        }
    }
}
