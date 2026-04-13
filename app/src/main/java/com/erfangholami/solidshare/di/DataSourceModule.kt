package com.erfangholami.solidshare.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.erfangholami.solidshare.data.local.auth.AuthLocalDataStore
import com.erfangholami.solidshare.data.local.auth.AuthLocalDataStoreImplementation
import com.erfangholami.solidshare.data.local.settings.SettingsLocalDataStore
import com.erfangholami.solidshare.data.local.settings.SettingsLocalDataStoreImplementation
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
    fun provideAuthLocalDataSource(
        dataStore: DataStore<List<String>>
    ): AuthLocalDataStore {
        return AuthLocalDataStoreImplementation(
            dataStore
        )
    }

}
