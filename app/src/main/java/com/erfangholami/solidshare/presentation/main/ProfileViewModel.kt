package com.erfangholami.solidshare.presentation.main

import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erfangholami.solidshare.data.repo.datamodule.DataModuleRegistry
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.file.FileRepository
import com.erfangholami.solidshare.data.repo.outbox.OutboxRepository
import com.erfangholami.solidshare.data.repo.settings.SettingsRepository
import com.erfangholami.solidshare.domain.model.PublicProfile
import com.erfangholami.solidshare.domain.model.ThemeMode
import com.erfangholami.solidshare.presentation.login.LoginViewModel
import com.erfangholami.solidshare.telemetry.AuthAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val fileRepository: FileRepository,
    private val outboxRepository: OutboxRepository,
    private val dataModules: DataModuleRegistry,
    private val authAnalytics: AuthAnalytics,
) : ViewModel() {

    val accounts: StateFlow<List<PublicProfile>> = authRepository.loggedInProfilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expiredAccounts: StateFlow<List<PublicProfile>> = authRepository.expiredProfilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeWebId: StateFlow<String> = authRepository.activeWebIdFlow
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val publicProfile: StateFlow<PublicProfile?> = authRepository.activeProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val themeMode: StateFlow<ThemeMode> = settingsRepository.getSettingPreferences()
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val logoutLoading = mutableStateOf(false)

    val navigateToLogin: StateFlow<Boolean> = flow {
        authRepository.getActiveWebId()
        emitAll(authRepository.isAuthorizedFlow.map { !it })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onKickedToLogin() {
        authAnalytics.kickedToLogin("no_authorized_accounts")
    }

    val reconnectBrowserIntent = mutableStateOf<Intent?>(null)
    val reconnectLoading = mutableStateOf(false)
    val reconnectError = mutableStateOf(false)
    val refreshlessReconnectWarning = mutableStateOf(false)

    fun reconnectAccount(webId: String) {
        viewModelScope.launch {
            reconnectLoading.value = true
            reconnectError.value = false
            val (intent, _) = runCatching {
                authRepository.createAuthenticationIntent(
                    webId = webId,
                    appName = LoginViewModel.APP_NAME,
                    redirectUri = LoginViewModel.REDIRECT_URI,
                    clientId = LoginViewModel.CLIENT_ID,
                )
            }.getOrDefault(Pair(null, null))
            if (intent != null) {
                reconnectBrowserIntent.value = intent
            } else {
                reconnectLoading.value = false
                reconnectError.value = true
            }
        }
    }

    fun handleReconnectResult(responseData: Intent?) {
        viewModelScope.launch {
            val webId = authRepository.submitAuthorizationResponse(responseData)
            reconnectLoading.value = false
            if (!webId.isNullOrEmpty()) {
                val issuerHost = authRepository.oidcIssuerHost(webId)
                val refreshable = authRepository.hasRefreshableSession(webId)
                authAnalytics.loginSucceeded(issuerHost, refreshable)
                if (!refreshable) {
                    authAnalytics.loginNoRefreshToken(issuerHost)
                    refreshlessReconnectWarning.value = true
                }
            } else {
                authAnalytics.loginFailed()
                reconnectError.value = true
            }
        }
    }

    fun onReconnectAborted() {
        reconnectLoading.value = false
    }

    fun acknowledgeRefreshlessReconnect() {
        refreshlessReconnectWarning.value = false
    }

    fun switchAccount(webId: String) {
        viewModelScope.launch {
            authRepository.setActiveWebId(webId)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutLoading.value = true
            val webId = activeWebId.value
            authRepository.removeProfile(webId)
            fileRepository.clearCacheForWebId(webId)
            outboxRepository.clearForWebId(webId)
            dataModules.clearCache(webId)
            logoutLoading.value = false
        }
    }

    fun logoutAll() {
        viewModelScope.launch {
            logoutLoading.value = true
            val webIds = accounts.value.map { it.webId }
            authRepository.removeAllProfiles()
            webIds.forEach {
                fileRepository.clearCacheForWebId(it)
                outboxRepository.clearForWebId(it)
                dataModules.clearCache(it)
            }
            logoutLoading.value = false
        }
    }
}
