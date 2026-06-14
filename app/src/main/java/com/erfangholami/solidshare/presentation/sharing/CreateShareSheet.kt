package com.erfangholami.solidshare.presentation.sharing

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareReceiver
import com.erfangholami.solidshare.presentation.components.LoadingState
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.theme.AppTheme
import com.erfangholami.solidshare.presentation.util.pasteText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateShareSheet(
    resourceUri: String,
    onDismiss: () -> Unit,
    submit: suspend (resourceUri: String, mode: ShareMode, receiver: ShareReceiver) -> GivenShare,
    deepLinkFor: (String) -> String,
    bareUrlFor: (String) -> String,
    resourceSubtitle: String? = null,
) {
    var stage by rememberSaveable(stateSaver = stageSaver) { mutableStateOf<Stage>(Stage.Form) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val shareCreateFailedMsg = stringResource(R.string.share_create_failed)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        when (val s = stage) {
            is Stage.Form -> ShareFormContent(
                resourceUri = resourceUri,
                resourceSubtitle = resourceSubtitle,
                onSubmit = { uri, mode, receiver ->
                    stage = Stage.Submitting
                    scope.launch {
                        try {
                            val share = submit(uri, mode, receiver)
                            stage = Stage.Result(
                                resourceUri = share.resourceUri,
                                deepLink = deepLinkFor(share.resourceUri),
                                bareUrl = bareUrlFor(share.resourceUri),
                                isPublic = receiver is ShareReceiver.Public,
                            )
                        } catch (e: Exception) {
                            stage = Stage.Error(e.message ?: shareCreateFailedMsg)
                        }
                    }
                },
            )

            is Stage.Submitting -> LoadingState(
                label = stringResource(R.string.creating_share),
                modifier = Modifier.padding(32.dp),
            )

            is Stage.Result -> ShareLinkPanel(
                resourceUri = s.resourceUri,
                deepLink = s.deepLink,
                bareUrl = s.bareUrl,
                showPublicOption = s.isPublic,
            )

            is Stage.Error -> ErrorContent(
                message = s.message,
                onRetry = { stage = Stage.Form },
                onClose = onDismiss,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareFormContent(
    resourceUri: String,
    resourceSubtitle: String?,
    onSubmit: (String, ShareMode, ShareReceiver) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(ShareMode.READ) }
    var anyoneWithLink by rememberSaveable { mutableStateOf(false) }
    var receiverValue by rememberSaveable { mutableStateOf("") }
    var whoSheetOpen by remember { mutableStateOf(false) }
    var modeSheetOpen by remember { mutableStateOf(false) }

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val canSubmit = anyoneWithLink || receiverValue.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ResourceHeaderRow(resourceUri = resourceUri, subtitle = resourceSubtitle)

        HorizontalDivider()

        SectionLabel(stringResource(R.string.who_can_access))
        SelectorField(
            text = if (anyoneWithLink) {
                stringResource(R.string.receiver_anyone_with_link)
            } else {
                stringResource(R.string.receiver_webid_label)
            },
            onClick = { whoSheetOpen = true },
        )

        if (!anyoneWithLink) {
            OutlinedTextField(
                value = receiverValue,
                onValueChange = { receiverValue = it },
                placeholder = { Text(stringResource(R.string.receiver_webid_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            clipboard.pasteText()?.let { receiverValue = it.trim() }
                        }
                    }) {
                        Icon(
                            Icons.Outlined.ContentPaste,
                            contentDescription = stringResource(R.string.paste),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionLabel(stringResource(R.string.access_mode))
        SelectorField(
            text = labelFor(mode),
            leadingIcon = iconFor(mode),
            onClick = { modeSheetOpen = true },
        )

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = {
                val receiver = if (anyoneWithLink) {
                    ShareReceiver.Public
                } else {
                    ShareReceiver.WebIdReceiver(receiverValue.trim())
                }
                onSubmit(resourceUri, mode, receiver)
            },
            enabled = canSubmit,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(
                Icons.Outlined.Share,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.share))
        }
    }

    if (whoSheetOpen) {
        val whoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { whoSheetOpen = false },
            sheetState = whoSheetState,
        ) {
            WhoCanAccessSheet(
                anyoneWithLink = anyoneWithLink,
                onSelect = {
                    whoSheetOpen = false
                    anyoneWithLink = it
                },
            )
        }
    }

    if (modeSheetOpen) {
        val modeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { modeSheetOpen = false },
            sheetState = modeSheetState,
        ) {
            AccessModeSheetContent(
                current = mode,
                onSelect = {
                    modeSheetOpen = false
                    mode = it
                },
            )
        }
    }
}

@Composable
private fun WhoCanAccessSheet(
    anyoneWithLink: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        SheetRadioRow(
            icon = Icons.Outlined.Person,
            label = stringResource(R.string.receiver_webid_label),
            selected = !anyoneWithLink,
            onClick = { onSelect(false) },
        )
        SheetRadioRow(
            icon = Icons.Outlined.Public,
            label = stringResource(R.string.receiver_anyone_with_link),
            selected = anyoneWithLink,
            onClick = { onSelect(true) },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SelectorField(
    text: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

private sealed class Stage {
    data object Form : Stage()
    data object Submitting : Stage()
    data class Result(
        val resourceUri: String,
        val deepLink: String,
        val bareUrl: String,
        val isPublic: Boolean,
    ) : Stage()
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ShareFormContentPreview() {
    AppTheme {
        ShareFormContent(
            resourceUri = PreviewSamples.RESOURCE,
            resourceSubtitle = "JPG · 2.4 MB",
            onSubmit = { _, _, _ -> },
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun WhoCanAccessSheetPreview() {
    AppTheme {
        WhoCanAccessSheet(
            anyoneWithLink = false,
            onSelect = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SectionLabelPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionLabel(text = "Who can access")
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Selector field")
@Composable
private fun SelectorFieldPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            SelectorField(
                text = "Anyone with the link",
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Selector field with icon")
@Composable
private fun SelectorFieldWithIconPreview() {
    AppTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            SelectorField(
                text = "View",
                onClick = {},
                leadingIcon = Icons.Outlined.Person,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ErrorContentPreview() {
    AppTheme {
        ErrorContent(
            message = "Couldn't create the share.",
            onRetry = {},
            onClose = {},
        )
    }
}
