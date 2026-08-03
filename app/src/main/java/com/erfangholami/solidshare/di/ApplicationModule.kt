package com.erfangholami.solidshare.di

import android.content.Context
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApplicationModule {

    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context,
    ): WorkManager = WorkManager.getInstance(context)

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
