package com.erfangholami.solidshare.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.theme.AppTheme

@Composable
fun PermissionRationaleDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(R.string.permission_allow),
    dismissLabel: String = stringResource(R.string.permission_not_now),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

@Composable
fun PermissionSettingsDialog(
    title: String,
    text: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PermissionRationaleDialogPreview() {
    AppTheme {
        PermissionRationaleDialog(
            title = "Turn on notifications",
            text = "Solid Share can let you know about uploads, downloads, and sharing activity.",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun PermissionSettingsDialogPreview() {
    AppTheme {
        PermissionSettingsDialog(
            title = "Camera access needed",
            text = "Camera access is turned off. Open Settings and enable it to take photos and videos.",
            onOpenSettings = {},
            onDismiss = {},
        )
    }
}
