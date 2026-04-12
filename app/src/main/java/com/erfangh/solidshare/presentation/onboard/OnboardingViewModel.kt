package com.erfangh.solidshare.presentation.onboard

import androidx.lifecycle.viewModelScope
import com.erfangh.solidshare.data.repo.settings.SettingsRepository
import com.erfangh.solidshare.presentation.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    val settingsRepository: SettingsRepository,
): BaseViewModel() {

    fun onBoardingCompleted() {
        viewModelScope.launch {
            settingsRepository.completeOnboarding(true)
        }
    }
}