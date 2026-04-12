package com.erfangholami.solidshare.presentation.login

import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.domain.model.LoginFilledData
import com.erfangholami.solidshare.domain.model.LoginFilledMethod
import com.erfangholami.solidshare.domain.model.PodServer
import com.erfangholami.solidshare.presentation.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    authRepository: AuthRepository,
): BaseViewModel() {

    val podServersState = authRepository.getListOfPodServers()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            runBlocking { authRepository.getListOfPodServers().first() }
        )

    val previouslyLoggedInUser = authRepository.getPreviouslyLoggedInUsers()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            runBlocking { authRepository.getPreviouslyLoggedInUsers().first() }
        )

    private val _loginFilledDataState = MutableStateFlow(LoginFilledData())
    val loginFilledDataState = _loginFilledDataState.asStateFlow()

    fun login() {
        //TODO("Not yet implemented")
    }

    fun setPersonalServerUrl(url: String) {
        if(url.isEmpty()) {
            _loginFilledDataState.value = LoginFilledData()
        } else {
            _loginFilledDataState.value = _loginFilledDataState.value.copy(
                type = LoginFilledMethod.PERSONAL_SERVER,
                podServer = PodServer(
                    name = url,
                    url = url,
                )
            )
        }
    }
}