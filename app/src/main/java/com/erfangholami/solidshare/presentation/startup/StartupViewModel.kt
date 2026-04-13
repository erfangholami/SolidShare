package com.erfangholami.solidshare.presentation.startup

import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.settings.SettingsRepository
import com.erfangholami.solidshare.presentation.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    settingsRepository: SettingsRepository,
) : BaseViewModel() {

    // null = still loading, true/false = auth state confirmed
    private val _hasLoggedInUser = MutableStateFlow<Boolean?>(null)
    val hasLoggedInUser = _hasLoggedInUser.asStateFlow()

    val settingsState = settingsRepository.getSettingPreferences()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = runBlocking { settingsRepository.getSettingPreferences().first() }
        )

    init {
        viewModelScope.launch {
            // getActiveWebId() is suspend and internally awaits Authenticator initialization,
            // so by the time it returns we have an accurate isUserAuthorized() reading.
            authRepository.getActiveWebId()
            _hasLoggedInUser.value = authRepository.isUserAuthorized()

            // Continue observing so logout/login elsewhere updates the startup state.
            authRepository.activeProfileFlow.collect {
                _hasLoggedInUser.value = authRepository.isUserAuthorized()
            }
        }
    }
}
