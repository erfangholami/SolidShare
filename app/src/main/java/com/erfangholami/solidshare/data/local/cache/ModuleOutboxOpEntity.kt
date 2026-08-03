package com.erfangholami.solidshare.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "module_outbox_op",
    indices = [Index(value = ["module", "webId", "status", "nextRetryAt"])],
)
data class ModuleOutboxOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val module: String,
    val webId: String,
    val type: String,
    val payload: String,
    val status: OpStatus,
    val attempts: Int,
    val nextRetryAt: Long,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
