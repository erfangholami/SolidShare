package com.erfangholami.solidshare.presentation.notifications

import androidx.lifecycle.ViewModel
import com.erfangholami.solidshare.data.repo.notifications.NotificationsBadgeState
import com.erfangholami.solidshare.data.repo.notifications.NotificationsBadgeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class NotificationsBadgeViewModel @Inject constructor(
    badgeStore: NotificationsBadgeStore,
) : ViewModel() {

    val state: StateFlow<NotificationsBadgeState> = badgeStore.state
}
