package com.erfangh.solidshare.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.erfangh.solidshare.data.local.settings.SettingsLocalDataStoreImplementation.PreferencesKeys.ONBOARDING_COMPLETED
import com.erfangh.solidshare.domain.model.Settings
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsLocalDataStoreImplementation @Inject constructor(
    val settingsDataSource: DataStore<Preferences>
) : SettingsLocalDataStore {

    private object PreferencesKeys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    override fun getSettingPreferences(): Flow<Settings> {
        return settingsDataSource.data.map {
            Settings(
                hasCompletedOnboarding = it[ONBOARDING_COMPLETED] ?: false,
            )
        }
    }

    override suspend fun completeOnboarding(completed: Boolean) {
        settingsDataSource.edit {
            it[ONBOARDING_COMPLETED] = completed
        }
    }
}