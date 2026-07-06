package com.erfangholami.solidshare.presentation.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.contacts.ContactsRepository
import com.erfangholami.solidshare.presentation.components.EmptyState
import com.erfangholami.solidshare.presentation.components.EntityRow
import com.erfangholami.solidshare.presentation.components.LoadingState
import com.erfangholami.solidshare.presentation.components.ProfileAvatar
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ContactPickerViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val contactsRepository: ContactsRepository,
) : ViewModel() {

    data class Entry(
        val name: String,
        val webId: String,
    )

    data class UiState(
        val loading: Boolean = true,
        val entries: List<Entry> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val entries = runCatching {
                val webId = requireNotNull(authRepository.getActiveWebId())
                val overview = contactsRepository.getOverview(webId)
                overview.books.flatMap { book ->
                    runCatching {
                        contactsRepository.getAllContacts(webId, book.uri)
                    }.getOrDefault(emptyList())
                }
                    .mapNotNull { contact ->
                        contact.webId?.let { Entry(contact.fullName, it) }
                    }
                    .distinctBy { it.webId }
                    .sortedBy { it.name.lowercase() }
            }.getOrDefault(emptyList())
            _state.update { it.copy(loading = false, entries = entries) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactReceiverPicker(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: ContactPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = stringResource(R.string.share_pick_from_contacts),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        when {
            state.loading -> LoadingState(
                modifier = Modifier.padding(vertical = 24.dp),
            )

            state.entries.isEmpty() -> EmptyState(
                title = stringResource(R.string.share_contacts_empty_title),
                subtitle = stringResource(R.string.share_contacts_empty_subtitle),
                icon = Icons.Filled.Contacts,
            )

            else -> LazyColumn {
                items(state.entries, key = { it.webId }) { entry ->
                    EntityRow(
                        title = entry.name,
                        subtitle = entry.webId,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(entry.webId) },
                        leading = {
                            ProfileAvatar(
                                webId = entry.webId,
                                displayName = entry.name,
                            )
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
