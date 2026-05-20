package com.erfangholami.solidshare.presentation.login

import android.content.Intent
import androidx.annotation.IntegerRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.presentation.navigation.AuthNavItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface LoginEvent {
    data class LaunchAuthorizationIntent(val intent: Intent) : LoginEvent
    data object NavigateAfterLogin : LoginEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        const val APP_NAME = "Solid Share"
        const val REDIRECT_URI = "com.erfangholami.solidshare:/oauth2redirect"
    }

    val isAddingAccount: Boolean = savedStateHandle.toRoute<AuthNavItem.Login>().isAddingAccount

    val podServers: List<UiPodServer> = authRepository.getListOfPodServers().map {
        UiPodServer(
            name = it.name,
            icon = R.drawable.ic_solid,
            url = it.url,
        )
    }

    val previouslyLoggedOutWebIds: StateFlow<List<String>> = authRepository.getListOfLoggedOutWebIDs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    fun loginWithWebId(webId: String) = launchLogin {
        authRepository.createAuthenticationIntent(
            webId = webId,
            appName = APP_NAME,
            redirectUri = REDIRECT_URI,
        )
    }

    fun loginWithOidcIssuer(oidcIssuer: String) = launchLogin {
        authRepository.createAuthenticationIntent(
            oidcIssuer = oidcIssuer,
            appName = APP_NAME,
            redirectUri = REDIRECT_URI,
        )
    }

    fun submitAuthorizationResponse(
        authorizationResponse: AuthorizationResponse?,
        authorizationException: AuthorizationException?,
    ) {
        viewModelScope.launch {
            authRepository.submitAuthorizationResponse(authorizationResponse, authorizationException)
            if (authRepository.isUserAuthorized()) {
                _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                _events.send(LoginEvent.NavigateAfterLogin)
            } else {
                val message = authorizationException?.errorDescription
                    ?: "A problem during login occurred!"
                _uiState.update { it.copy(isLoading = false, errorMessage = message) }
            }
        }
    }

    fun cancelLoading() {
        _uiState.update { it.copy(isLoading = false) }
    }

    private fun launchLogin(block: suspend () -> Pair<Intent?, String?>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val (intent, error) = block()
                if (intent != null) {
                    _events.send(LoginEvent.LaunchAuthorizationIntent(intent))
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = error) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Login failed")
                }
            }
        }
    }
}

data class UiPodServer(
    val name: String,
    @field:IntegerRes val icon: Int,
    val url: String,
)
