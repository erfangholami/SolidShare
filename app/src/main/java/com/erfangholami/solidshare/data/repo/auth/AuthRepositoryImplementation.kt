package com.erfangholami.solidshare.data.repo.auth

import android.content.Intent
import com.erfangholami.solidshare.data.local.auth.AuthLocalDataStore
import com.erfangholami.solidshare.domain.model.PodServer
import com.pondersource.shared.data.Profile
import com.pondersource.solidandroidapi.Authenticator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.TokenResponse

class AuthRepositoryImplementation(
    private val authenticator: Authenticator,
    private val localAuthDataStore: AuthLocalDataStore,
) : AuthRepository {

    override fun getListOfPodServers(): List<PodServer> {
        return listOf(
            PodServer("SolidCommunity.net", "https://solidcommunity.net"),
            PodServer("Inrupt PodSpaces", "https://login.inrupt.com"),
            PodServer("Data Pod", "https://datapod.igrant.io/"),
        )
    }

    override fun getListOfLoggedOutWebIDs(): Flow<List<String>> {
        return localAuthDataStore.getListOfLoggedOutWebIDs()
    }

    override val activeProfileFlow: StateFlow<Profile?>
        get() = authenticator.activeProfileFlow

    override val loggedInProfilesFlow: StateFlow<List<Profile>>
        get() = authenticator.loggedInProfilesFlow

    override val isAuthorizedFlow: StateFlow<Boolean>
        get() = authenticator.isAuthorizedFlow

    override val activeWebIdFlow: StateFlow<String?>
        get() = authenticator.activeWebIdFlow

    override suspend fun createAuthenticationIntent(
        webId: String?,
        oidcIssuer: String?,
        appName: String,
        redirectUri: String
    ): Pair<Intent?, String?> {
        return authenticator.createAuthenticationIntent(webId, oidcIssuer, appName, redirectUri)
    }

    override suspend fun submitAuthorizationResponse(
        authResponse: AuthorizationResponse?,
        authException: AuthorizationException?
    ): String? {

        return try {
            val webId = authenticator.submitAuthorizationResponse(authResponse, authException)
            if(!webId.isNullOrEmpty()) {
                removeLoggedOutWebId(webId)
            }
            webId
        } catch (_: Exception){
            null
        }
    }

    override suspend fun getTerminationSessionIntent(
        webId: String,
        logoutRedirectUrl: String,
    ): Pair<Intent?, String?> {
        return authenticator.getTerminationSessionIntent(webId, logoutRedirectUrl)
    }

    override suspend fun getLastTokenResponse(
        webId: String,
        forceRefresh: Boolean
    ): TokenResponse? {
        return authenticator.getLastTokenResponse(webId, forceRefresh)
    }

    override suspend fun getAuthHeaders(
        webId: String,
        httpMethod: String,
        uri: String
    ): Map<String, String> {
        return authenticator.getAuthHeaders(webId, httpMethod, uri)
    }

    override fun updateDPoPNonce(webId: String, nonce: String) {
        authenticator.updateDPoPNonce(webId, nonce)
    }

    override fun isUserAuthorized(): Boolean {
        return authenticator.isUserAuthorized()
    }

    override fun getAllLoggedInProfiles(): List<Profile> {
        return authenticator.getAllLoggedInProfiles()
    }

    override fun getProfile(webId: String): Profile {
        return authenticator.getProfile(webId)
    }

    override fun getActiveProfile(): Profile {
        return authenticator.getActiveProfile()
    }

    override suspend fun getActiveWebId(): String? {
        return authenticator.getActiveWebId()
    }

    override suspend fun setActiveWebId(webId: String) {
        return authenticator.setActiveWebId(webId)
    }

    override suspend fun removeProfile(webId: String) {
        addLoggedOutWebId(webId)
        authenticator.removeProfile(webId)
    }

    override suspend fun removeAllProfiles() {
        getAllLoggedInProfiles().forEach {
            addLoggedOutWebId(it.userInfo!!.webId)
        }
        authenticator.removeAllProfiles()
    }

    private suspend fun addLoggedOutWebId(webId: String) {
        localAuthDataStore.addLoggedOutWebId(webId)
    }

    private suspend fun removeLoggedOutWebId(webId: String) {
        localAuthDataStore.removeLoggedOutWebId(webId)
    }
}
