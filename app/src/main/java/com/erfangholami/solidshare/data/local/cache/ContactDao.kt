package com.erfangholami.solidshare.data.local.cache

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query(
        "SELECT * FROM cached_contact WHERE webId = :webId AND syncState != 'PENDING_DELETE' " +
            "ORDER BY name COLLATE NOCASE ASC",
    )
    fun observeContacts(webId: String): Flow<List<CachedContactEntity>>

    @Query("SELECT * FROM cached_contact WHERE webId = :webId AND syncState != 'PENDING_DELETE'")
    suspend fun getContacts(webId: String): List<CachedContactEntity>

    @Query("SELECT * FROM cached_contact WHERE webId = :webId AND contactUri = :contactUri LIMIT 1")
    suspend fun findByUri(webId: String, contactUri: String): CachedContactEntity?

    @Upsert
    suspend fun upsert(item: CachedContactEntity)

    @Upsert
    suspend fun upsertAll(items: List<CachedContactEntity>)

    @Query(
        "UPDATE cached_contact SET syncState = :syncState WHERE webId = :webId " +
            "AND contactUri = :contactUri",
    )
    suspend fun updateSyncState(webId: String, contactUri: String, syncState: SyncState)

    @Query("DELETE FROM cached_contact WHERE webId = :webId AND contactUri = :contactUri")
    suspend fun deleteByUri(webId: String, contactUri: String)

    @Query("DELETE FROM cached_contact WHERE webId = :webId")
    suspend fun deleteAllForWebId(webId: String)

    @Query(
        "DELETE FROM cached_contact WHERE webId = :webId AND syncState = 'SYNCED' " +
            "AND contactUri NOT IN (:keepUris)",
    )
    suspend fun deleteSyncedNotIn(webId: String, keepUris: List<String>)

    @Query(
        "SELECT COUNT(*) FROM cached_contact WHERE webId = :webId " +
            "AND syncState IN ('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE')",
    )
    fun observePendingCount(webId: String): Flow<Int>

    @Transaction
    suspend fun replaceSynced(webId: String, items: List<CachedContactEntity>) {
        upsertAll(items)
        deleteSyncedNotIn(webId, items.map { it.contactUri })
    }
}
