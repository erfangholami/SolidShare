package com.erfangholami.solidshare.presentation.main

import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.presentation.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class FilesViewModel @Inject constructor(
    authRepository: AuthRepository,
) : BaseViewModel() {
    val activeWebIdFlow: StateFlow<String?> = authRepository.activeWebIdFlow
}