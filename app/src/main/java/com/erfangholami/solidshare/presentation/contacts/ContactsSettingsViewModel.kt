package com.erfangholami.solidshare.presentation.contacts

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.contacts.ContactsRepository
import com.erfangholami.solidshare.sync.ContactsAccountManager
import com.erfangholami.solidshare.domain.error.AppError
import com.erfangholami.solidshare.domain.error.AppOperation
import com.erfangholami.solidshare.domain.error.ErrorPresenter
import com.erfangholami.solidshare.domain.error.asException
import com.erfangholami.solidshare.domain.error.rethrowIfCancellation
import com.erfangholami.solidshare.util.StringProvider
import com.erfangholami.solidshare.worker.ContactsDeviceImportWorker
import com.erfangholami.solidshare.worker.ContactsExportWorker
import com.erfangholami.solidshare.worker.ContactsImportWorker
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
    private val contactsAccountManager: ContactsAccountManager,
    private val workManager: WorkManager,
    private val stringProvider: StringProvider,
    private val errors: ErrorPresenter,
) : ViewModel() {

    data class UiState(
        val mergeCount: Int = 0,
        val contactCount: Int = 0,
        val busy: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun deleteAllContacts() {
        viewModelScope.launch {
            val count = _state.value.contactCount
            runCatching {
                val webId = authRepository.getActiveWebId()
                    ?: throw AppError.NoActiveAccount.asException()
                contactsRepository.queueDeleteAll(webId)
                contactsAccountManager.requestSync(webId)
            }.onSuccess {
                _message.value = stringProvider.getString(R.string.contacts_delete_all_done, count)
            }.onFailure {
                it.rethrowIfCancellation()
                _message.value = errors.message(it, AppOperation.DELETE_CONTACT)
            }
            _state.update { it.copy(mergeCount = 0, contactCount = 0) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val webId = runCatching { authRepository.getActiveWebId() }.getOrNull() ?: return@launch
            val contactCount = runCatching {
                contactsRepository.getOverview(webId).entries.size
            }.getOrDefault(0)
            _state.update { it.copy(contactCount = contactCount) }
            val mergeCount = runCatching {
                contactsRepository.findMergeSuggestions(webId).size
            }.getOrDefault(0)
            _state.update { it.copy(mergeCount = mergeCount) }
        }
    }

    fun importDeviceContacts() {
        viewModelScope.launch {
            val webId = runCatching { authRepository.getActiveWebId() }.getOrNull() ?: return@launch
            val request = OneTimeWorkRequestBuilder<ContactsDeviceImportWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setInputData(workDataOf(ContactsDeviceImportWorker.KEY_WEB_ID to webId))
                .build()
            workManager.enqueue(request)
            _message.value = stringProvider.getString(R.string.contacts_device_import_started)
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            runCatching {
                val webId = authRepository.getActiveWebId()
                    ?: throw AppError.NoActiveAccount.asException()
                contactsAccountManager.requestSync(webId)
            }
            _message.value = stringProvider.getString(R.string.contacts_sync_requested)
        }
    }

    fun startImport(uri: Uri) {
        viewModelScope.launch {
            val webId = runCatching { authRepository.getActiveWebId() }.getOrNull() ?: return@launch
            val request = OneTimeWorkRequestBuilder<ContactsImportWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setInputData(
                    workDataOf(
                        ContactsImportWorker.KEY_WEB_ID to webId,
                        ContactsImportWorker.KEY_SOURCE_URI to uri.toString(),
                    ),
                )
                .build()
            workManager.enqueue(request)
            _message.value = stringProvider.getString(R.string.contacts_import_started)
        }
    }

    fun startExport(uri: Uri) {
        viewModelScope.launch {
            val webId = runCatching { authRepository.getActiveWebId() }.getOrNull() ?: return@launch
            val request = OneTimeWorkRequestBuilder<ContactsExportWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setInputData(
                    workDataOf(
                        ContactsExportWorker.KEY_WEB_ID to webId,
                        ContactsExportWorker.KEY_DEST_URI to uri.toString(),
                    ),
                )
                .build()
            workManager.enqueue(request)
            _message.value = stringProvider.getString(R.string.contacts_export_started)
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
