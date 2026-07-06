package com.erfangholami.solidshare.presentation.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ContactListEntry
import com.erfangholami.solidshare.presentation.components.EmptyState
import com.erfangholami.solidshare.presentation.components.EntityRow
import com.erfangholami.solidshare.presentation.components.ErrorState
import com.erfangholami.solidshare.presentation.components.LoadingState
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.components.ProfileAvatar
import com.erfangholami.solidshare.presentation.navigation.ContactDetailRoute
import com.erfangholami.solidshare.presentation.navigation.ContactsSettingsRoute
import com.erfangholami.solidshare.presentation.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsPage(
    navController: NavController,
    viewModel: ContactsViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                title = {
                    Text(
                        stringResource(R.string.contacts_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    IconButton(
                        onClick = { navController.navigate(ContactsSettingsRoute) },
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.contacts_settings_title),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> LoadingState(modifier = Modifier.align(Alignment.Center))

                state.error != null -> ErrorState(
                    message = state.error.orEmpty(),
                    modifier = Modifier.align(Alignment.Center),
                    retryLabel = stringResource(R.string.retry),
                    onRetry = { viewModel.load() },
                )

                else -> ContactsContent(
                    state = state,
                    onQueryChange = viewModel::onQueryChange,
                    onBookSelected = viewModel::onBookSelected,
                    onContactClick = { entry ->
                        navController.navigate(ContactDetailRoute(entry.bookUri, entry.uri))
                    },
                    onOpenSettings = { navController.navigate(ContactsSettingsRoute) },
                )
            }
        }
    }
}

@Composable
private fun ContactsContent(
    state: ContactsViewModel.UiState,
    onQueryChange: (String) -> Unit,
    onBookSelected: (String?) -> Unit,
    onContactClick: (ContactListEntry) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.contacts_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )
        if (state.books.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.selectedBookUri == null,
                    onClick = { onBookSelected(null) },
                    label = { Text(stringResource(R.string.contacts_all_books)) },
                )
                state.books.forEach { book ->
                    FilterChip(
                        selected = state.selectedBookUri == book.uri,
                        onClick = { onBookSelected(book.uri) },
                        label = { Text(book.title) },
                    )
                }
            }
        }
        val filtered = state.filteredEntries
        if (filtered.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.contacts_empty_title),
                subtitle = stringResource(R.string.contacts_empty_subtitle),
                icon = Icons.Filled.Contacts,
                actionLabel = stringResource(R.string.contacts_settings_title),
                onAction = onOpenSettings,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val grouped = filtered.groupBy { entry ->
                entry.name.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
            }.toSortedMap()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                grouped.forEach { (letter, entries) ->
                    item(key = "header-$letter") {
                        Text(
                            text = letter.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(entries, key = { it.uri }) { entry ->
                        EntityRow(
                            title = entry.name,
                            modifier = Modifier.clickable { onContactClick(entry) },
                            leading = {
                                ProfileAvatar(
                                    webId = entry.uri,
                                    displayName = entry.name,
                                )
                            },
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ContactsContentPreview() {
    AppTheme {
        ContactsContent(
            state = ContactsViewModel.UiState(
                loading = false,
                books = PreviewSamples.addressBooks(),
                entries = PreviewSamples.contactEntries(),
            ),
            onQueryChange = {},
            onBookSelected = {},
            onContactClick = {},
            onOpenSettings = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Contacts Dark")
@Composable
private fun ContactsContentDarkPreview() {
    AppTheme(isDarkTheme = true) {
        ContactsContent(
            state = ContactsViewModel.UiState(
                loading = false,
                books = PreviewSamples.addressBooks(),
                entries = PreviewSamples.contactEntries(),
            ),
            onQueryChange = {},
            onBookSelected = {},
            onContactClick = {},
            onOpenSettings = {},
        )
    }
}
