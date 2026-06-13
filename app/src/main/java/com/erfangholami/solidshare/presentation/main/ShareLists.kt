package com.erfangholami.solidshare.presentation.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.ReceivedShare
import com.erfangholami.solidshare.presentation.components.ProfileAvatar
import com.erfangholami.solidshare.presentation.components.RowDivider
import com.erfangholami.solidshare.presentation.components.SheetActionRow
import com.erfangholami.solidshare.presentation.sharing.SharedAccessGroups
import com.erfangholami.solidshare.presentation.sharing.displayNameForUri
import com.erfangholami.solidshare.util.formatRelativeTime

@Composable
internal fun GivenList(
    shares: List<GivenShare>,
    onShowQr: (String) -> Unit,
    onManage: (String) -> Unit,
) {
    if (shares.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Public,
            title = stringResource(R.string.nothing_shared_yet_title),
            subtitle = stringResource(R.string.nothing_shared_yet_subtitle),
            actionLabel = null,
            onAction = null,
        )
        return
    }
    val groups = remember(shares) { shares.groupBy { it.resourceUri }.toList() }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(groups, key = { (resourceUri, _) -> "res|$resourceUri" }) { (resourceUri, recipients) ->
            GivenResourceRow(
                resourceUri = resourceUri,
                recipients = recipients,
                onShowQr = { onShowQr(resourceUri) },
                onManage = { onManage(resourceUri) },
            )
            RowDivider(startIndent = 72.dp)
        }
    }
}

@Composable
internal fun ReceivedList(
    shares: List<ReceivedShare>,
    reshareable: Set<String>,
    onOpen: (ReceivedShare) -> Unit,
    onRemove: (ReceivedShare) -> Unit,
    onReshare: (ReceivedShare) -> Unit,
    onOpenOwner: (ReceivedShare) -> Unit,
) {
    if (shares.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.QrCodeScanner,
            title = stringResource(R.string.nothing_shared_with_you_title),
            subtitle = stringResource(R.string.nothing_received_subtitle),
            actionLabel = null,
            onAction = null,
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(shares, key = { "${it.ownerWebId}|${it.resourceUri}" }) { share ->
            ReceivedRow(
                share = share,
                canReshare = share.resourceUri in reshareable,
                onOpen = { onOpen(share) },
                onRemove = { onRemove(share) },
                onReshare = { onReshare(share) },
                onOpenOwner = { onOpenOwner(share) },
            )
            RowDivider(startIndent = 72.dp)
        }
    }
}

@Composable
private fun GivenResourceRow(
    resourceUri: String,
    recipients: List<GivenShare>,
    onShowQr: () -> Unit,
    onManage: () -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onShowQr)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ResourceTile(resourceUri = resourceUri)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayNameForUri(resourceUri),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(8.dp))
            SharedAccessGroups(shares = recipients)
        }
        IconButton(onClick = { sheetOpen = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.actions))
        }
    }
    if (sheetOpen) {
        GivenShareActionsSheet(
            resourceUri = resourceUri,
            onDismiss = { sheetOpen = false },
            onShowQr = onShowQr,
            onManage = onManage,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GivenShareActionsSheet(
    resourceUri: String,
    onDismiss: () -> Unit,
    onShowQr: () -> Unit,
    onManage: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = displayNameForUri(resourceUri),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            SheetActionRow(
                icon = Icons.Outlined.QrCodeScanner,
                label = stringResource(R.string.show_share_link),
                onClick = {
                    onDismiss()
                    onShowQr()
                },
            )
            SheetActionRow(
                icon = Icons.Outlined.ManageAccounts,
                label = stringResource(R.string.manage_access),
                onClick = {
                    onDismiss()
                    onManage()
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReceivedRow(
    share: ReceivedShare,
    canReshare: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onReshare: () -> Unit,
    onOpenOwner: () -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val addedAt = formatRelativeTime(share.addedAt)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ResourceTile(resourceUri = share.resourceUri)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayNameForUri(share.resourceUri),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(
                    webId = share.ownerWebId,
                    displayName = null,
                    size = 24.dp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onOpenOwner),
                )
                ModeChip(mode = share.mode)
                if (addedAt != null) {
                    Text(
                        text = stringResource(R.string.added_relative, addedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        IconButton(onClick = { sheetOpen = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.actions))
        }
    }
    if (sheetOpen) {
        ReceivedShareActionsSheet(
            share = share,
            canReshare = canReshare,
            onDismiss = { sheetOpen = false },
            onReshare = onReshare,
            onRemove = onRemove,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceivedShareActionsSheet(
    share: ReceivedShare,
    canReshare: Boolean,
    onDismiss: () -> Unit,
    onReshare: () -> Unit,
    onRemove: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = displayNameForUri(share.resourceUri),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            if (canReshare) {
                SheetActionRow(
                    icon = Icons.Outlined.Share,
                    label = stringResource(R.string.reshare),
                    onClick = {
                        onDismiss()
                        onReshare()
                    },
                )
            }
            SheetActionRow(
                icon = Icons.Outlined.PersonRemove,
                label = stringResource(R.string.remove_from_list),
                onClick = {
                    onDismiss()
                    onRemove()
                },
            )
        }
    }
}
