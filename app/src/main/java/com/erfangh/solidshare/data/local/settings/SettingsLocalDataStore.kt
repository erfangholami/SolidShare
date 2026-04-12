package com.erfangh.solidshare.data.local.settings

import com.erfangh.solidshare.domain.model.Settings
import kotlinx.coroutines.flow.Flow

interface SettingsLocalDataStore {

    fun getSettingPreferences(): Flow<Settings>

    suspend fun completeOnboarding(completed: Boolean)
}