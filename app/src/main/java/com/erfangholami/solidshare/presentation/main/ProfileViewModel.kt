package com.erfangholami.solidshare.presentation.main

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.settings.SettingsRepository
import com.erfangholami.solidshare.domain.model.PublicProfile
import com.erfangholami.solidshare.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val accounts: StateFlow<List<PublicProfile>> = authRepository.loggedInProfilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeWebId: StateFlow<String> = authRepository.activeWebIdFlow
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val publicProfile: StateFlow<PublicProfile?> = authRepository.activeProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val themeMode: StateFlow<ThemeMode> = settingsRepository.getSettingPreferences()
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val logoutLoading = mutableStateOf(false)

    val navigateToLogin: StateFlow<Boolean> = authRepository.isAuthorizedFlow
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun switchAccount(webId: String) {
        viewModelScope.launch {
            authRepository.setActiveWebId(webId)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutLoading.value = true
            val webId = activeWebId.value
            authRepository.removeProfile(webId)
            logoutLoading.value = false
        }
    }

    fun logoutAll() {
        viewModelScope.launch {
            logoutLoading.value = true
            authRepository.removeAllProfiles()
            logoutLoading.value = false
        }
    }
}
