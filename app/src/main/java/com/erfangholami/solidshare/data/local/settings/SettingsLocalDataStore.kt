package com.erfangholami.solidshare.data.local.settings

import com.erfangholami.solidshare.domain.model.Settings
import com.erfangholami.solidshare.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsLocalDataStore {

    fun getSettingPreferences(): Flow<Settings>

    suspend fun completeOnboarding(completed: Boolean)

    suspend fun setThemeMode(themeMode: ThemeMode)

    fun getNotificationsLastSeen(webId: String): Flow<String?>

    suspend fun setNotificationsLastSeen(webId: String, isoInstant: String)

    fun getNotificationsLastNotified(webId: String): Flow<String?>

    suspend fun setNotificationsLastNotified(webId: String, isoInstant: String)
}
