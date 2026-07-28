package com.erfangholami.solidshare.data.local.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        CachedResourceEntity::class,
        CachedBlobEntity::class,
        OutboxOpEntity::class,
        CachedContactEntity::class,
        ContactOutboxOpEntity::class,
        CachedTicketEntity::class,
        TicketOutboxOpEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(CacheConverters::class)
abstract class SolidCacheDatabase : RoomDatabase() {
    abstract fun resourceDao(): ResourceDao
    abstract fun blobDao(): BlobDao
    abstract fun outboxDao(): OutboxDao
    abstract fun contactDao(): ContactDao
    abstract fun contactOutboxDao(): ContactOutboxDao
    abstract fun ticketDao(): TicketDao
    abstract fun ticketOutboxDao(): TicketOutboxDao
}
