package com.erfangholami.solidshare.data.local.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [CachedResourceEntity::class, CachedBlobEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(CacheConverters::class)
abstract class SolidCacheDatabase : RoomDatabase() {
    abstract fun resourceDao(): ResourceDao
    abstract fun blobDao(): BlobDao
}
