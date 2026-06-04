package com.erfangholami.solidshare.presentation.sharing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.domain.model.PublicProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ShareProfileViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {

    val activeProfile: StateFlow<PublicProfile?> = authRepository.activeProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
