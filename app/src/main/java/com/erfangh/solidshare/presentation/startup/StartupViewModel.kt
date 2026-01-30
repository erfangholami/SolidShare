package com.erfangh.solidshare.presentation.startup

import androidx.lifecycle.ViewModel
import com.erfangh.solidshare.data.repo.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

}