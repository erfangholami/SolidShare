package com.erfangholami.solidshare.presentation.startup

import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.settings.SettingsRepository
import com.erfangholami.solidshare.presentation.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

@HiltViewModel
class StartupViewModel @Inject constructor(
    authRepository: AuthRepository,
    settingsRepository: SettingsRepository,
) : BaseViewModel() {

    val hasLoggedInUser: StateFlow<Boolean> = authRepository.getLoggedInUsers()
        .map { it.isNotEmpty() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            runBlocking { authRepository.getLoggedInUsers().first().isNotEmpty() }
        )


    val settingsState = settingsRepository.getSettingPreferences()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = runBlocking{settingsRepository.getSettingPreferences().first()}
        )
}