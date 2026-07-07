package com.erfangholami.solidshare.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class OpType {
    UPLOAD,
    CREATE_FOLDER,
    DELETE,
    COPY,
}

enum class OpStatus {
    PENDING,
    IN_FLIGHT,
    FAILED,
    ERROR,
}

@Entity(
    tableName = "outbox_op",
    indices = [Index(value = ["webId", "status", "nextRetryAt"])],
)
data class OutboxOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val webId: String,
    val type: OpType,
    val targetUri: String,
    val parentContainerUri: String,
    val name: String,
    val mimeType: String?,
    val blobPath: String?,
    val sourceUri: String? = null,
    val isContainer: Boolean,
    val status: OpStatus,
    val attempts: Int,
    val nextRetryAt: Long,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
