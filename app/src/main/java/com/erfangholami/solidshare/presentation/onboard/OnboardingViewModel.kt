package com.erfangholami.solidshare.presentation.onboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.data.repo.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    val settingsRepository: SettingsRepository,
) : ViewModel() {

    fun onBoardingCompleted() {
        viewModelScope.launch {
            settingsRepository.completeOnboarding(true)
        }
    }
}