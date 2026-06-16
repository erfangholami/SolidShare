package com.erfangholami.solidshare.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.data.repo.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationPromptViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val shouldPromptAfterLogin: StateFlow<Boolean> =
        settingsRepository.hasPromptedNotificationsPermission()
            .map { prompted -> !prompted }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun markPrompted() {
        viewModelScope.launch { settingsRepository.setPromptedNotificationsPermission(true) }
    }
}
