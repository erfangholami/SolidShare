package com.erfangholami.solidshare.data.local.cache

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "cached_entity",
    primaryKeys = ["module", "webId", "uri"],
    indices = [Index(value = ["module", "webId"])],
)
data class CachedEntityEntity(
    val module: String,
    val webId: String,
    val uri: String,
    val sortKey: String? = null,
    val groupKey: String? = null,
    val searchText: String? = null,
    val detailJson: String,
    val etag: String? = null,
    val syncState: SyncState = SyncState.SYNCED,
    val cachedAt: Long,
)
