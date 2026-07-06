package com.erfangholami.solidshare.data.local.cache

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BlobDao {

    @Query("SELECT * FROM cached_blob WHERE webId = :webId AND uri = :uri LIMIT 1")
    suspend fun find(webId: String, uri: String): CachedBlobEntity?

    @Query("SELECT uri FROM cached_blob WHERE webId = :webId AND state = :state")
    fun observeCompleteUris(webId: String, state: BlobState): Flow<List<String>>

    @Upsert
    suspend fun upsert(blob: CachedBlobEntity)

    @Query("UPDATE cached_blob SET pinned = :pinned WHERE webId = :webId AND uri = :uri")
    suspend fun setPinned(webId: String, uri: String, pinned: Boolean)

    @Query("UPDATE cached_blob SET lastAccessedAt = :now WHERE webId = :webId AND uri = :uri")
    suspend fun touch(webId: String, uri: String, now: Long)

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM cached_blob WHERE pinned = 0 AND state = :state")
    suspend fun unpinnedSize(state: BlobState): Long

    @Query(
        "SELECT * FROM cached_blob WHERE pinned = 0 AND state = :state ORDER BY lastAccessedAt ASC",
    )
    suspend fun unpinnedByAge(state: BlobState): List<CachedBlobEntity>

    @Query("SELECT * FROM cached_blob WHERE webId = :webId")
    suspend fun forWebId(webId: String): List<CachedBlobEntity>

    @Delete
    suspend fun delete(blob: CachedBlobEntity)

    @Query("DELETE FROM cached_blob WHERE webId = :webId")
    suspend fun purgeRowsForWebId(webId: String)
}
