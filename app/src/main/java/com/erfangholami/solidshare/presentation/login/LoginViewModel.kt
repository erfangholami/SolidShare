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
import com.erfangholami.solidshare.telemetry.AuthAnalytics
import com.erfangholami.solidshare.domain.error.AppError
import com.erfangholami.solidshare.domain.error.AppOperation
import com.erfangholami.solidshare.domain.error.ErrorPresenter
import com.erfangholami.solidshare.domain.error.asException
import com.erfangholami.solidshare.domain.error.rethrowIfCancellation
import com.erfangholami.solidshare.util.StringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val stringProvider: StringProvider,
    private val errors: ErrorPresenter,
    private val authRepository: AuthRepository,
    private val authAnalytics: AuthAnalytics,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        const val APP_NAME = "Solid Share"
        const val REDIRECT_URI = "https://solidshare.app/oauth2redirect"
        const val CLIENT_ID = "https://solidshare.app/.well-known/clientid.jsonld"
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
    val refreshlessLoginWarning = mutableStateOf(false)

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
                e.rethrowIfCancellation()
                loginBrowserIntentErrorMessage.value = errors.message(e, AppOperation.SIGN_IN)
                loginLoading.value = false
            }
        }
    }

    fun loginWithWebId(webId: String) {
        launchLogin {
            authRepository.createAuthenticationIntent(
                webId = webId,
                appName = APP_NAME,
                redirectUri = REDIRECT_URI,
                clientId = CLIENT_ID
            )
        }
    }

    fun loginWithOidcIssuer(oidcIssuer: String) {
        launchLogin {
            authRepository.createAuthenticationIntent(
                oidcIssuer = oidcIssuer,
                appName = APP_NAME,
                redirectUri = REDIRECT_URI,
                clientId = CLIENT_ID
            )
        }
    }

    fun submitAuthorizationResponse(responseData: Intent?) {
        viewModelScope.launch {
            val webId = authRepository.submitAuthorizationResponse(responseData)

            loginLoading.value = false
            loginBrowserIntent.value = null
            if (!webId.isNullOrEmpty()) {
                loginBrowserIntentErrorMessage.value = null
                val issuerHost = authRepository.oidcIssuerHost(webId)
                val refreshable = authRepository.hasRefreshableSession(webId)
                authAnalytics.loginSucceeded(issuerHost, refreshable)
                if (refreshable) {
                    loginResult.value = true
                } else {
                    authAnalytics.loginNoRefreshToken(issuerHost)
                    refreshlessLoginWarning.value = true
                }
            } else {
                authAnalytics.loginFailed()
                loginResult.value = false
                loginBrowserIntentErrorMessage.value =
                    errors.present(AppError.SignInRequired, AppOperation.SIGN_IN).summary
            }
        }
    }

    fun acknowledgeRefreshlessLogin() {
        refreshlessLoginWarning.value = false
        loginResult.value = true
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
