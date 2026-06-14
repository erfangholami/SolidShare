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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.ReceivedShare
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.presentation.components.EmptyState
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.components.ResourceAction
import com.erfangholami.solidshare.presentation.components.ResourceActions
import com.erfangholami.solidshare.presentation.components.ResourceActionsSheet
import com.erfangholami.solidshare.presentation.components.RowDivider
import com.erfangholami.solidshare.presentation.container.itemCountLabel
import com.erfangholami.solidshare.presentation.container.metaSubtitle
import com.erfangholami.solidshare.presentation.sharing.SharedAccessGroups
import com.erfangholami.solidshare.presentation.sharing.SharedWithOwner
import com.erfangholami.solidshare.presentation.sharing.displayNameForUri
import com.erfangholami.solidshare.presentation.sharing.isContainerUri
import com.erfangholami.solidshare.presentation.theme.AppTheme
import com.erfangholami.solidshare.util.formatRelativeTime

@Composable
internal fun GivenList(
    shares: List<GivenShare>,
    onShowQr: (String) -> Unit,
    onManage: (String) -> Unit,
    loadMeta: suspend (String) -> ContainerItem?,
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
                loadMeta = loadMeta,
            )
            RowDivider(startIndent = 72.dp)
        }
    }
}

@Composable
internal fun ReceivedList(
    shares: List<ReceivedShare>,
    onOpen: (ReceivedShare) -> Unit,
    onRemove: (ReceivedShare) -> Unit,
    onReshare: (ReceivedShare) -> Unit,
    onOpenOwner: (ReceivedShare) -> Unit,
    onCopyLink: (ReceivedShare) -> Unit,
    onDownload: (ReceivedShare) -> Unit,
    onInfo: (ReceivedShare) -> Unit,
    onDelete: (ReceivedShare) -> Unit,
    loadMeta: suspend (String) -> ContainerItem?,
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
                onOpen = { onOpen(share) },
                onRemove = { onRemove(share) },
                onReshare = { onReshare(share) },
                onOpenOwner = { onOpenOwner(share) },
                onCopyLink = { onCopyLink(share) },
                onDownload = { onDownload(share) },
                onInfo = { onInfo(share) },
                onDelete = { onDelete(share) },
                loadMeta = loadMeta,
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
    loadMeta: suspend (String) -> ContainerItem?,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var headerItem by remember(resourceUri) { mutableStateOf<ContainerItem?>(null) }
    LaunchedEffect(sheetOpen) {
        if (sheetOpen && headerItem == null) headerItem = loadMeta(resourceUri)
    }
    val sharedAt = remember(recipients) {
        recipients.mapNotNull { it.createdAt }.maxOrNull()
    }?.let { formatRelativeTime(it) }
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
            if (sharedAt != null) {
                Spacer(Modifier.size(6.dp))
                Text(
                    text = stringResource(R.string.shared_relative, sharedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = { sheetOpen = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.actions))
        }
    }
    if (sheetOpen) {
        ResourceActionsSheet(
            resourceUri = resourceUri,
            subtitle = headerItem?.let { it.metaSubtitle(it.itemCount?.let { c -> itemCountLabel(c) }) },
            actions = ResourceActions.sharedByMe,
            onDismiss = { sheetOpen = false },
            onAction = { action ->
                when (action) {
                    ResourceAction.SHOW_SHARE_LINK -> onShowQr()
                    ResourceAction.MANAGE_ACCESS -> onManage()
                    else -> Unit
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReceivedRow(
    share: ReceivedShare,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onReshare: () -> Unit,
    onOpenOwner: () -> Unit,
    onCopyLink: () -> Unit,
    onDownload: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    loadMeta: suspend (String) -> ContainerItem?,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var headerItem by remember(share.resourceUri) { mutableStateOf<ContainerItem?>(null) }
    LaunchedEffect(sheetOpen) {
        if (sheetOpen && headerItem == null) headerItem = loadMeta(share.resourceUri)
    }
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
                SharedWithOwner(
                    ownerWebId = share.ownerWebId,
                    mode = share.mode,
                    onOwnerClick = onOpenOwner,
                )
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
        ResourceActionsSheet(
            resourceUri = share.resourceUri,
            subtitle = headerItem?.let { it.metaSubtitle(it.itemCount?.let { c -> itemCountLabel(c) }) },
            actions = ResourceActions.sharedWithMe(
                isContainer = isContainerUri(share.resourceUri),
                canEdit = share.mode == ShareMode.WRITE,
            ),
            onDismiss = { sheetOpen = false },
            onAction = { action ->
                when (action) {
                    ResourceAction.REMOVE_FROM_LIST -> onRemove()
                    ResourceAction.RESHARE -> onReshare()
                    ResourceAction.DOWNLOAD -> onDownload()
                    ResourceAction.COPY_LINK -> onCopyLink()
                    ResourceAction.OPEN_IN -> onOpen()
                    ResourceAction.INFO -> onInfo()
                    ResourceAction.DELETE -> onDelete()
                    else -> Unit
                }
            },
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun GivenListPreview() {
    AppTheme {
        GivenList(
            shares = listOf(
                PreviewSamples.givenShare(name = "ben", mode = ShareMode.READ),
                PreviewSamples.givenShare(name = "cara", mode = ShareMode.WRITE),
            ),
            onShowQr = {},
            onManage = {},
            loadMeta = { PreviewSamples.file() },
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ReceivedListPreview() {
    AppTheme {
        ReceivedList(
            shares = listOf(
                PreviewSamples.receivedShare(name = "owner", mode = ShareMode.READ),
            ),
            onOpen = {},
            onRemove = {},
            onReshare = {},
            onOpenOwner = {},
            onCopyLink = {},
            onDownload = {},
            onInfo = {},
            onDelete = {},
            loadMeta = { PreviewSamples.file() },
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun GivenResourceRowPreview() {
    AppTheme {
        GivenResourceRow(
            resourceUri = PreviewSamples.RESOURCE,
            recipients = listOf(
                PreviewSamples.givenShare(name = "ben", mode = ShareMode.READ),
            ),
            onShowQr = {},
            onManage = {},
            loadMeta = { PreviewSamples.file() },
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ReceivedRowPreview() {
    AppTheme {
        ReceivedRow(
            share = PreviewSamples.receivedShare(name = "owner", mode = ShareMode.WRITE),
            onOpen = {},
            onRemove = {},
            onReshare = {},
            onOpenOwner = {},
            onCopyLink = {},
            onDownload = {},
            onInfo = {},
            onDelete = {},
            loadMeta = { PreviewSamples.file() },
        )
    }
}
