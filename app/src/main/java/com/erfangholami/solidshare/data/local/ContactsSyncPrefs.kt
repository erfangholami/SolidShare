package com.erfangholami.solidshare.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.contactsSyncDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "contacts_sync",
)

@Serializable
data class GoogleMappingEntry(
    val podUri: String,
    val fieldsHash: String,
    val photoHash: String? = null,
)

@Singleton
class ContactsSyncPrefs @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun enabledGoogleAccountsFlow(webId: String): Flow<Set<String>> =
        context.contactsSyncDataStore.data.map { prefs ->
            prefs[accountsKey(webId)] ?: emptySet()
        }

    suspend fun enabledGoogleAccounts(webId: String): Set<String> =
        enabledGoogleAccountsFlow(webId).first()

    suspend fun setGoogleAccountEnabled(webId: String, accountName: String, enabled: Boolean) {
        context.contactsSyncDataStore.edit { prefs ->
            val current = prefs[accountsKey(webId)] ?: emptySet()
            if (enabled) {
                prefs[accountsKey(webId)] = current + accountName
            } else {
                prefs[accountsKey(webId)] = current - accountName
                prefs.remove(mappingKey(webId, accountName))
            }
        }
    }

    suspend fun getMapping(webId: String, accountName: String): Map<Long, GoogleMappingEntry> {
        val raw = context.contactsSyncDataStore.data
            .map { it[mappingKey(webId, accountName)] }
            .first()
            ?: return emptyMap()
        return runCatching {
            json.decodeFromString<Map<Long, GoogleMappingEntry>>(raw)
        }.getOrDefault(emptyMap())
    }

    suspend fun setMapping(
        webId: String,
        accountName: String,
        mapping: Map<Long, GoogleMappingEntry>,
    ) {
        context.contactsSyncDataStore.edit { prefs ->
            prefs[mappingKey(webId, accountName)] = json.encodeToString(mapping)
        }
    }

    suspend fun isTracked(webId: String, podUri: String): Boolean =
        trackedGoogleAccount(webId, podUri) != null

    suspend fun trackedGoogleAccount(webId: String, podUri: String): String? {
        val accounts = enabledGoogleAccounts(webId)
        return accounts.firstOrNull { account ->
            getMapping(webId, account).values.any { it.podUri == podUri }
        }
    }

    private fun accountsKey(webId: String) =
        stringSetPreferencesKey("google_accounts_$webId")

    private fun mappingKey(webId: String, accountName: String) =
        stringPreferencesKey("google_mapping_$webId|$accountName")
}
