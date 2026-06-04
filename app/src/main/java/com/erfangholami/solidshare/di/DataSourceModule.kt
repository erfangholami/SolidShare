package com.erfangholami.solidshare.di

import com.erfangholami.solidshare.data.local.auth.AuthLocalDataStore
import com.erfangholami.solidshare.data.local.auth.AuthLocalDataStoreImplementation
import com.erfangholami.solidshare.data.local.settings.SettingsLocalDataStore
import com.erfangholami.solidshare.data.local.settings.SettingsLocalDataStoreImplementation
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface DataSourceModule {

    @Binds
    fun bindSettingsLocalDataStore(
        implementation: SettingsLocalDataStoreImplementation,
    ): SettingsLocalDataStore

    @Binds
    fun bindAuthLocalDataStore(
        implementation: AuthLocalDataStoreImplementation,
    ): AuthLocalDataStore
}
