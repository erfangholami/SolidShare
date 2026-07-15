package com.erfangholami.solidshare.data.local.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactOutboxDao {

    @Insert
    suspend fun insert(op: ContactOutboxOpEntity): Long

    @Query(
        "SELECT * FROM contact_outbox_op WHERE webId = :webId " +
            "AND status IN ('PENDING', 'FAILED') AND nextRetryAt <= :now ORDER BY id ASC",
    )
    suspend fun dueOps(webId: String, now: Long): List<ContactOutboxOpEntity>

    @Query("SELECT DISTINCT webId FROM contact_outbox_op WHERE status IN ('PENDING', 'FAILED')")
    suspend fun pendingWebIds(): List<String>

    @Update
    suspend fun update(op: ContactOutboxOpEntity)

    @Query("DELETE FROM contact_outbox_op WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM contact_outbox_op WHERE webId = :webId")
    suspend fun deleteAllForWebId(webId: String)

    @Query(
        "SELECT COUNT(*) FROM contact_outbox_op WHERE webId = :webId " +
            "AND status IN ('PENDING', 'FAILED')",
    )
    fun observePendingCount(webId: String): Flow<Int>
}
