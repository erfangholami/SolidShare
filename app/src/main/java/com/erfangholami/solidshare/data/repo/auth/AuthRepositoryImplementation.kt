package com.erfangholami.solidshare.data.repo.auth

import android.content.Intent
import com.erfangholami.solidshare.data.local.auth.AuthLocalDataStore
import com.erfangholami.solidshare.domain.model.PodServer
import com.pondersource.shared.domain.profile.Profile
import com.pondersource.solidandroidapi.auth.Authenticator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

class AuthRepositoryImplementation(
    private val authenticator: Authenticator,
    private val localAuthDataStore: AuthLocalDataStore,
) : AuthRepository {

    override val activeWebIdFlow: StateFlow<String?> = authenticator.activeWebIdFlow
    override val loggedInProfilesFlow: StateFlow<List<Profile>> = authenticator.loggedInProfilesFlow
    override val isAuthorizedFlow: StateFlow<Boolean> = authenticator.isAuthorizedFlow

    override fun getListOfPodServers(): List<PodServer> = listOf(
        PodServer("SolidCommunity.net", "https://solidcommunity.net"),
        PodServer("Inrupt PodSpaces", "https://login.inrupt.com"),
        PodServer("Data Pod", "https://datapod.igrant.io/"),
    )

    override fun getListOfLoggedOutWebIDs(): Flow<List<String>> =
        localAuthDataStore.getListOfLoggedOutWebIDs()

    override fun isUserAuthorized(): Boolean = authenticator.isUserAuthorized()

    override fun getProfile(webId: String): Profile = authenticator.getProfile(webId)

    override suspend fun createAuthenticationIntent(
        webId: String?,
        oidcIssuer: String?,
        appName: String,
        redirectUri: String,
    ): Pair<Intent?, String?> =
        authenticator.createAuthenticationIntent(webId, oidcIssuer, appName, redirectUri)

    override suspend fun submitAuthorizationResponse(
        authResponse: AuthorizationResponse?,
        authException: AuthorizationException?,
    ): String? = try {
        val webId = authenticator.submitAuthorizationResponse(authResponse, authException)
        if (!webId.isNullOrEmpty()) {
            localAuthDataStore.removeLoggedOutWebId(webId)
        }
        webId
    } catch (_: Exception) {
        null
    }

    override suspend fun setActiveWebId(webId: String) {
        authenticator.setActiveWebId(webId)
    }

    override suspend fun removeProfile(webId: String) {
        localAuthDataStore.addLoggedOutWebId(webId)
        authenticator.removeProfile(webId)
    }

    override suspend fun removeAllProfiles() {
        authenticator.getAllLoggedInProfiles().forEach { profile ->
            profile.userInfo?.webId?.let { localAuthDataStore.addLoggedOutWebId(it) }
        }
        authenticator.removeAllProfiles()
    }
}
