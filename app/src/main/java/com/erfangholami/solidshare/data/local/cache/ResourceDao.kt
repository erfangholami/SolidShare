package com.erfangholami.solidshare.data.local.cache

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ResourceDao {

    @Query(
        "SELECT * FROM cached_resource WHERE webId = :webId AND parentContainerUri = :parentUri " +
            "AND syncState != 'PENDING_DELETE' ORDER BY isContainer DESC, name COLLATE NOCASE ASC",
    )
    fun observeContainer(webId: String, parentUri: String): Flow<List<CachedResourceEntity>>

    @Query(
        "SELECT identifier FROM cached_resource WHERE webId = :webId " +
            "AND syncState IN ('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE')",
    )
    fun observePendingUris(webId: String): Flow<List<String>>

    @Query(
        "SELECT identifier FROM cached_resource WHERE webId = :webId AND syncState IN ('ERROR', 'CONFLICT')",
    )
    fun observeErrorUris(webId: String): Flow<List<String>>

    @Upsert
    suspend fun upsert(item: CachedResourceEntity)

    @Query(
        "UPDATE cached_resource SET syncState = :syncState WHERE webId = :webId AND identifier = :identifier",
    )
    suspend fun updateSyncState(webId: String, identifier: String, syncState: SyncState)

    @Query("DELETE FROM cached_resource WHERE webId = :webId AND identifier = :identifier")
    suspend fun deleteByIdentifier(webId: String, identifier: String)

    @Query("SELECT * FROM cached_resource WHERE webId = :webId AND identifier = :identifier LIMIT 1")
    suspend fun findByIdentifier(webId: String, identifier: String): CachedResourceEntity?

    @Query(
        "SELECT * FROM cached_resource WHERE webId = :webId AND parentContainerUri = :parentUri " +
            "AND syncState != 'PENDING_DELETE' ORDER BY isContainer DESC, name COLLATE NOCASE ASC",
    )
    suspend fun getContainer(webId: String, parentUri: String): List<CachedResourceEntity>

    @Query(
        "SELECT MAX(cachedAt) FROM cached_resource WHERE webId = :webId AND parentContainerUri = :parentUri",
    )
    suspend fun lastCachedAt(webId: String, parentUri: String): Long?

    @Upsert
    suspend fun upsertAll(items: List<CachedResourceEntity>)

    @Query(
        "DELETE FROM cached_resource WHERE webId = :webId AND parentContainerUri = :parentUri " +
            "AND syncState = 'SYNCED' AND identifier NOT IN (:keepIdentifiers)",
    )
    suspend fun deleteMissing(webId: String, parentUri: String, keepIdentifiers: List<String>)

    @Query(
        "DELETE FROM cached_resource WHERE webId = :webId AND parentContainerUri = :parentUri " +
            "AND syncState = 'SYNCED'",
    )
    suspend fun deleteAllInContainer(webId: String, parentUri: String)

    @Query("DELETE FROM cached_resource WHERE webId = :webId")
    suspend fun purgeForWebId(webId: String)

    @Query(
        "SELECT identifier FROM cached_resource WHERE webId = :webId " +
            "AND parentContainerUri = :parentUri AND syncState != 'SYNCED'",
    )
    suspend fun pendingIdentifiersIn(webId: String, parentUri: String): List<String>

    @Transaction
    suspend fun replaceContainer(
        webId: String,
        parentUri: String,
        items: List<CachedResourceEntity>,
    ) {
        if (items.isEmpty()) {
            deleteAllInContainer(webId, parentUri)
            return
        }
        val pending = pendingIdentifiersIn(webId, parentUri).toSet()
        val incoming = items.filter { it.identifier !in pending }
        if (incoming.isNotEmpty()) upsertAll(incoming)
        deleteMissing(webId, parentUri, items.map { it.identifier })
    }
}
