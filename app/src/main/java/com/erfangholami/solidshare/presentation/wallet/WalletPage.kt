package com.erfangholami.solidshare.presentation.wallet

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketSummaryItem
import com.erfangholami.solidshare.presentation.components.EmptyState
import com.erfangholami.solidshare.presentation.components.ErrorState
import com.erfangholami.solidshare.presentation.components.LoadingState
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.components.SheetActionRow
import com.erfangholami.solidshare.presentation.navigation.TicketDetailRoute
import com.erfangholami.solidshare.presentation.navigation.TicketEditRoute
import com.erfangholami.solidshare.presentation.navigation.TicketImportRoute
import com.erfangholami.solidshare.presentation.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletPage(
    navController: NavController,
    viewModel: WalletViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAddSheet by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                if (bytes != null) {
                    viewModel.prepareImport(bytes, ticketFileName(context, uri))
                    navController.navigate(TicketImportRoute)
                }
            }
        }
    }

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
                        stringResource(R.string.wallet_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSheet = true }) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.wallet_add_ticket),
                )
            }
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

                state.isEmpty -> EmptyState(
                    title = stringResource(R.string.wallet_empty_title),
                    subtitle = stringResource(R.string.wallet_empty_subtitle),
                    icon = Icons.Filled.AccountBalanceWallet,
                    actionLabel = stringResource(R.string.wallet_add_ticket),
                    onAction = { showAddSheet = true },
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> TicketList(
                    upcoming = state.upcoming,
                    past = state.past,
                    onTicketClick = { ticket ->
                        navController.navigate(TicketDetailRoute(ticket.uri))
                    },
                )
            }
        }
    }

    if (showAddSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = sheetState,
        ) {
            Text(
                text = stringResource(R.string.wallet_add_ticket),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            SheetActionRow(
                icon = Icons.Filled.UploadFile,
                label = stringResource(R.string.wallet_add_import),
                onClick = {
                    showAddSheet = false
                    importLauncher.launch(
                        arrayOf(
                            "application/vnd.apple.pkpass",
                            "application/vnd.apple.pkpasses",
                            "application/zip",
                            "application/octet-stream",
                        ),
                    )
                },
            )
            SheetActionRow(
                icon = Icons.Filled.Edit,
                label = stringResource(R.string.wallet_add_manual),
                onClick = {
                    showAddSheet = false
                    navController.navigate(TicketEditRoute())
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TicketList(
    upcoming: List<TicketSummaryItem>,
    past: List<TicketSummaryItem>,
    onTicketClick: (TicketSummaryItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (upcoming.isNotEmpty()) {
            item(key = "header-upcoming") {
                SectionHeader(stringResource(R.string.wallet_upcoming))
            }
            items(upcoming, key = { it.uri }) { ticket ->
                PassCard(
                    data = ticket.toPassCardData(),
                    showBarcode = false,
                    onClick = { onTicketClick(ticket) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        if (past.isNotEmpty()) {
            item(key = "header-past") {
                SectionHeader(stringResource(R.string.wallet_past))
            }
            items(past, key = { it.uri }) { ticket ->
                PassCard(
                    data = ticket.toPassCardData(),
                    showBarcode = false,
                    expired = true,
                    onClick = { onTicketClick(ticket) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp),
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun TicketListPreview() {
    AppTheme {
        TicketList(
            upcoming = listOf(PreviewSamples.ticketSummary()),
            past = listOf(
                PreviewSamples.ticketSummary(
                    uri = "https://alice.solidcommunity.net/tickets/t2.ttl#this",
                    title = "Dune III",
                    category = TicketCategory.CINEMA,
                    eventStart = "2025-11-02T20:00:00Z",
                    issuer = "Pathé",
                ),
            ),
            onTicketClick = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Wallet Dark")
@Composable
private fun TicketListDarkPreview() {
    AppTheme(isDarkTheme = true) {
        TicketList(
            upcoming = listOf(PreviewSamples.ticketSummary()),
            past = emptyList(),
            onTicketClick = {},
        )
    }
}

private fun ticketFileName(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && index >= 0) return cursor.getString(index)
                }
        }
    }
    return uri.lastPathSegment
}
