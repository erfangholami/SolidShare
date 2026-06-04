package com.erfangholami.solidshare.presentation.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.erfangholami.solidshare.presentation.components.NotificationBell

@Composable
fun TopBarNotificationBell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsBadgeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NotificationBell(
        unreadCount = state.unreadCount,
        onClick = onClick,
        modifier = modifier,
    )
}
