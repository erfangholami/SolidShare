package com.erfangholami.solidshare.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.erfangholami.solidshare.data.local.settings.SettingsLocalDataStoreImplementation.PreferencesKeys.ONBOARDING_COMPLETED
import com.erfangholami.solidshare.data.local.settings.SettingsLocalDataStoreImplementation.PreferencesKeys.THEME_MODE
import com.erfangholami.solidshare.domain.model.Settings
import com.erfangholami.solidshare.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SettingsLocalDataStoreImplementation @Inject constructor(
    val settingsDataSource: DataStore<Preferences>
) : SettingsLocalDataStore {

    private object PreferencesKeys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS_PERMISSION_PROMPTED =
            booleanPreferencesKey("notifications_permission_prompted")
    }

    override fun getSettingPreferences(): Flow<Settings> {
        return settingsDataSource.data.map {
            Settings(
                hasCompletedOnboarding = it[ONBOARDING_COMPLETED] ?: false,
                themeMode = ThemeMode.fromName(it[THEME_MODE]),
            )
        }
    }

    override suspend fun completeOnboarding(completed: Boolean) {
        settingsDataSource.edit {
            it[ONBOARDING_COMPLETED] = completed
        }
    }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        settingsDataSource.edit {
            it[THEME_MODE] = themeMode.name
        }
    }

    override fun getNotificationsLastSeen(webId: String): Flow<String?> {
        val key = notificationsLastSeenKey(webId)
        return settingsDataSource.data.map { it[key] }
    }

    override suspend fun setNotificationsLastSeen(webId: String, isoInstant: String) {
        val key = notificationsLastSeenKey(webId)
        settingsDataSource.edit {
            it[key] = isoInstant
        }
    }

    override fun getNotificationsLastNotified(webId: String): Flow<String?> {
        val key = notificationsLastNotifiedKey(webId)
        return settingsDataSource.data.map { it[key] }
    }

    override suspend fun setNotificationsLastNotified(webId: String, isoInstant: String) {
        val key = notificationsLastNotifiedKey(webId)
        settingsDataSource.edit {
            it[key] = isoInstant
        }
    }

    override fun hasPromptedNotificationsPermission(): Flow<Boolean> {
        return settingsDataSource.data.map {
            it[PreferencesKeys.NOTIFICATIONS_PERMISSION_PROMPTED] ?: false
        }
    }

    override suspend fun setPromptedNotificationsPermission(prompted: Boolean) {
        settingsDataSource.edit {
            it[PreferencesKeys.NOTIFICATIONS_PERMISSION_PROMPTED] = prompted
        }
    }

    private fun notificationsLastSeenKey(webId: String) =
        stringPreferencesKey("notifications_last_seen_$webId")

    private fun notificationsLastNotifiedKey(webId: String) =
        stringPreferencesKey("notifications_last_notified_$webId")
}
