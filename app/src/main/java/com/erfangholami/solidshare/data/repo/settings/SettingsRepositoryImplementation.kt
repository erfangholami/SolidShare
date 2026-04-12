package com.erfangholami.solidshare.data.repo.settings

import com.erfangholami.solidshare.data.local.settings.SettingsLocalDataStore
import com.erfangholami.solidshare.domain.model.Settings
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow

class SettingsRepositoryImplementation @Inject constructor(
    val settingsLocalDataStore: SettingsLocalDataStore
): SettingsRepository {

    override fun getSettingPreferences(): Flow<Settings> {
        return settingsLocalDataStore.getSettingPreferences()
    }

    override suspend fun completeOnboarding(completed: Boolean) {
        settingsLocalDataStore.completeOnboarding(completed)
    }
}