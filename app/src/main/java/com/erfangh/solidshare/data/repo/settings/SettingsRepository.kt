package com.erfangh.solidshare.data.repo.settings

import com.erfangh.solidshare.domain.model.Settings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettingPreferences(): Flow<Settings>

    suspend fun completeOnboarding(completed: Boolean)
}