package com.erfangholami.solidshare.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import com.erfangholami.solidshare.domain.model.ContactDetail
import kotlinx.serialization.json.Json

@Entity(
    tableName = "cached_contact",
    primaryKeys = ["webId", "contactUri"],
    indices = [Index(value = ["webId"])],
)
data class CachedContactEntity(
    val webId: String,
    val contactUri: String,
    val bookUri: String,
    val name: String,
    val detailJson: String,
    val etag: String? = null,
    val syncState: SyncState = SyncState.SYNCED,
    val cachedAt: Long,
)

private val contactJson = Json { ignoreUnknownKeys = true }

fun CachedContactEntity.toDomain(): ContactDetail =
    contactJson.decodeFromString(ContactDetail.serializer(), detailJson)

fun ContactDetail.toCacheEntity(
    webId: String,
    bookUri: String,
    cachedAt: Long,
    etag: String? = null,
    syncState: SyncState = SyncState.SYNCED,
): CachedContactEntity = CachedContactEntity(
    webId = webId,
    contactUri = uri,
    bookUri = bookUri,
    name = fullName,
    detailJson = contactJson.encodeToString(ContactDetail.serializer(), this),
    etag = etag,
    syncState = syncState,
    cachedAt = cachedAt,
)
