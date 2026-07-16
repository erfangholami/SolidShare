package com.erfangholami.solidshare.presentation.wallet

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.erfangholami.solidshare.presentation.components.BlockingProgressOverlay
import com.erfangholami.solidshare.presentation.components.ErrorState
import com.erfangholami.solidshare.presentation.components.LoadingState
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.navigation.ManageSharingRoute
import com.erfangholami.solidshare.presentation.navigation.TicketEditRoute
import com.erfangholami.solidshare.presentation.theme.AppTheme
import com.erfangholami.solidshare.util.BarcodeRenderer
import com.erfangholami.solidshare.util.BarcodeRenderer.is2d
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailPage(
    navController: NavController,
    viewModel: TicketDetailViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        val window = activity?.window
        val previous = window?.attributes?.screenBrightness
        window?.attributes = window?.attributes?.apply {
            screenBrightness = 1f
        }
        onDispose {
            window?.attributes = window?.attributes?.apply {
                screenBrightness = previous
                    ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
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
                title = {},
                actions = {
                    IconButton(
                        onClick = {
                            navController.navigate(ManageSharingRoute(viewModel.ticketUri))
                        },
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(R.string.share),
                        )
                    }
                    IconButton(
                        onClick = {
                            navController.navigate(
                                TicketEditRoute(ticketUri = viewModel.ticketUri),
                            )
                        },
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.edit),
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.more_options),
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            if (state.ticket?.artifactUri != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ticket_open_pass)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.FileOpen, contentDescription = null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.fetchArtifact { file ->
                                            openArtifact(
                                                context = context,
                                                bytes = file.bytes,
                                                mime = file.contentType,
                                                onNoViewer = viewModel::notifyNoViewer,
                                            )
                                        }
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.delete),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    showDeleteDialog = true
                                },
                            )
                        }
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

                state.ticket != null -> TicketDetailContent(ticket = state.ticket!!)
            }
            if (state.busy) {
                BlockingProgressOverlay(label = stringResource(R.string.wallet_title))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.ticket_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.ticket_delete_message,
                        state.ticket?.title.orEmpty(),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete { navController.popBackStack() }
                    },
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun TicketDetailContent(ticket: Ticket) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TicketCategoryIcon(ticket.category)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ticket.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                val subtitle = listOfNotNull(labelFor(ticket.category), ticket.issuer)
                    .joinToString(" · ")
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!ticket.token.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            TicketBarcode(token = ticket.token, format = ticket.barcodeFormat)
        }

        Spacer(Modifier.height(16.dp))

        ticket.event?.let { event ->
            event.name?.let {
                TicketDetailRow(Icons.Filled.Event, stringResource(R.string.ticket_field_event), it)
            }
            formatTicketDate(event.start)?.let {
                TicketDetailRow(
                    Icons.Filled.Schedule,
                    stringResource(R.string.ticket_field_starts),
                    it,
                )
            }
            formatTicketDate(event.end)?.let {
                TicketDetailRow(
                    Icons.Filled.Schedule,
                    stringResource(R.string.ticket_field_ends),
                    it,
                )
            }
            event.venue?.let { venue ->
                val place = listOfNotNull(venue.name, venue.address).joinToString(", ")
                if (place.isNotBlank()) {
                    TicketDetailRow(
                        Icons.Filled.Place,
                        stringResource(R.string.ticket_field_venue),
                        place,
                    )
                }
            }
        }
        ticket.seat?.let { seat ->
            val seatLine = listOfNotNull(
                seat.section?.let { stringResource(R.string.ticket_field_seat_section) + " " + it },
                seat.row?.let { stringResource(R.string.ticket_field_seat_row) + " " + it },
                seat.number?.let { stringResource(R.string.ticket_field_seat_number) + " " + it },
            ).joinToString(" · ")
            if (seatLine.isNotBlank()) {
                TicketDetailRow(
                    Icons.Filled.Chair,
                    stringResource(R.string.ticket_field_seat),
                    seatLine,
                )
            }
        }
        ticket.number?.let {
            TicketDetailRow(
                Icons.Filled.ConfirmationNumber,
                stringResource(R.string.ticket_field_number),
                it,
            )
        }
        ticket.holder?.let {
            TicketDetailRow(Icons.Filled.Badge, stringResource(R.string.ticket_field_holder), it)
        }
        ticket.issuer?.let {
            TicketDetailRow(
                Icons.Filled.Storefront,
                stringResource(R.string.ticket_field_issuer),
                it,
            )
        }
        if (ticket.price != null) {
            TicketDetailRow(
                Icons.Filled.Payments,
                stringResource(R.string.ticket_field_price),
                listOfNotNull(ticket.price, ticket.currency).joinToString(" "),
            )
        }
        formatTicketDate(ticket.validFrom)?.let {
            TicketDetailRow(
                Icons.Filled.Schedule,
                stringResource(R.string.ticket_field_valid_from),
                it,
            )
        }
        formatTicketDate(ticket.validThrough)?.let {
            TicketDetailRow(
                Icons.Filled.Schedule,
                stringResource(R.string.ticket_field_valid_through),
                it,
            )
        }
        ticket.description?.let {
            TicketDetailRow(
                Icons.AutoMirrored.Filled.Notes,
                stringResource(R.string.ticket_field_notes),
                it,
            )
        }
    }
}

@Composable
internal fun TicketBarcode(
    token: String,
    format: TicketBarcodeFormat,
) {
    val density = LocalDensity.current
    val is2d = format.is2d()
    val widthPx = with(density) { 280.dp.roundToPx() }
    val heightPx = with(density) { (if (is2d) 280.dp else 96.dp).roundToPx() }
    val bitmap = remember(token, format) {
        BarcodeRenderer.render(token, format, widthPx, heightPx)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.ticket_barcode_description),
                contentScale = ContentScale.Fit,
                modifier = if (is2d) {
                    Modifier
                        .fillMaxWidth(0.8f)
                        .aspectRatio(1f)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                },
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = token,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = Color.Black,
            textAlign = TextAlign.Center,
            maxLines = 3,
        )
    }
}

@Composable
private fun TicketDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun openArtifact(
    context: Context,
    bytes: ByteArray,
    mime: String,
    onNoViewer: () -> Unit,
) {
    runCatching {
        val dir = File(context.cacheDir, "passes").apply { mkdirs() }
        val extension = if (mime == "application/vnd.apple.pkpass") ".pkpass" else ".bin"
        val file = File(dir, "pass$extension")
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }.onFailure { onNoViewer() }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun TicketDetailContentPreview() {
    AppTheme {
        TicketDetailContent(ticket = PreviewSamples.ticket())
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Ticket Detail Dark")
@Composable
private fun TicketDetailContentDarkPreview() {
    AppTheme(isDarkTheme = true) {
        TicketDetailContent(ticket = PreviewSamples.ticket())
    }
}
