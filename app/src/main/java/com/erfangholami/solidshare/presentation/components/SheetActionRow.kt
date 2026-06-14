package com.erfangholami.solidshare.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.presentation.theme.AppTheme

@Composable
fun SheetActionRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    val effectiveTint = if (enabled) tint else tint.copy(alpha = 0.38f)
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
            tint = effectiveTint,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = effectiveTint,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Default")
@Composable
private fun SheetActionRowPreview() {
    AppTheme {
        Surface {
            SheetActionRow(
                icon = Icons.Filled.Share,
                label = "Share",
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Destructive")
@Composable
private fun SheetActionRowDestructivePreview() {
    AppTheme {
        Surface {
            SheetActionRow(
                icon = Icons.Filled.Delete,
                label = "Delete",
                tint = MaterialTheme.colorScheme.error,
                onClick = {},
            )
        }
    }
}
