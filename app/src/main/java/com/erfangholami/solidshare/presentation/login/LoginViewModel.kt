package com.erfangholami.solidshare.presentation.login

import android.content.Intent
import androidx.annotation.IntegerRes
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.presentation.base.BaseViewModel
import com.erfangholami.solidshare.presentation.navigation.AuthNavItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel() {

    companion object {
        const val APP_NAME = "Solid Share"
        const val REDIRECT_URI = "com.erfangholami.solidshare:/oauth2redirect"
    }

    val isAddingAccount: Boolean = savedStateHandle.toRoute<AuthNavItem.Login>().isAddingAccount

    val podServers = authRepository.getListOfPodServers().map {
        UiPodServer(
            name = it.name,
            icon = R.drawable.ic_solid,
            url = it.url
        )
    }

    val previouslyLoggedOutWebIds = authRepository.getListOfLoggedOutWebIDs()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val loginBrowserIntent = mutableStateOf<Intent?>(null)
    val loginBrowserIntentErrorMessage = mutableStateOf<String?>(null)
    val loginLoading = mutableStateOf(false)
    val loginResult = mutableStateOf(false)

    private fun launchLogin(block: suspend () -> Pair<Intent?, String?>) {
        viewModelScope.launch {
            loginLoading.value = true
            try {
                val intentRes = block()
                loginBrowserIntentErrorMessage.value = intentRes.second
                loginBrowserIntent.value = intentRes.first
                if (intentRes.first == null) {
                    loginLoading.value = false
                }
            } catch (e: Exception) {
                loginBrowserIntentErrorMessage.value = e.message ?: "Login failed"
                loginLoading.value = false
            }
        }
    }

    fun loginWithWebId(webId: String) {
        launchLogin {
            authRepository.createAuthenticationIntent(webId = webId, appName = APP_NAME, redirectUri = REDIRECT_URI)
        }
    }

    fun loginWithOidcIssuer(oidcIssuer: String) {
        launchLogin {
            authRepository.createAuthenticationIntent(oidcIssuer = oidcIssuer, appName = APP_NAME, redirectUri = REDIRECT_URI)
        }
    }

    fun submitAuthorizationResponse(
        authorizationResponse: AuthorizationResponse?,
        authorizationException: AuthorizationException?
    ) {
        viewModelScope.launch {
            authRepository.submitAuthorizationResponse(
                authorizationResponse,
                authorizationException
            )

            loginLoading.value = false
            loginBrowserIntent.value = null
            if (isLoggedIn()) {
                loginBrowserIntentErrorMessage.value = null
                loginResult.value = true
            } else {
                loginResult.value = false
                if (authorizationException != null) {
                    loginBrowserIntentErrorMessage.value = authorizationException.errorDescription
                } else {
                    loginBrowserIntentErrorMessage.value = "A problem during login occurred!"
                }
            }
        }
    }

    fun isLoggedIn(): Boolean {
        return authRepository.isUserAuthorized()
    }
}

data class UiPodServer(
    val name: String,
    @field:IntegerRes val icon: Int,
    val url: String,
)