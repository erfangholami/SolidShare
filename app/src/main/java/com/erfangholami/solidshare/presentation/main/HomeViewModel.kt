package com.erfangholami.solidshare.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.domain.model.PublicProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {

    val profile: StateFlow<PublicProfile?> = authRepository.activeProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
