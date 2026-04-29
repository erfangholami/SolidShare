package com.erfangholami.solidshare.di

import android.content.Context
import com.erfangholami.solidshare.data.local.auth.AuthLocalDataStore
import com.erfangholami.solidshare.data.local.settings.SettingsLocalDataStore
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.auth.AuthRepositoryImplementation
import com.erfangholami.solidshare.data.repo.file.FileRepository
import com.erfangholami.solidshare.data.repo.file.FileRepositoryImplementation
import com.erfangholami.solidshare.data.repo.settings.SettingsRepository
import com.erfangholami.solidshare.data.repo.settings.SettingsRepositoryImplementation
import com.pondersource.solidandroidapi.Authenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class RepositoryModule {

    @Singleton
    @Provides
    fun provideAuthRepository(
        authenticator: Authenticator,
        authLocalDataStore: AuthLocalDataStore,
    ): AuthRepository {
        return AuthRepositoryImplementation(authenticator, authLocalDataStore)
    }

    @Singleton
    @Provides
    fun provideSettingsRepository(
        settingsLocalDataStore: SettingsLocalDataStore
    ): SettingsRepository {
        return SettingsRepositoryImplementation(
            settingsLocalDataStore
        )
    }

    @Singleton
    @Provides
    fun provideFileRepository(
        @ApplicationContext context: Context,
        authenticator: Authenticator,
    ): FileRepository {
        return FileRepositoryImplementation(context, authenticator)
    }

}
