package com.erfangholami.solidshare.data.repo.settings

import com.erfangholami.solidshare.domain.model.Settings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettingPreferences(): Flow<Settings>

    suspend fun completeOnboarding(completed: Boolean)
}