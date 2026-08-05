package com.erfangholami.solidshare.presentation.sharing

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.error.AppOperation
import com.erfangholami.solidshare.domain.error.LocalErrorPresenter
import com.erfangholami.solidshare.domain.error.rethrowIfCancellation
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.ShareReceiver
import com.erfangholami.solidshare.presentation.components.LoadingState
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.components.ProfileAvatar
import com.erfangholami.solidshare.presentation.components.EntityRow
import com.erfangholami.solidshare.presentation.components.RequiresConnectionHint
import com.erfangholami.solidshare.presentation.components.SheetActionRow
import com.erfangholami.solidshare.presentation.rememberIsOnline
import com.erfangholami.solidshare.presentation.theme.AppTheme
import com.erfangholami.solidshare.presentation.util.pasteText
import com.erfangholami.solidshare.util.formatRelativeTime
import kotlinx.coroutines.launch

@Composable
internal fun EntityHeaderRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(56.dp),
            ) {}
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun EntityPeopleCard(
    people: List<GivenShare>,
    isOnline: Boolean,
    onPersonClick: (GivenShare) -> Unit,
    onPersonAvatarClick: (String) -> Unit,
    onAddPerson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            if (people.isEmpty()) {
                Text(
                    text = stringResource(R.string.entity_no_people),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            people.forEach { share ->
                EntityPersonRow(
                    share = share,
                    onClick = { onPersonClick(share) },
                    onAvatarClick = (share.receiver as? ShareReceiver.WebIdReceiver)
                        ?.let { r -> { onPersonAvatarClick(r.webId) } },
                )
            }
            OutlinedButton(
                onClick = onAddPerson,
                enabled = isOnline,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.add_people))
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Outlined.AddCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            RequiresConnectionHint(
                visible = !isOnline,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun EntityPersonRow(
    share: GivenShare,
    onClick: () -> Unit,
    onAvatarClick: (() -> Unit)?,
) {
    val timeText = share.createdAt
        ?.let { formatRelativeTime(it) }
        ?.let { stringResource(R.string.access_granted_relative, it) }
    EntityRow(
        title = describeReceiver(share.receiver),
        subtitle = timeText,
        modifier = Modifier.clickable(onClick = onClick),
        leading = {
            ProfileAvatar(
                webId = (share.receiver as? ShareReceiver.WebIdReceiver)?.webId.orEmpty(),
                displayName = null,
                size = 40.dp,
                modifier = if (onAvatarClick != null) {
                    Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onAvatarClick)
                } else {
                    Modifier
                },
            )
        },
        trailing = {
            Text(
                text = stringResource(R.string.entity_can_view),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntityRemoveAccessSheet(
    isOnline: Boolean,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        RequiresConnectionHint(
            visible = !isOnline,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        SheetActionRow(
            icon = Icons.Filled.Delete,
            label = stringResource(R.string.remove_access),
            enabled = isOnline,
            tint = MaterialTheme.colorScheme.error,
            onClick = onRemove,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntityShareAddSheet(
    onDismiss: () -> Unit,
    submit: suspend (receiverWebId: String) -> GivenShare,
    deepLinkFor: (String) -> String,
    bareUrlFor: (String) -> String,
) {
    var stage by rememberSaveable(stateSaver = entityStageSaver) {
        mutableStateOf<EntityShareStage>(EntityShareStage.Form)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val shareCreateFailedMsg = stringResource(R.string.share_create_failed)
    val errors = LocalErrorPresenter.current
    val isOnline by rememberIsOnline()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        when (val s = stage) {
            is EntityShareStage.Form -> EntityShareFormContent(
                isOnline = isOnline,
                onSubmit = { receiver ->
                    stage = EntityShareStage.Submitting
                    scope.launch {
                        try {
                            val share = submit(receiver)
                            stage = EntityShareStage.Result(
                                resourceUri = share.resourceUri,
                                deepLink = deepLinkFor(share.resourceUri),
                                bareUrl = bareUrlFor(share.resourceUri),
                            )
                        } catch (e: Exception) {
                            e.rethrowIfCancellation()
                            stage = EntityShareStage.Error(
                                errors?.message(e, AppOperation.CREATE_SHARE, subject = receiver)
                                    ?: shareCreateFailedMsg,
                            )
                        }
                    }
                },
            )

            is EntityShareStage.Submitting -> LoadingState(
                label = stringResource(R.string.creating_share),
                modifier = Modifier.padding(32.dp),
            )

            is EntityShareStage.Result -> ShareLinkPanel(
                resourceUri = s.resourceUri,
                deepLink = s.deepLink,
                bareUrl = s.bareUrl,
                showPublicOption = false,
            )

            is EntityShareStage.Error -> ErrorContent(
                message = s.message,
                onRetry = { stage = EntityShareStage.Form },
                onClose = onDismiss,
            )
        }
    }
}

@Composable
private fun EntityShareFormContent(
    isOnline: Boolean,
    onSubmit: (String) -> Unit,
) {
    var receiverValue by rememberSaveable { mutableStateOf("") }
    var contactPickerOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.receiver_webid_label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = receiverValue,
            onValueChange = { receiverValue = it },
            placeholder = { Text(stringResource(R.string.receiver_webid_placeholder)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                Row {
                    IconButton(onClick = { contactPickerOpen = true }) {
                        Icon(
                            Icons.Filled.Contacts,
                            contentDescription = stringResource(R.string.share_pick_from_contacts),
                        )
                    }
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
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        RequiresConnectionHint(visible = !isOnline)
        Button(
            onClick = { onSubmit(receiverValue.trim()) },
            enabled = receiverValue.isNotBlank() && isOnline,
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
        Spacer(Modifier.height(16.dp))
    }

    val receiverPicker = LocalReceiverPicker.current
    if (contactPickerOpen && receiverPicker != null) {
        receiverPicker.Picker(
            onPick = { webId ->
                receiverValue = webId
                contactPickerOpen = false
            },
            onDismiss = { contactPickerOpen = false },
        )
    }
}

private sealed class EntityShareStage {
    data object Form : EntityShareStage()
    data object Submitting : EntityShareStage()
    data class Result(
        val resourceUri: String,
        val deepLink: String,
        val bareUrl: String,
    ) : EntityShareStage()

    data class Error(val message: String) : EntityShareStage()
}

private val entityStageSaver = androidx.compose.runtime.saveable.Saver<EntityShareStage, Any>(
    save = { stage ->
        when (stage) {
            is EntityShareStage.Error -> listOf("error", stage.message)
            else -> "form"
        }
    },
    restore = {
        when (it) {
            is List<*> -> if (it.firstOrNull() == "error") {
                EntityShareStage.Error(it.getOrNull(1) as? String ?: "")
            } else {
                EntityShareStage.Form
            }

            else -> EntityShareStage.Form
        }
    },
)

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun EntityHeaderRowPreview() {
    AppTheme {
        Surface {
            EntityHeaderRow(
                icon = Icons.Filled.ConfirmationNumber,
                title = "Coldplay — Music of the Spheres",
                subtitle = "Ticket",
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun EntityPeopleCardPreview() {
    AppTheme {
        Surface {
            EntityPeopleCard(
                people = listOf(
                    PreviewSamples.givenShare(name = "ben"),
                    PreviewSamples.givenShare(name = "mia"),
                ),
                isOnline = true,
                onPersonClick = {},
                onPersonAvatarClick = {},
                onAddPerson = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun EntityPeopleCardEmptyPreview() {
    AppTheme {
        Surface {
            EntityPeopleCard(
                people = emptyList(),
                isOnline = false,
                onPersonClick = {},
                onPersonAvatarClick = {},
                onAddPerson = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun EntityShareFormContentPreview() {
    AppTheme {
        Surface {
            EntityShareFormContent(isOnline = true, onSubmit = {})
        }
    }
}
