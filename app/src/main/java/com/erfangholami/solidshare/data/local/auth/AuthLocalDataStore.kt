package com.erfangholami.solidshare.data.local.auth

import kotlinx.coroutines.flow.Flow

interface AuthLocalDataStore {

    fun getListOfLoggedOutWebIDs(): Flow<List<String>>

    suspend fun addLoggedOutWebId(webId: String)

    suspend fun removeLoggedOutWebId(webId: String)
}