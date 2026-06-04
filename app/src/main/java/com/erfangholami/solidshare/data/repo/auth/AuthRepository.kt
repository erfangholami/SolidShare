package com.erfangholami.solidshare.data.repo.auth

import android.content.Intent
import com.erfangholami.solidshare.domain.model.PodServer
import com.erfangholami.solidshare.domain.model.PublicProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {

    val activeWebIdFlow: StateFlow<String?>
    val activeProfileFlow: Flow<PublicProfile?>
    val loggedInProfilesFlow: Flow<List<PublicProfile>>
    val isAuthorizedFlow: StateFlow<Boolean>

    fun getListOfPodServers(): List<PodServer>
    fun getListOfLoggedOutWebIDs(): Flow<List<String>>

    fun isUserAuthorized(): Boolean
    fun getStorages(webId: String): List<String>

    suspend fun getActiveWebId(): String?

    suspend fun createAuthenticationIntent(
        webId: String? = null,
        oidcIssuer: String? = null,
        appName: String,
        redirectUri: String,
    ): Pair<Intent?, String?>

    suspend fun submitAuthorizationResponse(responseData: Intent?): String?

    suspend fun setActiveWebId(webId: String)
    suspend fun removeProfile(webId: String)
    suspend fun removeAllProfiles()

    suspend fun reloadProfile(webId: String): PublicProfile
}
