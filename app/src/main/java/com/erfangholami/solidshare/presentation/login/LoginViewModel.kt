package com.erfangholami.solidshare.presentation.login

import android.content.Intent
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : BaseViewModel() {

    companion object {
        const val REDIRECT_URI = "com.erfangholami.solidshare:/oauth2redirect"
    }

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

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState = _loginState.asStateFlow()

    fun setSelectedOfficialPod(podServer: PodServer) {
        _loginFilledDataState.value = LoginFilledData(
            type = LoginFilledMethod.OFFICIAL_POD,
            podServer = podServer
        )
    }

    fun setPersonalServerUrl(url: String) {
        if (url.isEmpty()) {
            _loginFilledDataState.value = LoginFilledData()
        } else {
            _loginFilledDataState.value = LoginFilledData(
                type = LoginFilledMethod.PERSONAL_SERVER,
                podServer = PodServer(name = url, url = url)
            )
        }
    }

    fun login() {
        val podServer = _loginFilledDataState.value.podServer ?: return
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                val (intent, _) = authRepository.createAuthenticationIntentWithOidcIssuer(
                    "Solid Share",
                    podServer.url,
                    REDIRECT_URI
                )
                if (intent == null) {
                    _loginState.value = LoginState.Error("Failed to create authentication intent")
                } else {
                    _loginState.value = LoginState.LaunchAuth(intent)
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message ?: "Failed to start authentication")
            }
        }
    }

    fun handleAuthResult(data: Intent?) {
        viewModelScope.launch {
            val response = data?.let { AuthorizationResponse.fromIntent(it) }
            val exception = data?.let { AuthorizationException.fromIntent(it) }
            try {
                val webId = authRepository.submitAuthorizationResponse(response, exception)
                if (webId != null) {
                    _loginState.value = LoginState.Success(webId)
                } else {
                    _loginState.value = LoginState.Error("Authentication failed")
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message ?: "Authentication failed")
            }
        }
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class LaunchAuth(val intent: Intent) : LoginState()
    data class Success(val webId: String) : LoginState()
    data class Error(val message: String) : LoginState()
}
