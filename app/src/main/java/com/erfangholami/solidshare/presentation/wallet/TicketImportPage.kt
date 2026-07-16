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
                TicketImportViewModel.ImportState.NotFound ->
                    NotFoundContent(onClose = { navController.popBackStack() })

                is TicketImportViewModel.ImportState.Found -> FoundContent(
                    draft = current.draft,
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
private fun NotFoundContent(onClose: () -> Unit) {
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
            stringResource(R.string.ticket_import_none),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.ticket_import_none_subtitle),
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
        TicketWalletCard(draft)
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

@Composable
fun TicketWalletCard(draft: TicketDraft) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TicketCategoryIcon(draft.category, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        labelFor(draft.category).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    draft.issuer?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                draft.title.ifBlank { stringResource(R.string.ticket_untitled) },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            draft.journey?.let {
                Spacer(Modifier.height(16.dp))
                JourneyBody(it)
            } ?: run {
                EventBody(draft)
            }

            SeatChip(draft)

            if (!draft.token.isNullOrBlank()) {
                Spacer(Modifier.height(20.dp))
                TicketBarcode(token = draft.token!!, format = draft.barcodeFormat)
            }
        }
    }
}

@Composable
internal fun JourneyBody(journey: TicketJourney) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StopColumn(journey.from, alignEnd = false, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 12.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        StopColumn(journey.to, alignEnd = true, modifier = Modifier.weight(1f))
    }
    val line = listOfNotNull(
        journey.carrier?.takeIf { it.isNotBlank() },
        journey.serviceNumber?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    if (line.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val details = listOfNotNull(
        journey.from?.gate?.let { stringResource(R.string.ticket_field_gate) + " " + it },
        journey.from?.terminal?.let { stringResource(R.string.ticket_field_terminal) + " " + it },
        journey.from?.platform?.let { stringResource(R.string.ticket_field_platform) + " " + it },
    ).joinToString(" · ")
    if (details.isNotBlank()) {
        Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StopColumn(stop: TicketStop?, alignEnd: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(
            stop?.code ?: stop?.name ?: "—",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        stop?.cityName?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        shortTime(stop?.time)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EventBody(draft: TicketDraft) {
    val lines = listOfNotNull(
        shortDateTime(draft.event?.start),
        draft.event?.venue?.name?.takeIf { it.isNotBlank() },
    )
    if (lines.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        lines.forEach {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SeatChip(draft: TicketDraft) {
    val seat = draft.seat ?: return
    val parts = listOfNotNull(
        seat.section?.let { stringResource(R.string.ticket_field_seat_section) + " " + it },
        seat.row?.let { stringResource(R.string.ticket_field_seat_row) + " " + it },
        seat.number?.let { stringResource(R.string.ticket_field_seat_number) + " " + it },
    ).joinToString(" · ")
    if (parts.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(parts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM · HH:mm")

private fun shortTime(iso: String?): String? {
    iso ?: return null
    runCatching { OffsetDateTime.parse(iso).format(timeFormatter) }.getOrNull()?.let { return it }
    return runCatching { LocalDate.parse(iso).format(dateFormatter) }.getOrNull()
}

private fun shortDateTime(iso: String?): String? {
    iso ?: return null
    runCatching { OffsetDateTime.parse(iso).format(dateTimeFormatter) }.getOrNull()?.let { return it }
    return runCatching { LocalDate.parse(iso).format(dateFormatter) }.getOrNull()
}
