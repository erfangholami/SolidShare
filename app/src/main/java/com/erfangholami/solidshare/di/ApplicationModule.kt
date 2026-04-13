package com.erfangholami.solidshare.di

import android.content.Context
import com.pondersource.solidandroidapi.Authenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class ApplicationModule {

    @Provides
    @Singleton
    fun provideAuthenticator(
        @ApplicationContext context: Context
    ): Authenticator {
        return Authenticator.getInstance(context)
    }

}
