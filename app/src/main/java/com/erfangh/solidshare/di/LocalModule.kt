package com.erfangh.solidshare.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class LocalModule {

    companion object {
        private const val PREFERENCES_NAME = "com.erfangh.solidshare.preferences"
    }

    private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(
        PREFERENCES_NAME
    )

    @Provides
    @Singleton
    fun providePreferencesDatasource(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.preferencesDataStore
}