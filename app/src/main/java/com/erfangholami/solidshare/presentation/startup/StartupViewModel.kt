package com.erfangholami.solidshare.presentation.startup

import androidx.lifecycle.ViewModel
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    val settingsRepository: SettingsRepository,
) : ViewModel() {

    suspend fun hasCompletedOnBoarding(): Boolean {
        return settingsRepository.getSettingPreferences().first().hasCompletedOnboarding
    }

    suspend fun isLoggedIn(): Boolean {
        return authRepository.activeWebIdFlow.map {
            !it.isNullOrEmpty()
        }.first()
    }
}
