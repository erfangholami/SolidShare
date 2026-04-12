package com.erfangholami.solidshare.data.repo.auth

import com.erfangholami.solidshare.domain.model.PodServer
import com.erfangholami.solidshare.domain.model.PreviouslyLoggedInUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    fun getListOfPodServers(): Flow<List<PodServer>>

    fun getPreviouslyLoggedInUsers(): Flow<List<PreviouslyLoggedInUser>>

    fun getLoggedInUsers(): Flow<List<PreviouslyLoggedInUser>>

}