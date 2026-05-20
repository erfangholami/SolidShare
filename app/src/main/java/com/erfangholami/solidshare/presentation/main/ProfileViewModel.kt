package com.erfangholami.solidshare.presentation.main

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.pondersource.shared.domain.profile.Profile
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
) : ViewModel() {

    val accounts: StateFlow<List<Profile>> = authRepository.loggedInProfilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeWebId: StateFlow<String> = authRepository.activeWebIdFlow
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val logoutLoading = mutableStateOf(false)

    val navigateToLogin: StateFlow<Boolean> = authRepository.isAuthorizedFlow
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun switchAccount(webId: String) {
        viewModelScope.launch {
            authRepository.setActiveWebId(webId)
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
