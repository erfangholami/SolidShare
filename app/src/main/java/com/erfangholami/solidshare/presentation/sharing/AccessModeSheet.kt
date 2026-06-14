package com.erfangholami.solidshare.presentation.sharing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.presentation.components.SheetActionRow
import com.erfangholami.solidshare.presentation.theme.AppTheme

@Composable
internal fun AccessModeSheetContent(
    current: ShareMode,
    onSelect: (ShareMode) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        ShareMode.entries.forEach { mode ->
            SheetRadioRow(
                icon = iconFor(mode),
                label = labelFor(mode),
                selected = mode == current,
                onClick = { onSelect(mode) },
            )
        }
        if (onRemove != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SheetActionRow(
                icon = Icons.Outlined.Delete,
                label = stringResource(R.string.remove_access),
                tint = MaterialTheme.colorScheme.error,
                onClick = onRemove,
            )
        }
    }
}

@Composable
internal fun SheetRadioRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview(name = "AccessModeSheetContent · removable", showBackground = true, widthDp = 360)
@Composable
private fun AccessModeSheetContentRemovablePreview() {
    AppTheme {
        Surface {
            AccessModeSheetContent(
                current = ShareMode.APPEND,
                onSelect = {},
                onRemove = {},
            )
        }
    }
}

@Preview(name = "AccessModeSheetContent · no remove", showBackground = true, widthDp = 360)
@Composable
private fun AccessModeSheetContentNoRemovePreview() {
    AppTheme {
        Surface {
            AccessModeSheetContent(
                current = ShareMode.APPEND,
                onSelect = {},
                onRemove = null,
            )
        }
    }
}

@Preview(name = "SheetRadioRow", showBackground = true, widthDp = 360)
@Composable
private fun SheetRadioRowPreview() {
    AppTheme {
        Surface {
            SheetRadioRow(
                icon = Icons.Filled.Visibility,
                label = "View",
                selected = true,
                onClick = {},
            )
        }
    }
}
