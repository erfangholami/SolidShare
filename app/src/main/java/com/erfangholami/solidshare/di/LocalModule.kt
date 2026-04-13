package com.erfangholami.solidshare.di

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.pondersource.shared.data.ProfileList
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class LocalModule {

    companion object {
        private const val PREFERENCES_NAME = "com.erfangholami.solidshare.preferences"
        private const val LOGGED_OUT_WEBID_NAME = "loggedoutwebids.json"
    }

    private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(
        PREFERENCES_NAME
    )

    private val Context.loggedOutWebIDsDataStore: DataStore<List<String>> by dataStore(
        fileName = LOGGED_OUT_WEBID_NAME,
        serializer = object : Serializer<List<String>> {
            override val defaultValue: List<String>
                get() = emptyList()

            override suspend fun readFrom(input: InputStream): List<String> {
                try {
                    return Json.decodeFromString<List<String>>(
                        input.readBytes().decodeToString()
                    )
                } catch (serialization: SerializationException) {
                    throw CorruptionException("Unable to read Time", serialization)
                }
            }

            override suspend fun writeTo(
                t: List<String>,
                output: OutputStream
            ) {
                withContext(Dispatchers.IO) {
                    output.write(
                        Json.encodeToString(t)
                            .encodeToByteArray()
                    )
                }
            }

        }
    )

    @Provides
    @Singleton
    fun providePreferencesDatasource(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.preferencesDataStore

    @Provides
    @Singleton
    fun provideLoggedOutWebIDsDatasource(
        @ApplicationContext context: Context
    ): DataStore<List<String>> = context.loggedOutWebIDsDataStore
}