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
            "ORDER BY isContainer DESC, name COLLATE NOCASE ASC",
    )
    fun observeContainer(webId: String, parentUri: String): Flow<List<CachedResourceEntity>>

    @Query("SELECT * FROM cached_resource WHERE webId = :webId AND identifier = :identifier LIMIT 1")
    suspend fun findByIdentifier(webId: String, identifier: String): CachedResourceEntity?

    @Query(
        "SELECT * FROM cached_resource WHERE webId = :webId AND parentContainerUri = :parentUri " +
            "ORDER BY isContainer DESC, name COLLATE NOCASE ASC",
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
            "AND identifier NOT IN (:keepIdentifiers)",
    )
    suspend fun deleteMissing(webId: String, parentUri: String, keepIdentifiers: List<String>)

    @Query("DELETE FROM cached_resource WHERE webId = :webId AND parentContainerUri = :parentUri")
    suspend fun deleteAllInContainer(webId: String, parentUri: String)

    @Query("DELETE FROM cached_resource WHERE webId = :webId")
    suspend fun purgeForWebId(webId: String)

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
        upsertAll(items)
        deleteMissing(webId, parentUri, items.map { it.identifier })
    }
}
