package com.erfangholami.solidshare.presentation.contacts

import android.graphics.BitmapFactory
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
import com.erfangholami.solidshare.domain.model.ContactGroup
import com.erfangholami.solidshare.domain.model.ContactRef
import com.erfangholami.solidshare.presentation.navigation.ContactDetailRoute
import com.erfangholami.solidshare.sync.SolidAccountManager
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val contactsRepository: ContactsRepository,
    private val solidAccountManager: SolidAccountManager,
    private val stringProvider: StringProvider,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val contact: ContactDetail? = null,
        val memberGroups: List<ContactGroup> = emptyList(),
        val photo: ImageBitmap? = null,
        val busy: Boolean = false,
    )

    private data class Loaded(
        val contact: ContactDetail,
        val groups: List<ContactGroup>,
        val photo: ImageBitmap?,
    )

    private val route = savedStateHandle.toRoute<ContactDetailRoute>()
    val bookUri: String = route.bookUri
    val contactUri: String = route.contactUri

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                val contact = contactsRepository.getContact(webId, contactUri)
                val groups = runCatching {
                    contactsRepository.getGroups(webId, bookUri)
                        .filter { contact.uri in it.memberUris }
                }.getOrDefault(emptyList())
                val photo = contact.photoUri?.let { photoUri ->
                    runCatching {
                        val bytes = contactsRepository.getContactPhoto(webId, photoUri)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }.getOrNull()
                }
                Loaded(contact, groups, photo)
            }.onSuccess { loaded ->
                _state.update {
                    it.copy(
                        loading = false,
                        contact = loaded.contact,
                        memberGroups = loaded.groups,
                        photo = loaded.photo,
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

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                contactsRepository.queueDelete(webId, ContactRef(bookUri, contactUri))
                solidAccountManager.requestSync(webId)
            }.onSuccess {
                onDeleted()
            }.onFailure {
                _message.value = stringProvider.getString(R.string.error_something_went_wrong)
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
