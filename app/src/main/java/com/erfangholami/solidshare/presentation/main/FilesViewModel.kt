package com.erfangholami.solidshare.presentation.main

import androidx.lifecycle.ViewModel
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class FilesViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {
    val activeWebIdFlow: StateFlow<String?> = authRepository.activeWebIdFlow
}
