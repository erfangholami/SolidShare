package com.erfangholami.solidshare.di

import android.content.Context
import androidx.room.Room
import com.erfangholami.solidshare.data.local.cache.BlobDao
import com.erfangholami.solidshare.data.local.cache.CacheKeyManager
import com.erfangholami.solidshare.data.local.cache.ContactDao
import com.erfangholami.solidshare.data.local.cache.ContactOutboxDao
import com.erfangholami.solidshare.data.local.cache.OutboxDao
import com.erfangholami.solidshare.data.local.cache.ResourceDao
import com.erfangholami.solidshare.data.local.cache.SolidCacheDatabase
import com.erfangholami.solidshare.data.local.cache.TicketDao
import com.erfangholami.solidshare.data.local.cache.TicketOutboxDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    private const val DATABASE_NAME = "solid_cache.db"

    @Provides
    @Singleton
    fun provideCacheDatabase(
        @ApplicationContext context: Context,
        keyManager: CacheKeyManager,
    ): SolidCacheDatabase {
        System.loadLibrary("sqlcipher")
        return runCatching { open(context, keyManager.databasePassphrase()) }
            .getOrElse {
                context.deleteDatabase(DATABASE_NAME)
                keyManager.resetPassphrase()
                open(context, keyManager.databasePassphrase())
            }
    }

    @Provides
    fun provideResourceDao(database: SolidCacheDatabase): ResourceDao = database.resourceDao()

    @Provides
    fun provideBlobDao(database: SolidCacheDatabase): BlobDao = database.blobDao()

    @Provides
    fun provideOutboxDao(database: SolidCacheDatabase): OutboxDao = database.outboxDao()

    @Provides
    fun provideContactDao(database: SolidCacheDatabase): ContactDao = database.contactDao()

    @Provides
    fun provideContactOutboxDao(database: SolidCacheDatabase): ContactOutboxDao =
        database.contactOutboxDao()

    @Provides
    fun provideTicketDao(database: SolidCacheDatabase): TicketDao = database.ticketDao()

    @Provides
    fun provideTicketOutboxDao(database: SolidCacheDatabase): TicketOutboxDao =
        database.ticketOutboxDao()

    private fun open(context: Context, passphrase: ByteArray): SolidCacheDatabase =
        Room.databaseBuilder(context, SolidCacheDatabase::class.java, DATABASE_NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
}
