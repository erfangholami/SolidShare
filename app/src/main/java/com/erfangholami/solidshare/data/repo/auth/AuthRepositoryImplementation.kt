package com.erfangholami.solidshare.data.repo.auth

import android.content.Intent
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.solidshare.data.local.auth.AuthLocalDataStore
import com.erfangholami.solidshare.data.repo.profile.PublicProfileRepository
import com.erfangholami.solidshare.domain.model.PodServer
import com.erfangholami.solidshare.domain.model.PublicProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AuthRepositoryImplementation @Inject constructor(
    private val authenticator: Authenticator,
    private val localAuthDataStore: AuthLocalDataStore,
    private val publicProfileRepository: PublicProfileRepository,
) : AuthRepository {

    override val activeWebIdFlow: StateFlow<String?> = authenticator.activeWebIdFlow
    override val activeProfileFlow: Flow<PublicProfile?> =
        authenticator.activeProfileFlow.map { it?.let(publicProfileRepository::fromProfile) }
    override val loggedInProfilesFlow: Flow<List<PublicProfile>> =
        authenticator.loggedInProfilesFlow.map { profiles ->
            profiles.mapNotNull(publicProfileRepository::fromProfile)
        }
    override val isAuthorizedFlow: StateFlow<Boolean> = authenticator.isAuthorizedFlow

    override fun getListOfPodServers(): List<PodServer> = listOf(
        PodServer("SolidCommunity.net", "https://solidcommunity.net"),
        PodServer("Inrupt PodSpaces", "https://login.inrupt.com"),
        PodServer("Data Pod", "https://datapod.igrant.io/"),
        PodServer("SolidWeb.org", "https://solidweb.org"),
        PodServer("SolidWeb.me", "https://solidweb.me"),
        PodServer("Redpencil", "https://solid.redpencil.io"),
    )

    override fun getListOfLoggedOutWebIDs(): Flow<List<String>> =
        localAuthDataStore.getListOfLoggedOutWebIDs()

    override fun isUserAuthorized(): Boolean = authenticator.isUserAuthorized()

    override fun getStorages(webId: String): List<String> =
        authenticator.getProfile(webId).webId?.getStorages()?.map { it.toString() } ?: emptyList()

    override suspend fun getActiveWebId(): String? = authenticator.getActiveWebId()

    override suspend fun createAuthenticationIntent(
        webId: String?,
        oidcIssuer: String?,
        appName: String,
        redirectUri: String,
    ): Pair<Intent?, String?> =
        authenticator.createAuthenticationIntent(webId, oidcIssuer, appName, redirectUri)

    override suspend fun submitAuthorizationResponse(responseData: Intent?): String? = try {
        val webId = authenticator.submitAuthorizationResponse(responseData)
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

    override suspend fun reloadProfile(webId: String): PublicProfile =
        publicProfileRepository.fromProfile(authenticator.reloadProfile(webId))
            ?: error("Couldn't load profile for $webId")
}
