package com.erfangholami.solidshare.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ContactOpType {
    DELETE,
    MERGE,
    DELETE_ALL,
}

@Entity(
    tableName = "contact_outbox_op",
    indices = [Index(value = ["webId", "status", "nextRetryAt"])],
)
data class ContactOutboxOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val webId: String,
    val type: ContactOpType,
    val payload: String,
    val status: OpStatus,
    val attempts: Int,
    val nextRetryAt: Long,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
