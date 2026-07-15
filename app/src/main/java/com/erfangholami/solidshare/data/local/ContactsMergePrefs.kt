package com.erfangholami.solidshare.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.contactsMergeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "contacts_merge",
)

@Singleton
class ContactsMergePrefs @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    suspend fun dismissed(webId: String): Set<String> =
        context.contactsMergeDataStore.data
            .map { it[dismissedKey(webId)] ?: emptySet() }
            .first()

    suspend fun dismiss(webId: String, signature: String) {
        context.contactsMergeDataStore.edit { prefs ->
            prefs[dismissedKey(webId)] = (prefs[dismissedKey(webId)] ?: emptySet()) + signature
        }
    }

    suspend fun prune(webId: String, keep: Set<String>) {
        context.contactsMergeDataStore.edit { prefs ->
            val current = prefs[dismissedKey(webId)] ?: return@edit
            val pruned = current.intersect(keep)
            if (pruned.size != current.size) prefs[dismissedKey(webId)] = pruned
        }
    }

    private fun dismissedKey(webId: String) =
        stringSetPreferencesKey("dismissed_$webId")
}
