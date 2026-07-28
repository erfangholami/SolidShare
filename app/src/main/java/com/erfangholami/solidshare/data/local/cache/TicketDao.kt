package com.erfangholami.solidshare.data.local.cache

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TicketDao {

    @Query(
        "SELECT * FROM cached_ticket WHERE webId = :webId " +
            "AND syncState != 'PENDING_DELETE' ORDER BY cachedAt DESC",
    )
    fun observeTickets(webId: String): Flow<List<CachedTicketEntity>>

    @Query(
        "SELECT * FROM cached_ticket WHERE webId = :webId AND syncState != 'PENDING_DELETE'",
    )
    suspend fun getTickets(webId: String): List<CachedTicketEntity>

    @Query("SELECT * FROM cached_ticket WHERE webId = :webId AND ticketUri = :ticketUri")
    suspend fun findByUri(webId: String, ticketUri: String): CachedTicketEntity?

    @Upsert
    suspend fun upsert(ticket: CachedTicketEntity)

    @Upsert
    suspend fun upsertAll(tickets: List<CachedTicketEntity>)

    @Query(
        "UPDATE cached_ticket SET syncState = :syncState " +
            "WHERE webId = :webId AND ticketUri = :ticketUri",
    )
    suspend fun updateSyncState(webId: String, ticketUri: String, syncState: SyncState)

    @Query("DELETE FROM cached_ticket WHERE webId = :webId AND ticketUri = :ticketUri")
    suspend fun deleteByUri(webId: String, ticketUri: String)

    @Query("DELETE FROM cached_ticket WHERE webId = :webId")
    suspend fun deleteAllForWebId(webId: String)

    @Query(
        "DELETE FROM cached_ticket WHERE webId = :webId AND syncState = 'SYNCED' " +
            "AND ticketUri NOT IN (:keepUris)",
    )
    suspend fun deleteSyncedNotIn(webId: String, keepUris: List<String>)

    @Query(
        "SELECT COUNT(*) FROM cached_ticket WHERE webId = :webId " +
            "AND syncState IN ('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE')",
    )
    fun observePendingCount(webId: String): Flow<Int>

    @Transaction
    suspend fun replaceSynced(webId: String, tickets: List<CachedTicketEntity>) {
        upsertAll(tickets)
        deleteSyncedNotIn(webId, tickets.map { it.ticketUri })
    }
}
