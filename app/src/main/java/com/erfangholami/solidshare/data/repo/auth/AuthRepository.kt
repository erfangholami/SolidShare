package com.erfangholami.solidshare.data.repo.auth

import com.erfangholami.solidshare.domain.model.PodServer
import com.pondersource.solidandroidapi.auth.Authenticator
import kotlinx.coroutines.flow.Flow

interface AuthRepository: Authenticator {

    fun getListOfPodServers(): List<PodServer>

    fun getListOfLoggedOutWebIDs(): Flow<List<String>>

}