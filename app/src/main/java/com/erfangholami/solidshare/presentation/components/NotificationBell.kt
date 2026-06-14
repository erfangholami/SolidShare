package com.erfangholami.solidshare.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.presentation.theme.AppTheme

@Composable
fun NotificationBell(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                }
            },
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = stringResource(R.string.notifications),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Unread")
@Composable
private fun NotificationBellUnreadPreview() {
    AppTheme {
        NotificationBell(unreadCount = 3, onClick = {})
    }
}

@Preview(showBackground = true, widthDp = 360, name = "Empty")
@Composable
private fun NotificationBellEmptyPreview() {
    AppTheme {
        NotificationBell(unreadCount = 0, onClick = {})
    }
}
