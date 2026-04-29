package com.erfangholami.solidshare.presentation.container

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ContainerItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionsBottomSheet(
    item: ContainerItem,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onOpenWith: () -> Unit,
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

            ActionRow(
                icon = Icons.Filled.Share,
                label = stringResource(R.string.share),
                enabled = false,
                onClick = {},
            )

            ActionRow(
                icon = Icons.Filled.Download,
                label = stringResource(R.string.download_to_device),
                onClick = onDownload,
            )

            ActionRow(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                label = stringResource(R.string.open_with),
                onClick = onOpenWith,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            ActionRow(
                icon = Icons.Filled.Delete,
                label = stringResource(R.string.delete),
                enabled = false,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                onClick = {},
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = tint.let {
                if (enabled) it else it.copy(alpha = 0.38f)
            },
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint.let {
                if (enabled) it else it.copy(alpha = 0.38f)
            },
        )
    }
}
