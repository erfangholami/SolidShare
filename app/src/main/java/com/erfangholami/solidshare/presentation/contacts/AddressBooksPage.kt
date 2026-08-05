package com.erfangholami.solidshare.presentation.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.AddressBook
import com.erfangholami.solidshare.presentation.components.BlockingProgressOverlay
import com.erfangholami.solidshare.presentation.components.EmptyState
import com.erfangholami.solidshare.presentation.components.EntityRow
import com.erfangholami.solidshare.presentation.components.ErrorState
import com.erfangholami.solidshare.presentation.components.UiErrorState
import com.erfangholami.solidshare.presentation.components.LoadingState
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.components.RequiresConnectionHint
import com.erfangholami.solidshare.presentation.components.RowDivider
import com.erfangholami.solidshare.presentation.rememberIsOnline
import com.erfangholami.solidshare.presentation.theme.AppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBooksPage(
    navController: NavController,
    viewModel: AddressBooksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val isOnline by rememberIsOnline()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val offlineMessage = stringResource(R.string.books_requires_connection)

    var createOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<AddressBook?>(null) }
    var deleteTarget by remember { mutableStateOf<AddressBook?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    fun requireOnline(action: () -> Unit) {
        if (isOnline) action() else scope.launch { snackbarHostState.showSnackbar(offlineMessage) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.books_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { requireOnline { createOpen = true } }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.book_new))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AddressBooksContent(
                state = state,
                isOnline = isOnline,
                onRetry = { viewModel.load() },
                onRename = { requireOnline { renameTarget = it } },
                onDelete = { requireOnline { deleteTarget = it } },
            )
            if (state.busy) {
                BlockingProgressOverlay(label = stringResource(R.string.books_title))
            }
        }
    }

    if (createOpen) {
        BookNameDialog(
            title = stringResource(R.string.book_new),
            confirmLabel = stringResource(R.string.create),
            initialName = "",
            showPrivacy = true,
            onConfirm = { name, isPrivate ->
                createOpen = false
                viewModel.create(name, isPrivate)
            },
            onDismiss = { createOpen = false },
        )
    }
    renameTarget?.let { book ->
        BookNameDialog(
            title = stringResource(R.string.book_rename),
            confirmLabel = stringResource(R.string.save),
            initialName = book.title,
            showPrivacy = false,
            onConfirm = { name, _ ->
                renameTarget = null
                viewModel.rename(book, name)
            },
            onDismiss = { renameTarget = null },
        )
    }
    deleteTarget?.let { book ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.book_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.book_delete_warning,
                        book.title,
                        book.contactCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        viewModel.delete(book)
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
internal fun AddressBooksContent(
    state: AddressBooksViewModel.UiState,
    isOnline: Boolean,
    onRetry: () -> Unit,
    onRename: (AddressBook) -> Unit,
    onDelete: (AddressBook) -> Unit,
) {
    when {
        state.loading -> LoadingState()
        state.error != null -> UiErrorState(error = state.error, onRetry = onRetry)
        state.books.isEmpty() -> EmptyState(
            title = stringResource(R.string.books_empty_title),
            subtitle = stringResource(R.string.books_empty_description),
        )
        else -> Column(modifier = Modifier.fillMaxSize()) {
            RequiresConnectionHint(
                visible = !isOnline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.books, key = { it.uri }) { book ->
                    BookRow(book = book, onRename = onRename, onDelete = onDelete)
                    RowDivider()
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}

@Composable
private fun BookRow(
    book: AddressBook,
    onRename: (AddressBook) -> Unit,
    onDelete: (AddressBook) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    EntityRow(
        title = book.title,
        subtitle = stringResource(
            R.string.book_subtitle,
            book.contactCount,
            stringResource(
                if (book.isPrivate) R.string.book_private_badge else R.string.book_public_badge,
            ),
        ),
        leading = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(22.dp),
                )
            }
        },
        trailing = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.book_actions),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.book_rename)) },
                        onClick = {
                            menuOpen = false
                            onRename(book)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.book_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete(book)
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun BookNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    showPrivacy: Boolean,
    onConfirm: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var isPrivate by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.book_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showPrivacy) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPrivate = !isPrivate },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.book_private_label),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                stringResource(R.string.book_private_support),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name, isPrivate) },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun AddressBooksContentPreview() {
    AppTheme {
        AddressBooksContent(
            state = AddressBooksViewModel.UiState(
                loading = false,
                books = PreviewSamples.addressBooks(),
            ),
            isOnline = true,
            onRetry = {},
            onRename = {},
            onDelete = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun AddressBooksEmptyPreview() {
    AppTheme {
        AddressBooksContent(
            state = AddressBooksViewModel.UiState(loading = false),
            isOnline = false,
            onRetry = {},
            onRename = {},
            onDelete = {},
        )
    }
}
