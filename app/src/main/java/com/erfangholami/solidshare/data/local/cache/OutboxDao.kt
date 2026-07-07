package com.erfangholami.solidshare.data.local.cache

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface OutboxDao {

    @Insert
    suspend fun insert(op: OutboxOpEntity): Long

    @Query(
        "SELECT * FROM outbox_op WHERE status = 'PENDING' OR (status = 'FAILED' AND nextRetryAt <= :now) " +
            "ORDER BY createdAt ASC LIMIT 1",
    )
    suspend fun nextActionable(now: Long): OutboxOpEntity?

    @Query("UPDATE outbox_op SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: OpStatus, now: Long)

    @Query(
        "UPDATE outbox_op SET status = :status, attempts = :attempts, nextRetryAt = :nextRetryAt, " +
            "lastError = :error, updatedAt = :now WHERE id = :id",
    )
    suspend fun setRetry(
        id: Long,
        status: OpStatus,
        attempts: Int,
        nextRetryAt: Long,
        error: String?,
        now: Long,
    )

    @Query("UPDATE outbox_op SET status = 'PENDING' WHERE status = 'IN_FLIGHT'")
    suspend fun resetInFlight()

    @Delete
    suspend fun delete(op: OutboxOpEntity)

    @Query("DELETE FROM outbox_op WHERE webId = :webId AND targetUri = :targetUri")
    suspend fun deleteByTarget(webId: String, targetUri: String)

    @Query("SELECT COUNT(*) FROM outbox_op WHERE status != 'ERROR'")
    suspend fun countUnfinished(): Int

    @Query("DELETE FROM outbox_op WHERE webId = :webId")
    suspend fun purgeForWebId(webId: String)
}
