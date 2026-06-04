package com.erfangholami.solidshare.di

import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.auth.AuthRepositoryImplementation
import com.erfangholami.solidshare.data.repo.file.FileRepository
import com.erfangholami.solidshare.data.repo.file.FileRepositoryImplementation
import com.erfangholami.solidshare.data.repo.notifications.NotificationsRepository
import com.erfangholami.solidshare.data.repo.notifications.NotificationsRepositoryImplementation
import com.erfangholami.solidshare.data.repo.profile.PublicProfileRepository
import com.erfangholami.solidshare.data.repo.profile.PublicProfileRepositoryImplementation
import com.erfangholami.solidshare.data.repo.settings.SettingsRepository
import com.erfangholami.solidshare.data.repo.settings.SettingsRepositoryImplementation
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingRepositoryImplementation
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {

    @Binds
    @Singleton
    fun bindAuthRepository(
        implementation: AuthRepositoryImplementation,
    ): AuthRepository

    @Binds
    @Singleton
    fun bindSettingsRepository(
        implementation: SettingsRepositoryImplementation,
    ): SettingsRepository

    @Binds
    @Singleton
    fun bindFileRepository(
        implementation: FileRepositoryImplementation,
    ): FileRepository

    @Binds
    @Singleton
    fun bindSharingRepository(
        implementation: SharingRepositoryImplementation,
    ): SharingRepository

    @Binds
    @Singleton
    fun bindNotificationsRepository(
        implementation: NotificationsRepositoryImplementation,
    ): NotificationsRepository

    @Binds
    @Singleton
    fun bindPublicProfileRepository(
        implementation: PublicProfileRepositoryImplementation,
    ): PublicProfileRepository
}
