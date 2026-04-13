package com.erfangholami.solidshare.di

import com.erfangholami.solidshare.data.local.settings.SettingsLocalDataStore
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.auth.AuthRepositoryImplementation
import com.erfangholami.solidshare.data.repo.settings.SettingsRepository
import com.erfangholami.solidshare.data.repo.settings.SettingsRepositoryImplementation
import com.pondersource.solidandroidapi.Authenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class RepositoryModule {

    @Provides
    fun provideAuthRepository(
        authenticator: Authenticator
    ): AuthRepository {
        return AuthRepositoryImplementation(authenticator)
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
