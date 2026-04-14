package com.erfangholami.solidshare.presentation.startup

import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.settings.SettingsRepository
import com.erfangholami.solidshare.presentation.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    val settingsRepository: SettingsRepository,
) : BaseViewModel() {

    suspend fun hasCompletedOnBoarding(): Boolean {
        return settingsRepository.getSettingPreferences().first().hasCompletedOnboarding
    }

    suspend fun isLoggedIn(): Boolean {
        return authRepository.activeWebIdFlow.map {
            !it.isNullOrEmpty()
        }.first()
    }
}
