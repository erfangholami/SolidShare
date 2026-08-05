package com.erfangholami.solidshare.presentation.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.local.ContactsMergePrefs
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.contacts.ContactMergeEngine
import com.erfangholami.solidshare.data.repo.contacts.ContactsRepository
import com.erfangholami.solidshare.domain.model.ContactRef
import com.erfangholami.solidshare.domain.model.MergeSuggestion
import com.erfangholami.solidshare.sync.ContactsAccountManager
import com.erfangholami.solidshare.domain.error.AppError
import com.erfangholami.solidshare.domain.error.AppOperation
import com.erfangholami.solidshare.domain.error.ErrorPresenter
import com.erfangholami.solidshare.domain.error.asException
import com.erfangholami.solidshare.domain.error.rethrowIfCancellation
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ContactsMergeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val contactsRepository: ContactsRepository,
    private val mergeEngine: ContactMergeEngine,
    private val mergePrefs: ContactsMergePrefs,
    private val contactsAccountManager: ContactsAccountManager,
    private val stringProvider: StringProvider,
    private val errors: ErrorPresenter,
) : ViewModel() {

    data class MemberUi(
        val contactUri: String,
        val name: String,
        val subtitle: String,
        val isSurvivor: Boolean,
    )

    data class SuggestionUi(
        val signature: String,
        val title: String,
        val members: List<MemberUi>,
    )

    data class UiState(
        val loading: Boolean = true,
        val busy: Boolean = false,
        val suggestions: List<SuggestionUi> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private var raw: List<MergeSuggestion> = emptyList()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val suggestions = runCatching {
                val webId = authRepository.getActiveWebId()
                    ?: throw AppError.NoActiveAccount.asException()
                contactsRepository.findMergeSuggestions(webId)
            }.getOrDefault(emptyList())
            raw = suggestions
            _state.update {
                it.copy(loading = false, suggestions = suggestions.map { s -> s.toUi() })
            }
        }
    }

    fun merge(signature: String) {
        val suggestion = raw.firstOrNull { it.signature == signature } ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching {
                val webId = authRepository.getActiveWebId()
                    ?: throw AppError.NoActiveAccount.asException()
                val survivor = mergeEngine.chooseSurvivor(suggestion.members.map { it.contact })
                val survivorMember = suggestion.members.first { it.contact.uri == survivor.uri }
                val losers = suggestion.members
                    .filter { it.contact.uri != survivor.uri }
                    .map { ContactRef(it.bookUri, it.contact.uri) }
                contactsRepository.queueMerge(
                    webId = webId,
                    survivor = ContactRef(survivorMember.bookUri, survivor.uri),
                    losers = losers,
                )
                contactsAccountManager.requestSync(webId)
            }.onSuccess {
                _message.value = stringProvider.getString(R.string.contacts_merge_done)
            }.onFailure {
                it.rethrowIfCancellation()
                _message.value = errors.message(it, AppOperation.MERGE_CONTACTS)
            }
            _state.update { it.copy(busy = false) }
            load()
        }
    }

    fun dismiss(signature: String) {
        viewModelScope.launch {
            runCatching {
                val webId = authRepository.getActiveWebId()
                    ?: throw AppError.NoActiveAccount.asException()
                mergePrefs.dismiss(webId, signature)
            }
            raw = raw.filterNot { it.signature == signature }
            _state.update { state ->
                state.copy(suggestions = state.suggestions.filterNot { it.signature == signature })
            }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun MergeSuggestion.toUi(): SuggestionUi {
        val survivor = mergeEngine.chooseSurvivor(members.map { it.contact })
        return SuggestionUi(
            signature = signature,
            title = survivor.fullName.ifBlank {
                stringProvider.getString(R.string.contacts_merge_untitled)
            },
            members = members
                .sortedByDescending { it.contact.uri == survivor.uri }
                .map { member ->
                    MemberUi(
                        contactUri = member.contact.uri,
                        name = member.contact.fullName.ifBlank {
                            stringProvider.getString(R.string.contacts_merge_untitled)
                        },
                        subtitle = subtitleFor(member.contact),
                        isSurvivor = member.contact.uri == survivor.uri,
                    )
                },
        )
    }

    private fun subtitleFor(contact: com.erfangholami.solidshare.domain.model.ContactDetail): String =
        contact.webId
            ?: contact.emails.firstOrNull()?.address
            ?: contact.phones.firstOrNull()?.number
            ?: ""
}
