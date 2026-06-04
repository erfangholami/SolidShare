package com.erfangholami.solidshare.presentation.sharing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareReceiver
import com.erfangholami.solidshare.presentation.util.copyText
import com.erfangholami.solidshare.presentation.util.generateQrBitmap
import com.erfangholami.solidshare.presentation.util.rememberQrLogo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateShareSheet(
    initialResourceUri: String? = null,
    onDismiss: () -> Unit,
    submit: suspend (resourceUri: String, mode: ShareMode, receiver: ShareReceiver) -> GivenShare,
    deepLinkFor: (String) -> String,
) {
    var stage by rememberSaveable(stateSaver = stageSaver) { mutableStateOf<Stage>(Stage.Form) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val shareCreateFailedMsg = stringResource(R.string.share_create_failed)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        when (val s = stage) {
            is Stage.Form -> ShareFormContent(
                initialResourceUri = initialResourceUri,
                onCancel = onDismiss,
                onSubmit = { uri, mode, receiver ->
                    stage = Stage.Submitting
                    scope.launch {
                        try {
                            val share = submit(uri, mode, receiver)
                            stage = Stage.Result(share, deepLinkFor(share.resourceUri))
                        } catch (e: Exception) {
                            stage = Stage.Error(e.message ?: shareCreateFailedMsg)
                        }
                    }
                },
            )

            is Stage.Submitting -> SubmittingContent(label = stringResource(R.string.creating_share))

            is Stage.Result -> ShareResultContent(
                share = s.share,
                deepLink = s.deepLink,
                onClose = onDismiss,
            )

            is Stage.Error -> ErrorContent(
                message = s.message,
                onRetry = { stage = Stage.Form },
                onClose = onDismiss,
            )
        }
    }
}

@Composable
private fun ShareFormContent(
    initialResourceUri: String?,
    onCancel: () -> Unit,
    onSubmit: (String, ShareMode, ShareReceiver) -> Unit,
) {
    var resourceUri by rememberSaveable { mutableStateOf(initialResourceUri.orEmpty()) }
    var mode by rememberSaveable { mutableStateOf(ShareMode.READ) }
    var receiverKind by rememberSaveable { mutableStateOf(ReceiverKind.WEBID) }
    var receiverValue by rememberSaveable { mutableStateOf("") }

    val canSubmit = remember(resourceUri, receiverKind, receiverValue) {
        resourceUri.isNotBlank() &&
                (receiverKind == ReceiverKind.PUBLIC || receiverValue.isNotBlank())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.share_resource_title), style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = resourceUri,
            onValueChange = { resourceUri = it },
            label = { Text(stringResource(R.string.resource_url_label)) },
            enabled = initialResourceUri == null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.access_label), style = MaterialTheme.typography.labelMedium)
        Column(modifier = Modifier.selectableGroup()) {
            ShareMode.entries.forEach { option ->
                OptionRow(
                    label = when (option) {
                        ShareMode.READ -> stringResource(R.string.share_mode_read)
                        ShareMode.APPEND -> stringResource(R.string.share_mode_append)
                        ShareMode.WRITE -> stringResource(R.string.share_mode_write)
                    },
                    selected = option == mode,
                    onClick = { mode = option },
                )
            }
        }

        Text(stringResource(R.string.receiver_label), style = MaterialTheme.typography.labelMedium)
        Column(modifier = Modifier.selectableGroup()) {
            ReceiverKind.entries.forEach { kind ->
                OptionRow(
                    label = when (kind) {
                        ReceiverKind.WEBID -> stringResource(R.string.receiver_kind_webid)
                        ReceiverKind.GROUP -> stringResource(R.string.receiver_kind_group)
                        ReceiverKind.PUBLIC -> stringResource(R.string.receiver_anyone_with_link)
                    },
                    selected = kind == receiverKind,
                    onClick = { receiverKind = kind },
                )
            }
        }

        if (receiverKind != ReceiverKind.PUBLIC) {
            OutlinedTextField(
                value = receiverValue,
                onValueChange = { receiverValue = it },
                label = {
                    Text(
                        if (receiverKind == ReceiverKind.WEBID) stringResource(R.string.receiver_webid_label)
                        else stringResource(R.string.group_uri_label)
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            Spacer(Modifier.size(8.dp))
            Button(
                enabled = canSubmit,
                onClick = {
                    val receiver = when (receiverKind) {
                        ReceiverKind.WEBID -> ShareReceiver.WebIdReceiver(receiverValue.trim())
                        ReceiverKind.GROUP -> ShareReceiver.GroupReceiver(receiverValue.trim())
                        ReceiverKind.PUBLIC -> ShareReceiver.Public
                    }
                    onSubmit(resourceUri.trim(), mode, receiver)
                },
            ) { Text(stringResource(R.string.share)) }
        }
    }
}

@Composable
internal fun ShareResultContent(
    share: GivenShare,
    deepLink: String,
    onClose: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val logo = rememberQrLogo()
    val bitmap = remember(deepLink, logo) { generateQrBitmap(deepLink, 720, logo = logo) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.share_created_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(
                R.string.share_summary,
                share.mode.name.lowercase(),
                describeReceiver(share.receiver),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.qr_code_content_description),
            modifier = Modifier.size(240.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = deepLink,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                scope.launch { clipboard.copyText(deepLink) }
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_link))
            }
        }

        Text(
            text = stringResource(R.string.resource_prefix, share.resourceUri),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.done)) }
    }
}

@Composable
internal fun SubmittingContent(label: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.share_create_error_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(onClick = onClose) { Text(stringResource(R.string.close)) }
            Spacer(Modifier.size(8.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private sealed class Stage {
    data object Form : Stage()
    data object Submitting : Stage()
    data class Result(val share: GivenShare, val deepLink: String) : Stage()
    data class Error(val message: String) : Stage()
}

private val stageSaver = androidx.compose.runtime.saveable.Saver<Stage, Any>(
    save = { stage ->
        when (stage) {
            is Stage.Form -> "form"
            is Stage.Error -> listOf("error", stage.message)
            else -> "form"
        }
    },
    restore = {
        when (it) {
            "form" -> Stage.Form
            is List<*> -> if (it.firstOrNull() == "error") {
                Stage.Error(it.getOrNull(1) as? String ?: "")
            } else Stage.Form

            else -> Stage.Form
        }
    },
)
