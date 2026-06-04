package com.erfangholami.solidshare.presentation.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.ReceivedShare
import com.erfangholami.solidshare.presentation.sharing.describeReceiver
import com.erfangholami.solidshare.presentation.sharing.displayNameForUri
import com.erfangholami.solidshare.presentation.sharing.labelFor
import com.erfangholami.solidshare.presentation.sharing.shortenWebId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GivenList(
    shares: List<GivenShare>,
    onRevoke: (GivenShare) -> Unit,
    onShowQr: (GivenShare) -> Unit,
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
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(
            shares,
            key = { "${describeKey(it.receiver)}|${it.resourceUri}|${it.mode}" },
        ) { share ->
            GivenRow(
                share = share,
                onRevoke = { onRevoke(share) },
                onShowQr = { onShowQr(share) },
            )
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
            )
        }
    }
}

@Composable
private fun GivenRow(
    share: GivenShare,
    onRevoke: () -> Unit,
    onShowQr: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onShowQr)
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
            Spacer(Modifier.size(2.dp))
            Text(
                text = describeReceiver(share.receiver),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(6.dp))
            ModeChip(label = labelFor(share.mode))
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.actions))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.show_share_link)) },
                    leadingIcon = { Icon(Icons.Outlined.QrCodeScanner, null) },
                    onClick = {
                        menuOpen = false
                        onShowQr()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.revoke), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onRevoke()
                    },
                )
            }
        }
    }
}

@Composable
private fun ReceivedRow(
    share: ReceivedShare,
    canReshare: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onReshare: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
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
            Spacer(Modifier.size(2.dp))
            Text(
                text = stringResource(R.string.from_webid, shortenWebId(share.ownerWebId)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(6.dp))
            ModeChip(label = labelFor(share.mode))
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.actions))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (canReshare) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reshare)) },
                        leadingIcon = { Icon(Icons.Outlined.Share, null) },
                        onClick = {
                            menuOpen = false
                            onReshare()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_from_list)) },
                    leadingIcon = { Icon(Icons.Outlined.PersonRemove, null) },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                )
            }
        }
    }
}
