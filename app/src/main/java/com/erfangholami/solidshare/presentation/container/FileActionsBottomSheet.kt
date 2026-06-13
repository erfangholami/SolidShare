package com.erfangholami.solidshare.presentation.container

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.presentation.components.SheetActionRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionsBottomSheet(
    item: ContainerItem,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onManageAccess: () -> Unit,
    onDuplicate: () -> Unit,
    onDownload: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenIn: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    showShare: Boolean = true,
    showManage: Boolean = true,
    showDuplicate: Boolean = true,
    showDelete: Boolean = true,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            Spacer(Modifier.height(8.dp))

            if (showShare) {
                SheetActionRow(
                    icon = Icons.Filled.Share,
                    label = stringResource(R.string.share),
                    onClick = onShare,
                )
            }

            if (showManage) {
                SheetActionRow(
                    icon = Icons.Outlined.ManageAccounts,
                    label = stringResource(R.string.manage_access),
                    onClick = onManageAccess,
                )
            }

            if (showDuplicate) {
                SheetActionRow(
                    icon = Icons.Outlined.ContentCopy,
                    label = stringResource(R.string.duplicate),
                    onClick = onDuplicate,
                )
            }

            if (!item.isContainer) {
                SheetActionRow(
                    icon = Icons.Filled.Download,
                    label = stringResource(R.string.download_to_device),
                    onClick = onDownload,
                )
            }

            SheetActionRow(
                icon = Icons.Filled.Link,
                label = stringResource(R.string.copy_link),
                onClick = onCopyLink,
            )

            if (!item.isContainer) {
                SheetActionRow(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    label = stringResource(R.string.open_in),
                    onClick = onOpenIn,
                )
            }

            SheetActionRow(
                icon = Icons.Outlined.Info,
                label = stringResource(R.string.info),
                onClick = onInfo,
            )

            if (showDelete) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SheetActionRow(
                    icon = Icons.Outlined.Delete,
                    label = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
            }
        }
    }
}

