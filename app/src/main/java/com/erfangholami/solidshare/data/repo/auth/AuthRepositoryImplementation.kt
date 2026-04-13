package com.erfangholami.solidshare.data.repo.auth

import android.content.Intent
import com.erfangholami.solidshare.domain.model.PodServer
import com.erfangholami.solidshare.domain.model.PreviouslyLoggedInUser
import com.pondersource.shared.data.Profile
import com.pondersource.solidandroidapi.Authenticator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.TokenResponse

class AuthRepositoryImplementation(
    private val authenticator: Authenticator,
) : AuthRepository {

    override fun getListOfPodServers(): Flow<List<PodServer>> {
        return flow {
            emit(listOf(
                PodServer("SolidCommunity", "https://solidcommunity.net"),
                PodServer("Inrupt PodSpaces", "https://login.inrupt.com"),
                PodServer("Data Pod", "https://datapod.com"),
            ))
        }
    }

    override fun getPreviouslyLoggedInUsers(): Flow<List<PreviouslyLoggedInUser>> {
        return getLoggedInUsers()
    }

    override fun getLoggedInUsers(): Flow<List<PreviouslyLoggedInUser>> {
        return authenticator.activeProfileFlow.map {
            if (authenticator.isUserAuthorized()) {
                authenticator.getAllLoggedInProfiles().mapNotNull { profile ->
                    val webId = profile.userInfo?.webId ?: return@mapNotNull null
                    PreviouslyLoggedInUser(webId, PodServer("", ""))
                }
            } else {
                emptyList()
            }
        }
    }

    override val activeProfileFlow: StateFlow<Profile>
        get() = authenticator.activeProfileFlow

    override suspend fun createAuthenticationIntentWithWebId(
        clientName: String,
        webId: String,
        redirectUri: String
    ): Pair<Intent?, String?> {
        return authenticator.createAuthenticationIntentWithWebId(clientName, webId, redirectUri)
    }

    override suspend fun createAuthenticationIntentWithOidcIssuer(
        clientName: String,
        oidcIssuer: String,
        redirectUri: String
    ): Pair<Intent?, String?> {
        return authenticator.createAuthenticationIntentWithOidcIssuer(clientName, oidcIssuer, redirectUri)
    }

    override suspend fun submitAuthorizationResponse(
        authResponse: AuthorizationResponse?,
        authException: AuthorizationException?
    ): String? {
        return authenticator.submitAuthorizationResponse(authResponse, authException)
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

    override fun getProfile(): Profile {
        return authenticator.getProfile()
    }

    override suspend fun getActiveWebId(): String? {
        return authenticator.getActiveWebId()
    }

    override suspend fun setActiveWebId(webId: String) {
        return authenticator.setActiveWebId(webId)
    }

    override suspend fun resetProfile() {
        authenticator.resetProfile()
    }

    override suspend fun resetProfile(webId: String) {
        authenticator.resetProfile(webId)
    }

    override suspend fun getTerminationSessionIntent(
        webId: String,
        logoutRedirectUrl: String
    ): Pair<Intent?, String?> {
        return authenticator.getTerminationSessionIntent(webId, logoutRedirectUrl)
    }
}
