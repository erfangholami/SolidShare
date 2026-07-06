package com.erfangholami.solidshare.data.local.cache

import androidx.room.Entity

enum class BlobState {
    COMPLETE,
    PENDING_UPLOAD,
}

@Entity(
    tableName = "cached_blob",
    primaryKeys = ["webId", "uri"],
)
data class CachedBlobEntity(
    val webId: String,
    val uri: String,
    val localPath: String,
    val etag: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val pinned: Boolean,
    val lastAccessedAt: Long,
    val state: BlobState,
)
