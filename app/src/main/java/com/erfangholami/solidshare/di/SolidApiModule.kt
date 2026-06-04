package com.erfangholami.solidshare.di

import android.content.Context
import com.erfangholami.androidsolidservices.api.auth.Authenticator
import com.erfangholami.androidsolidservices.api.notifications.NotificationsManager
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.api.sharing.SharingManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SolidApiModule {

    @Provides
    @Singleton
    fun provideAuthenticator(
        @ApplicationContext context: Context,
    ): Authenticator = Authenticator.getInstance(context)

    @Provides
    @Singleton
    fun provideSolidResourceManager(
        authenticator: Authenticator,
    ): SolidResourceManager = SolidResourceManager.getInstance(authenticator)

    @Provides
    @Singleton
    fun provideSharingManager(
        authenticator: Authenticator,
    ): SharingManager = SharingManager.getInstance(authenticator)

    @Provides
    @Singleton
    fun provideNotificationsManager(
        authenticator: Authenticator,
    ): NotificationsManager = NotificationsManager.getInstance(authenticator)
}
