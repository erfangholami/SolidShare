package com.erfangholami.solidshare.presentation.container

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.ResourceType
import com.erfangholami.solidshare.presentation.components.EmptyState
import com.erfangholami.solidshare.presentation.components.PreviewSamples
import com.erfangholami.solidshare.presentation.theme.AppTheme

@Composable
internal fun ContainerItemRow(
    item: ContainerItem,
    onClick: () -> Unit,
    onMoreOptions: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ResourceTypeIcon(type = item.resourceType, size = 48.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.getItemSubtitle(item.itemCount?.let { itemCountLabel(it) }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onMoreOptions) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ContainerItemCard(
    item: ContainerItem,
    onClick: () -> Unit,
    onMoreOptions: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(item.resourceType.tint.copy(alpha = 0.12f)),
            ) {
                Icon(
                    imageVector = item.resourceType.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center),
                    tint = item.resourceType.tint,
                )
                IconButton(
                    onClick = onMoreOptions,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.more_options),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.getItemSubtitle(item.itemCount?.let { itemCountLabel(it) }),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun EmptyState(modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.this_folder_is_empty),
        modifier = modifier,
        illustration = {
            Image(
                painter = painterResource(R.drawable.empty_container),
                contentDescription = null,
                modifier = Modifier.size(width = 132.dp, height = 107.dp),
            )
        },
        subtitle = stringResource(R.string.this_folder_is_empty_description),
    )
}

@Preview(showBackground = true, widthDp = 360, name = "Row File")
@Composable
private fun ContainerItemRowFilePreview() {
    AppTheme {
        Surface {
            ContainerItemRow(
                item = PreviewSamples.file(name = "trip.jpg", resourceType = ResourceType.IMAGE),
                onClick = {},
                onMoreOptions = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Row Folder")
@Composable
private fun ContainerItemRowFolderPreview() {
    AppTheme {
        Surface {
            ContainerItemRow(
                item = PreviewSamples.folder(name = "Documents", itemCount = 12),
                onClick = {},
                onMoreOptions = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Row Folder Dark")
@Composable
private fun ContainerItemRowFolderDarkPreview() {
    AppTheme(isDarkTheme = true) {
        Surface {
            ContainerItemRow(
                item = PreviewSamples.folder(name = "Documents", itemCount = 12),
                onClick = {},
                onMoreOptions = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 200, name = "Card")
@Composable
private fun ContainerItemCardPreview() {
    AppTheme {
        Surface {
            Box(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.width(180.dp)) {
                    ContainerItemCard(
                        item = PreviewSamples.file(
                            name = "report.pdf",
                            resourceType = ResourceType.PDF,
                        ),
                        onClick = {},
                        onMoreOptions = {},
                    )
                }
            }
        }
    }
}

