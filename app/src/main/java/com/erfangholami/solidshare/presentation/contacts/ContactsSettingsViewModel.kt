package com.erfangholami.solidshare.presentation.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.device.DeviceContactsSource
import com.erfangholami.solidshare.data.local.ContactsSyncPrefs
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.contacts.ContactsRepository
import com.erfangholami.solidshare.domain.model.ContactDetail
import com.erfangholami.solidshare.sync.SolidAccountManager
import com.erfangholami.solidshare.sync.SolidAccounts
import com.erfangholami.solidshare.util.StringProvider
import com.erfangholami.solidshare.util.VCardReader
import com.erfangholami.solidshare.util.VCardWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ContactsSettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val contactsRepository: ContactsRepository,
    private val deviceContactsSource: DeviceContactsSource,
    private val contactsSyncPrefs: ContactsSyncPrefs,
    private val solidAccountManager: SolidAccountManager,
    private val stringProvider: StringProvider,
) : ViewModel() {

    data class GoogleAccountRow(
        val accountName: String,
        val contactCount: Int,
        val enabled: Boolean,
    )

    data class UiState(
        val loadingAccounts: Boolean = true,
        val googleAccounts: List<GoogleAccountRow> = emptyList(),
        val busy: Boolean = false,
        val progress: Pair<Int, Int>? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun loadAccounts() {
        viewModelScope.launch {
            _state.update { it.copy(loadingAccounts = true) }
            runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                val enabled = contactsSyncPrefs.enabledGoogleAccounts(webId)
                deviceContactsSource.listAccounts(SolidAccounts.ACCOUNT_TYPE)
                    .filter { it.accountType == GOOGLE_ACCOUNT_TYPE }
                    .mapNotNull { account ->
                        account.accountName?.let { name ->
                            GoogleAccountRow(
                                accountName = name,
                                contactCount = account.contacts.size,
                                enabled = name in enabled,
                            )
                        }
                    }
            }.onSuccess { rows ->
                _state.update { it.copy(loadingAccounts = false, googleAccounts = rows) }
            }.onFailure {
                _state.update { state -> state.copy(loadingAccounts = false) }
            }
        }
    }

    fun onGoogleAccountToggled(accountName: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                contactsSyncPrefs.setGoogleAccountEnabled(webId, accountName, enabled)
                if (enabled) {
                    solidAccountManager.requestSync(webId)
                    _message.value = stringProvider.getString(R.string.contacts_sync_requested)
                }
            }
            _state.update { state ->
                state.copy(
                    googleAccounts = state.googleAccounts.map { row ->
                        if (row.accountName == accountName) row.copy(enabled = enabled) else row
                    },
                )
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                solidAccountManager.requestSync(webId)
            }
            _message.value = stringProvider.getString(R.string.contacts_sync_requested)
        }
    }

    fun importVcf(text: String) {
        viewModelScope.launch {
            val cards = runCatching { VCardReader.parse(text) }.getOrDefault(emptyList())
            if (cards.isEmpty()) {
                _message.value = stringProvider.getString(R.string.contacts_vcf_invalid)
                return@launch
            }
            _state.update { it.copy(busy = true, progress = 0 to cards.size) }
            runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                val bookUri = contactsRepository.ensureDefaultAddressBook(webId)
                val existingKeys = runCatching {
                    contactsRepository.getAllContacts(webId, bookUri)
                }.getOrDefault(emptyList()).map { it.dedupeKey() }.toMutableSet()
                var imported = 0
                var skipped = 0
                cards.forEachIndexed { index, card ->
                    val key = dedupeKey(
                        card.draft.fullName ?: listOfNotNull(
                            card.draft.givenName,
                            card.draft.familyName,
                        ).joinToString(" "),
                        card.draft.phones.firstOrNull()?.number,
                        card.draft.emails.firstOrNull()?.address,
                    )
                    if (key in existingKeys) {
                        skipped++
                    } else {
                        val created = contactsRepository.createContact(webId, bookUri, card.draft)
                        if (card.photo != null) {
                            runCatching {
                                contactsRepository.setContactPhoto(
                                    webId,
                                    created.uri,
                                    card.photo,
                                    card.photoMime ?: "image/jpeg",
                                )
                            }
                        }
                        existingKeys.add(key)
                        imported++
                    }
                    _state.update { it.copy(progress = (index + 1) to cards.size) }
                }
                solidAccountManager.requestSync(webId)
                imported to skipped
            }.onSuccess { (imported, skipped) ->
                _state.update { it.copy(busy = false, progress = null) }
                _message.value = stringProvider.getString(
                    R.string.contacts_import_done,
                    imported,
                    skipped,
                )
            }.onFailure {
                _state.update { it.copy(busy = false, progress = null) }
                _message.value = stringProvider.getString(R.string.error_something_went_wrong)
            }
        }
    }

    fun buildExport(onReady: (String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                val overview = contactsRepository.getOverview(webId)
                val contacts = overview.books.flatMap { book ->
                    runCatching {
                        contactsRepository.getAllContacts(webId, book.uri)
                    }.getOrDefault(emptyList())
                }
                val withPhotos = contacts.map { contact ->
                    val photo = contact.photoUri?.let { uri ->
                        runCatching { contactsRepository.getContactPhoto(webId, uri) }.getOrNull()
                    }
                    contact to photo
                }
                VCardWriter.write(withPhotos)
            }.onSuccess { text ->
                _state.update { it.copy(busy = false) }
                onReady(text)
            }.onFailure {
                _state.update { it.copy(busy = false) }
                _message.value = stringProvider.getString(R.string.contacts_export_failed)
            }
        }
    }

    fun notifyExportSaved() {
        _message.value = stringProvider.getString(R.string.contacts_export_saved)
    }

    fun notifyExportFailed() {
        _message.value = stringProvider.getString(R.string.contacts_export_failed)
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun ContactDetail.dedupeKey(): String =
        dedupeKey(fullName, phones.firstOrNull()?.number, emails.firstOrNull()?.address)

    private fun dedupeKey(name: String?, phone: String?, email: String?): String {
        val normalizedPhone = phone?.filter { it.isDigit() }.orEmpty()
        return listOf(
            name.orEmpty().trim().lowercase(),
            normalizedPhone,
            email.orEmpty().trim().lowercase(),
        ).joinToString("|")
    }

    companion object {
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
