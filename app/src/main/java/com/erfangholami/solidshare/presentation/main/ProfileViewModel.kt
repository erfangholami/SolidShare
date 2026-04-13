package com.erfangholami.solidshare.presentation.main

import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.presentation.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : BaseViewModel() {

    val activeProfile = authRepository.activeProfileFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            authRepository.getProfile()
        )

    val allProfiles = authRepository.activeProfileFlow
        .map { authRepository.getAllLoggedInProfiles() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            authRepository.getAllLoggedInProfiles()
        )

    private val _navigateToLogin = MutableSharedFlow<Unit>()
    val navigateToLogin = _navigateToLogin.asSharedFlow()

    fun switchAccount(webId: String) {
        viewModelScope.launch {
            authRepository.setActiveWebId(webId)
        }
    }

    fun logout(webId: String) {
        viewModelScope.launch {
            authRepository.resetProfile(webId)
            if (!authRepository.isUserAuthorized()) {
                _navigateToLogin.emit(Unit)
            }
        }
    }
}
