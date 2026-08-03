package com.erfangholami.solidshare.data.local.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ModuleOutboxDao {

    @Insert
    suspend fun insert(op: ModuleOutboxOpEntity): Long

    @Query(
        "SELECT * FROM module_outbox_op WHERE module = :module AND webId = :webId " +
            "AND status IN ('PENDING', 'FAILED') AND nextRetryAt <= :now ORDER BY id ASC",
    )
    suspend fun dueOps(module: String, webId: String, now: Long): List<ModuleOutboxOpEntity>

    @Query(
        "SELECT * FROM module_outbox_op WHERE module = :module AND webId = :webId " +
            "AND status IN ('PENDING', 'FAILED') ORDER BY id ASC",
    )
    suspend fun pendingOps(module: String, webId: String): List<ModuleOutboxOpEntity>

    @Query(
        "SELECT DISTINCT module || ' ' || webId FROM module_outbox_op " +
            "WHERE status IN ('PENDING', 'FAILED')",
    )
    suspend fun pendingModuleWebIds(): List<String>

    @Update
    suspend fun update(op: ModuleOutboxOpEntity)

    @Query("DELETE FROM module_outbox_op WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM module_outbox_op WHERE module = :module AND webId = :webId")
    suspend fun deleteAllForWebId(module: String, webId: String)

    @Query(
        "SELECT COUNT(*) FROM module_outbox_op WHERE module = :module AND webId = :webId " +
            "AND status IN ('PENDING', 'FAILED')",
    )
    fun observePendingCount(module: String, webId: String): Flow<Int>
}
