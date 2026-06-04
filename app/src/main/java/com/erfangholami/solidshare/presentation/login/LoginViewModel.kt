package com.erfangholami.solidshare.presentation.login

import android.content.Intent
import androidx.annotation.IntegerRes
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.presentation.navigation.AuthNavItem
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val stringProvider: StringProvider,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        const val APP_NAME = "Solid Share"
        const val REDIRECT_URI = "com.erfangholami.solidshare:/oauth2redirect"
    }

    val isAddingAccount: Boolean = savedStateHandle.toRoute<AuthNavItem.Login>().isAddingAccount

    val podServers = authRepository.getListOfPodServers().map { server ->
        UiPodServer(
            name = server.name,
            icon = R.drawable.ic_solid,
            iconTint = tintForProvider(server.url),
            url = server.url,
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
                loginBrowserIntentErrorMessage.value =
                    e.message ?: stringProvider.getString(R.string.error_login_failed)
                loginLoading.value = false
            }
        }
    }

    fun loginWithWebId(webId: String) {
        launchLogin {
            authRepository.createAuthenticationIntent(
                webId = webId,
                appName = APP_NAME,
                redirectUri = REDIRECT_URI
            )
        }
    }

    fun loginWithOidcIssuer(oidcIssuer: String) {
        launchLogin {
            authRepository.createAuthenticationIntent(
                oidcIssuer = oidcIssuer,
                appName = APP_NAME,
                redirectUri = REDIRECT_URI
            )
        }
    }

    fun submitAuthorizationResponse(responseData: Intent?) {
        viewModelScope.launch {
            authRepository.submitAuthorizationResponse(responseData)

            loginLoading.value = false
            loginBrowserIntent.value = null
            if (isLoggedIn()) {
                loginBrowserIntentErrorMessage.value = null
                loginResult.value = true
            } else {
                loginResult.value = false
                loginBrowserIntentErrorMessage.value = stringProvider.getString(R.string.error_login_problem)
            }
        }
    }

    fun isLoggedIn(): Boolean {
        return authRepository.isUserAuthorized()
    }

    private fun tintForProvider(url: String): Color {
        val host = runCatching { java.net.URI(url).host.orEmpty() }
            .getOrDefault("")
            .lowercase()
        return when {
            "inrupt" in host -> Color(0xFF18A092)
            "solidcommunity" in host -> Color(0xFF7C4DFF)
            "datapod" in host || "igrant" in host -> Color(0xFFEF7C29)
            else -> Color(0xFF4D65FF)
        }
    }
}

data class UiPodServer(
    val name: String,
    @field:IntegerRes val icon: Int,
    val iconTint: Color,
    val url: String,
)
