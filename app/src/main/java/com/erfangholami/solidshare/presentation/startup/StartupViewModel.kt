package com.erfangholami.solidshare.presentation.startup

import androidx.lifecycle.ViewModel
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.settings.SettingsRepository
import com.erfangholami.solidshare.telemetry.AuthAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val authAnalytics: AuthAnalytics,
    val settingsRepository: SettingsRepository,
) : ViewModel() {

    suspend fun hasCompletedOnBoarding(): Boolean {
        return settingsRepository.getSettingPreferences().first().hasCompletedOnboarding
    }

    suspend fun isLoggedIn(): Boolean {
        authRepository.getActiveWebId()
        return authRepository.isUserAuthorized()
    }

    fun reportStartupRoute(destination: String) {
        authAnalytics.startupRouted(destination)
    }
}
