package com.erfangholami.solidshare.di

import android.content.Context
import com.erfangholami.solidshare.telemetry.AuthAnalytics
import com.erfangholami.solidshare.telemetry.FirebaseAuthAnalytics
import com.erfangholami.solidshare.telemetry.FirebaseTelemetryInstaller
import com.erfangholami.solidshare.telemetry.TelemetryInstaller
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface TelemetryModule {

    @Binds
    fun bindAuthAnalytics(implementation: FirebaseAuthAnalytics): AuthAnalytics

    @Binds
    fun bindTelemetryInstaller(implementation: FirebaseTelemetryInstaller): TelemetryInstaller
}

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAnalytics(
        @ApplicationContext context: Context,
    ): FirebaseAnalytics {
        FirebaseApp.initializeApp(context)
        return FirebaseAnalytics.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideFirebaseCrashlytics(
        @ApplicationContext context: Context,
    ): FirebaseCrashlytics {
        FirebaseApp.initializeApp(context)
        return FirebaseCrashlytics.getInstance()
    }
}
