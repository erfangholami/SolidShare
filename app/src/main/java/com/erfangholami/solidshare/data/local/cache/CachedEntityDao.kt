package com.erfangholami.solidshare.data.local.cache

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedEntityDao {

    @Query(
        "SELECT * FROM cached_entity WHERE module = :module AND webId = :webId " +
            "AND syncState != 'PENDING_DELETE' ORDER BY sortKey COLLATE NOCASE ASC",
    )
    fun observeBySortKey(module: String, webId: String): Flow<List<CachedEntityEntity>>

    @Query(
        "SELECT * FROM cached_entity WHERE module = :module AND webId = :webId " +
            "AND syncState != 'PENDING_DELETE' ORDER BY cachedAt DESC",
    )
    fun observeNewestFirst(module: String, webId: String): Flow<List<CachedEntityEntity>>

    @Query(
        "SELECT * FROM cached_entity WHERE module = :module AND webId = :webId " +
            "AND syncState != 'PENDING_DELETE'",
    )
    suspend fun get(module: String, webId: String): List<CachedEntityEntity>

    @Query(
        "SELECT * FROM cached_entity WHERE module = :module AND webId = :webId " +
            "AND uri = :uri LIMIT 1",
    )
    suspend fun findByUri(module: String, webId: String, uri: String): CachedEntityEntity?

    @Query("SELECT DISTINCT webId FROM cached_entity WHERE module = :module")
    suspend fun webIds(module: String): List<String>

    @Upsert
    suspend fun upsert(item: CachedEntityEntity)

    @Upsert
    suspend fun upsertAll(items: List<CachedEntityEntity>)

    @Query(
        "UPDATE cached_entity SET syncState = :syncState WHERE module = :module " +
            "AND webId = :webId AND uri = :uri",
    )
    suspend fun updateSyncState(module: String, webId: String, uri: String, syncState: SyncState)

    @Query("DELETE FROM cached_entity WHERE module = :module AND webId = :webId AND uri = :uri")
    suspend fun deleteByUri(module: String, webId: String, uri: String)

    @Query("DELETE FROM cached_entity WHERE module = :module AND webId = :webId")
    suspend fun deleteAllForWebId(module: String, webId: String)

    @Query(
        "DELETE FROM cached_entity WHERE module = :module AND webId = :webId " +
            "AND groupKey = :groupKey",
    )
    suspend fun deleteByGroupKey(module: String, webId: String, groupKey: String)

    @Query(
        "DELETE FROM cached_entity WHERE module = :module AND webId = :webId " +
            "AND syncState = 'SYNCED' AND uri NOT IN (:keepUris)",
    )
    suspend fun deleteSyncedNotIn(module: String, webId: String, keepUris: List<String>)

    @Transaction
    suspend fun replaceSynced(module: String, webId: String, items: List<CachedEntityEntity>) {
        upsertAll(items)
        deleteSyncedNotIn(module, webId, items.map { it.uri })
    }
}
