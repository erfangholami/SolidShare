package com.erfangholami.solidshare.data.repo.auth

import com.erfangholami.solidshare.data.local.auth.AuthLocalDataStore
import com.erfangholami.solidshare.domain.model.PodServer
import com.erfangholami.solidshare.domain.model.PreviouslyLoggedInUser
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AuthRepositoryImplementation @Inject constructor(
    authLocalDataStore: AuthLocalDataStore
) : AuthRepository {

    override fun getListOfPodServers(): Flow<List<PodServer>> {
        return flow {
            emit(listOf(
                PodServer("Data Pod", "https://datapod.com"),
                PodServer("Solid Community", "https://solidcommunity.net"),
                PodServer("Inrupt Pod Spaces", "https://inrupt.com"),
            ))
        }
    }

    override fun getPreviouslyLoggedInUsers(): Flow<List<PreviouslyLoggedInUser>> {
        return flow {
            emit(listOf())
        }
    }

    override fun getLoggedInUsers(): Flow<List<PreviouslyLoggedInUser>> {
        return flow {
            emit(listOf())
        }
    }

}