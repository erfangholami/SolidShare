package com.erfangholami.solidshare.presentation.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketEventInfo
import com.erfangholami.solidshare.domain.model.TicketSeatInfo
import com.erfangholami.solidshare.domain.model.TicketVenue
import com.erfangholami.solidshare.presentation.components.BlockingProgressOverlay
import com.erfangholami.solidshare.presentation.components.LoadingState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketEditPage(
    navController: NavController,
    viewModel: TicketEditViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val draft = state.draft

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
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.ticket_form_edit_title
                            else R.string.ticket_form_new_title,
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    TextButton(
                        enabled = !state.saving && state.canSave,
                        onClick = { viewModel.save { navController.popBackStack() } },
                    ) {
                        Text(stringResource(R.string.save))
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
            if (state.loading) {
                LoadingState(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = draft.title,
                        onValueChange = { viewModel.onDraftChange(draft.copy(title = it)) },
                        label = { Text(stringResource(R.string.ticket_field_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CategoryDropdown(
                        selected = draft.category,
                        onSelected = { viewModel.onDraftChange(draft.copy(category = it)) },
                    )
                    OutlinedTextField(
                        value = draft.token.orEmpty(),
                        onValueChange = {
                            viewModel.onDraftChange(
                                draft.copy(
                                    token = it.takeIf { value -> value.isNotBlank() },
                                    barcodeFormat = if (it.isBlank()) {
                                        TicketBarcodeFormat.NONE
                                    } else if (draft.barcodeFormat == TicketBarcodeFormat.NONE) {
                                        TicketBarcodeFormat.QR_CODE
                                    } else {
                                        draft.barcodeFormat
                                    },
                                ),
                            )
                        },
                        label = { Text(stringResource(R.string.ticket_field_token)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!draft.token.isNullOrBlank()) {
                        BarcodeFormatDropdown(
                            selected = draft.barcodeFormat,
                            onSelected = {
                                viewModel.onDraftChange(draft.copy(barcodeFormat = it))
                            },
                        )
                    }
                    OutlinedTextField(
                        value = draft.event?.name.orEmpty(),
                        onValueChange = {
                            viewModel.onDraftChange(draft.withEvent { event -> event.copy(name = it) })
                        },
                        label = { Text(stringResource(R.string.ticket_field_event)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DateTimeField(
                        label = stringResource(R.string.ticket_field_starts),
                        iso = draft.event?.start,
                        onChange = {
                            viewModel.onDraftChange(draft.withEvent { event -> event.copy(start = it) })
                        },
                    )
                    DateTimeField(
                        label = stringResource(R.string.ticket_field_ends),
                        iso = draft.event?.end,
                        onChange = {
                            viewModel.onDraftChange(draft.withEvent { event -> event.copy(end = it) })
                        },
                    )
                    OutlinedTextField(
                        value = draft.event?.venue?.name.orEmpty(),
                        onValueChange = {
                            viewModel.onDraftChange(
                                draft.withVenue { venue -> venue.copy(name = it) },
                            )
                        },
                        label = { Text(stringResource(R.string.ticket_field_venue)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.event?.venue?.address.orEmpty(),
                        onValueChange = {
                            viewModel.onDraftChange(
                                draft.withVenue { venue -> venue.copy(address = it) },
                            )
                        },
                        label = { Text(stringResource(R.string.ticket_field_venue_address)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = draft.seat?.section.orEmpty(),
                            onValueChange = {
                                viewModel.onDraftChange(
                                    draft.withSeat { seat -> seat.copy(section = it) },
                                )
                            },
                            label = { Text(stringResource(R.string.ticket_field_seat_section)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = draft.seat?.row.orEmpty(),
                            onValueChange = {
                                viewModel.onDraftChange(
                                    draft.withSeat { seat -> seat.copy(row = it) },
                                )
                            },
                            label = { Text(stringResource(R.string.ticket_field_seat_row)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = draft.seat?.number.orEmpty(),
                            onValueChange = {
                                viewModel.onDraftChange(
                                    draft.withSeat { seat -> seat.copy(number = it) },
                                )
                            },
                            label = { Text(stringResource(R.string.ticket_field_seat_number)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = draft.number.orEmpty(),
                        onValueChange = {
                            viewModel.onDraftChange(draft.copy(number = it.ifBlank { null }))
                        },
                        label = { Text(stringResource(R.string.ticket_field_number)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.holder.orEmpty(),
                        onValueChange = {
                            viewModel.onDraftChange(draft.copy(holder = it.ifBlank { null }))
                        },
                        label = { Text(stringResource(R.string.ticket_field_holder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.issuer.orEmpty(),
                        onValueChange = {
                            viewModel.onDraftChange(draft.copy(issuer = it.ifBlank { null }))
                        },
                        label = { Text(stringResource(R.string.ticket_field_issuer)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = draft.price.orEmpty(),
                            onValueChange = {
                                viewModel.onDraftChange(draft.copy(price = it.ifBlank { null }))
                            },
                            label = { Text(stringResource(R.string.ticket_field_price)) },
                            singleLine = true,
                            modifier = Modifier.weight(2f),
                        )
                        OutlinedTextField(
                            value = draft.currency.orEmpty(),
                            onValueChange = {
                                viewModel.onDraftChange(draft.copy(currency = it.ifBlank { null }))
                            },
                            label = { Text(stringResource(R.string.ticket_field_currency)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    DateTimeField(
                        label = stringResource(R.string.ticket_field_valid_from),
                        iso = draft.validFrom,
                        onChange = { viewModel.onDraftChange(draft.copy(validFrom = it)) },
                    )
                    DateTimeField(
                        label = stringResource(R.string.ticket_field_valid_through),
                        iso = draft.validThrough,
                        onChange = { viewModel.onDraftChange(draft.copy(validThrough = it)) },
                    )
                    OutlinedTextField(
                        value = draft.description.orEmpty(),
                        onValueChange = {
                            viewModel.onDraftChange(draft.copy(description = it.ifBlank { null }))
                        },
                        label = { Text(stringResource(R.string.ticket_field_notes)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(32.dp))
                }
            }
            if (state.saving) {
                BlockingProgressOverlay(label = stringResource(R.string.save))
            }
        }
    }
}

private fun TicketDraft.withEvent(
    transform: (TicketEventInfo) -> TicketEventInfo,
): TicketDraft = copy(event = transform(event ?: TicketEventInfo()).normalized())

private fun TicketDraft.withVenue(
    transform: (TicketVenue) -> TicketVenue,
): TicketDraft = withEvent { event ->
    event.copy(venue = transform(event.venue ?: TicketVenue()).normalized())
}

private fun TicketDraft.withSeat(
    transform: (TicketSeatInfo) -> TicketSeatInfo,
): TicketDraft {
    val updated = transform(seat ?: TicketSeatInfo())
    val normalized = TicketSeatInfo(
        number = updated.number?.ifBlank { null },
        row = updated.row?.ifBlank { null },
        section = updated.section?.ifBlank { null },
    )
    val isEmpty = normalized.number == null && normalized.row == null && normalized.section == null
    return copy(seat = if (isEmpty) null else normalized)
}

private fun TicketEventInfo.normalized(): TicketEventInfo? {
    val cleaned = TicketEventInfo(
        name = name?.ifBlank { null },
        start = start?.ifBlank { null },
        end = end?.ifBlank { null },
        venue = venue,
    )
    val isEmpty = cleaned.name == null && cleaned.start == null &&
            cleaned.end == null && cleaned.venue == null
    return if (isEmpty) null else cleaned
}

private fun TicketVenue.normalized(): TicketVenue? {
    val cleaned = TicketVenue(
        name = name?.ifBlank { null },
        address = address?.ifBlank { null },
    )
    return if (cleaned.name == null && cleaned.address == null) null else cleaned
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selected: TicketCategory,
    onSelected: (TicketCategory) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = labelFor(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.ticket_field_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            TicketCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(labelFor(category)) },
                    leadingIcon = { Icon(iconFor(category), contentDescription = null) },
                    onClick = {
                        expanded = false
                        onSelected(category)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarcodeFormatDropdown(
    selected: TicketBarcodeFormat,
    onSelected: (TicketBarcodeFormat) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.name.replace('_', ' '),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.ticket_field_barcode_format)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            TicketBarcodeFormat.entries.filter { it != TicketBarcodeFormat.NONE }
                .forEach { format ->
                    DropdownMenuItem(
                        text = { Text(format.name.replace('_', ' ')) },
                        onClick = {
                            expanded = false
                            onSelected(format)
                        },
                    )
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeField(
    label: String,
    iso: String?,
    onChange: (String?) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    OutlinedTextField(
        value = formatTicketDate(iso).orEmpty(),
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(label) },
        trailingIcon = {
            Row {
                if (!iso.isNullOrBlank()) {
                    TextButton(onClick = { onChange(null) }) {
                        Text(stringResource(R.string.remove))
                    }
                }
                TextButton(onClick = { showDatePicker = true }) {
                    Text(stringResource(R.string.edit))
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            pickedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            showTimePicker = true
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(initialHour = 12, initialMinute = 0)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(label) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val date = pickedDate
                        if (date != null) {
                            val instant = date
                                .atTime(LocalTime.of(timePickerState.hour, timePickerState.minute))
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                            onChange(instant.toString())
                        }
                        showTimePicker = false
                    },
                ) {
                    Text(stringResource(R.string.done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
