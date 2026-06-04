package com.erfangholami.solidshare.data.repo.settings

import com.erfangholami.solidshare.data.local.settings.SettingsLocalDataStore
import com.erfangholami.solidshare.domain.model.Settings
import com.erfangholami.solidshare.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SettingsRepositoryImplementation @Inject constructor(
    val settingsLocalDataStore: SettingsLocalDataStore
): SettingsRepository {

    override fun getSettingPreferences(): Flow<Settings> {
        return settingsLocalDataStore.getSettingPreferences()
    }

    override suspend fun completeOnboarding(completed: Boolean) {
        settingsLocalDataStore.completeOnboarding(completed)
    }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        settingsLocalDataStore.setThemeMode(themeMode)
    }

    override fun getNotificationsLastSeen(webId: String): Flow<String?> =
        settingsLocalDataStore.getNotificationsLastSeen(webId)

    override suspend fun setNotificationsLastSeen(webId: String, isoInstant: String) {
        settingsLocalDataStore.setNotificationsLastSeen(webId, isoInstant)
    }

    override fun getNotificationsLastNotified(webId: String): Flow<String?> =
        settingsLocalDataStore.getNotificationsLastNotified(webId)

    override suspend fun setNotificationsLastNotified(webId: String, isoInstant: String) {
        settingsLocalDataStore.setNotificationsLastNotified(webId, isoInstant)
    }
}
