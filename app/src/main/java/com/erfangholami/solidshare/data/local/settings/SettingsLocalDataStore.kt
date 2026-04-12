package com.erfangholami.solidshare.data.local.settings

import com.erfangholami.solidshare.domain.model.Settings
import kotlinx.coroutines.flow.Flow

interface SettingsLocalDataStore {

    fun getSettingPreferences(): Flow<Settings>

    suspend fun completeOnboarding(completed: Boolean)
}