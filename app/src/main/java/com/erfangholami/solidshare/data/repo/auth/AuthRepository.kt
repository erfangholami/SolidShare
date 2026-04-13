package com.erfangholami.solidshare.data.repo.auth

import com.erfangholami.solidshare.domain.model.PodServer
import com.pondersource.solidandroidapi.Authenticator
import kotlinx.coroutines.flow.Flow

interface AuthRepository: Authenticator {

    fun getListOfPodServers(): Flow<List<PodServer>>

    fun getListOfLoggedOutWebIDs(): Flow<List<String>>

}