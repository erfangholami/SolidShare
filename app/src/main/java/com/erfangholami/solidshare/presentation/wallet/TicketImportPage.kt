package com.erfangholami.solidshare.presentation.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.passimport.PassImages
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketJourney
import com.erfangholami.solidshare.domain.model.TicketStop
import com.erfangholami.solidshare.presentation.navigation.TicketEditRoute
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketImportPage(
    navController: NavController,
    viewModel: TicketImportViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val visuals by viewModel.visuals.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.load() }
    androidx.compose.runtime.LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ticket_import_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val current = state) {
                TicketImportViewModel.ImportState.Loading -> LoadingContent()
                is TicketImportViewModel.ImportState.NotFound ->
                    NotFoundContent(
                        linkFailed = current.linkFailed,
                        onClose = { navController.popBackStack() },
                    )

                is TicketImportViewModel.ImportState.Found -> FoundContent(
                    draft = current.draft,
                    visuals = visuals,
                    saving = saving,
                    onAdd = { viewModel.add { navController.popBackStack() } },
                    onEdit = {
                        val draft = viewModel.prepareEdit() ?: return@FoundContent
                        navController.navigate(TicketEditRoute(draft = draft)) {
                            popUpTo(navController.currentBackStackEntry?.destination?.route ?: return@navigate) {
                                inclusive = true
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.ticket_import_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NotFoundContent(onClose: () -> Unit, linkFailed: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(
                if (linkFailed) R.string.ticket_import_link_failed
                else R.string.ticket_import_none,
            ),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                if (linkFailed) R.string.ticket_import_link_failed_subtitle
                else R.string.ticket_import_none_subtitle,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
    }
}

@Composable
private fun FoundContent(
    draft: TicketDraft,
    visuals: PassImages?,
    saving: Boolean,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        PassCard(draft.toPassCardData(visuals))
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onEdit,
                enabled = !saving,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.action_edit)) }
            Button(
                onClick = onAdd,
                enabled = !saving,
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.ticket_import_add)) }
        }
    }
}


