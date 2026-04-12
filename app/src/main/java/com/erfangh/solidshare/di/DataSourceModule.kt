package com.erfangh.solidshare.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.erfangh.solidshare.data.local.auth.AuthLocalDataStore
import com.erfangh.solidshare.data.local.auth.AuthLocalDataStoreImplementation
import com.erfangh.solidshare.data.local.settings.SettingsLocalDataStore
import com.erfangh.solidshare.data.local.settings.SettingsLocalDataStoreImplementation
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class DataSourceModule {

    @Provides
    fun provideSettingsLocalDataSource(
        settingsDataSource: DataStore<Preferences>
    ): SettingsLocalDataStore {
        return SettingsLocalDataStoreImplementation(
            settingsDataSource
        )
    }

    @Provides
    fun provideAuthLocalDataStore(): AuthLocalDataStore {
        return AuthLocalDataStoreImplementation(
        )
    }
}