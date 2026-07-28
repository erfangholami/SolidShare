package com.erfangholami.solidshare.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TicketOpType {
    CREATE,
    UPDATE,
    DELETE,
}

@Entity(
    tableName = "ticket_outbox_op",
    indices = [Index(value = ["webId", "status", "nextRetryAt"])],
)
data class TicketOutboxOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val webId: String,
    val type: TicketOpType,
    val payload: String,
    val status: OpStatus,
    val attempts: Int,
    val nextRetryAt: Long,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
