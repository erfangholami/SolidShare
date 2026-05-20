package com.erfangholami.solidshare.data.repo.auth

import android.content.Intent
import com.erfangholami.solidshare.domain.model.PodServer
import com.pondersource.shared.domain.profile.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

interface AuthRepository {

    val activeWebIdFlow: StateFlow<String?>
    val loggedInProfilesFlow: StateFlow<List<Profile>>
    val isAuthorizedFlow: StateFlow<Boolean>

    fun getListOfPodServers(): List<PodServer>
    fun getListOfLoggedOutWebIDs(): Flow<List<String>>

    fun isUserAuthorized(): Boolean
    fun getProfile(webId: String): Profile

    suspend fun createAuthenticationIntent(
        webId: String? = null,
        oidcIssuer: String? = null,
        appName: String,
        redirectUri: String,
    ): Pair<Intent?, String?>

    suspend fun submitAuthorizationResponse(
        authResponse: AuthorizationResponse?,
        authException: AuthorizationException?,
    ): String?

    suspend fun setActiveWebId(webId: String)
    suspend fun removeProfile(webId: String)
    suspend fun removeAllProfiles()
}
