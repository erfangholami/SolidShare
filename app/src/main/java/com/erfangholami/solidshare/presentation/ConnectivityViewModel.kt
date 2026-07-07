package com.erfangholami.solidshare.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ConnectivityViewModel @Inject constructor(
    networkMonitor: NetworkMonitor,
) : ViewModel() {
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            networkMonitor.currentlyOnline(),
        )
}

@Composable
fun rememberIsOnline(): State<Boolean> =
    hiltViewModel<ConnectivityViewModel>().isOnline.collectAsStateWithLifecycle()
