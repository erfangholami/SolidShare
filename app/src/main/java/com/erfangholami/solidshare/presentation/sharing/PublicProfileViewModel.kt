package com.erfangholami.solidshare.presentation.sharing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.contacts.ContactsRepository
import com.erfangholami.solidshare.data.repo.profile.PublicProfileRepository
import com.erfangholami.solidshare.domain.model.ContactDraft
import com.erfangholami.solidshare.domain.model.ContactEmail
import com.erfangholami.solidshare.domain.model.ContactLinkType
import com.erfangholami.solidshare.domain.model.ContactPhone
import com.erfangholami.solidshare.domain.model.ContactWebLink
import com.erfangholami.solidshare.domain.model.PublicProfile
import com.erfangholami.solidshare.presentation.navigation.PublicProfileRoute
import com.erfangholami.solidshare.sync.SolidAccountManager
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PublicProfileViewModel @Inject constructor(
    private val stringProvider: StringProvider,
    savedStateHandle: SavedStateHandle,
    private val publicProfileRepository: PublicProfileRepository,
    private val authRepository: AuthRepository,
    private val contactsRepository: ContactsRepository,
    private val solidAccountManager: SolidAccountManager,
) : ViewModel() {

    sealed class UiState {
        data object Loading : UiState()
        data class Success(val profile: PublicProfile) : UiState()
        data class Error(val message: String) : UiState()
    }

    val webId: String = savedStateHandle.toRoute<PublicProfileRoute>().webId

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _addingToContacts = MutableStateFlow(false)
    val addingToContacts: StateFlow<Boolean> = _addingToContacts.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            val result = publicProfileRepository.fetchByWebId(webId)
            _state.value = result.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: stringProvider.getString(R.string.error_unknown)) },
            )
        }
    }

    fun addToContacts() {
        val profile = (_state.value as? UiState.Success)?.profile ?: return
        if (_addingToContacts.value) return
        viewModelScope.launch {
            _addingToContacts.value = true
            runCatching {
                val ownerWebId = requireNotNull(authRepository.getActiveWebId())
                if (webId == ownerWebId) {
                    stringProvider.getString(R.string.contact_add_own_profile)
                } else {
                    val match = contactsRepository.findContactByWebId(ownerWebId, webId)
                    if (match.exists) {
                        val name = match.contact?.fullName?.takeIf { it.isNotBlank() }
                        if (name != null) {
                            stringProvider.getString(
                                R.string.contact_add_already_exists_named,
                                name,
                            )
                        } else {
                            stringProvider.getString(R.string.contact_add_already_exists)
                        }
                    } else {
                        val bookUri = contactsRepository.ensureDefaultAddressBook(ownerWebId)
                        contactsRepository.createContact(ownerWebId, bookUri, profile.toDraft())
                        solidAccountManager.requestSync(ownerWebId)
                        stringProvider.getString(R.string.contact_add_added)
                    }
                }
            }.onSuccess { result ->
                _message.value = result
            }.onFailure {
                _message.value = stringProvider.getString(R.string.error_something_went_wrong)
            }
            _addingToContacts.value = false
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun PublicProfile.toDraft(): ContactDraft = ContactDraft(
        fullName = name?.takeIf { it.isNotBlank() }
            ?: webId.substringAfter("//").substringBefore("/"),
        givenName = givenName,
        familyName = familyName,
        phones = phones.map { ContactPhone(it) },
        emails = emails.map { ContactEmail(it) },
        organization = organization,
        role = role,
        links = listOf(ContactWebLink(ContactLinkType.WEB_ID, webId)),
    )
}
