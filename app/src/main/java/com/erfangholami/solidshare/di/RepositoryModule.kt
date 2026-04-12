package com.erfangholami.solidshare.di

import com.erfangholami.solidshare.data.local.auth.AuthLocalDataStore
import com.erfangholami.solidshare.data.local.settings.SettingsLocalDataStore
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.auth.AuthRepositoryImplementation
import com.erfangholami.solidshare.data.repo.settings.SettingsRepository
import com.erfangholami.solidshare.data.repo.settings.SettingsRepositoryImplementation
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class RepositoryModule {

    @Provides
    fun provideAuthRepository(
        authLocalDataStore: AuthLocalDataStore
    ): AuthRepository {
        return AuthRepositoryImplementation(authLocalDataStore)
    }

    @Provides
    fun provideSettingsRepository(
        settingsLocalDataStore: SettingsLocalDataStore
    ): SettingsRepository {
        return SettingsRepositoryImplementation(
            settingsLocalDataStore
        )
    }

}