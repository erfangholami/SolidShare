package com.erfangholami.solidshare.data.local.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        CachedResourceEntity::class,
        CachedBlobEntity::class,
        OutboxOpEntity::class,
        CachedEntityEntity::class,
        ModuleOutboxOpEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
@TypeConverters(CacheConverters::class)
abstract class SolidCacheDatabase : RoomDatabase() {
    abstract fun resourceDao(): ResourceDao
    abstract fun blobDao(): BlobDao
    abstract fun outboxDao(): OutboxDao
    abstract fun cachedEntityDao(): CachedEntityDao
    abstract fun moduleOutboxDao(): ModuleOutboxDao
}
