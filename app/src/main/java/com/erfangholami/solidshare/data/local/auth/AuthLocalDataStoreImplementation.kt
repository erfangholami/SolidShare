package com.erfangholami.solidshare.data.local.auth

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AuthLocalDataStoreImplementation @Inject constructor(
    val dataStore: DataStore<List<String>>,
): AuthLocalDataStore {

    override fun getListOfLoggedOutWebIDs(): Flow<List<String>> {
        return dataStore.data
    }

    override suspend fun addLoggedOutWebId(webId: String) {
        dataStore.updateData {
            it.plus(webId)
        }
    }

    override suspend fun removeLoggedOutWebId(webId: String) {
        dataStore.updateData {
            it.minus(webId)
        }
    }
}