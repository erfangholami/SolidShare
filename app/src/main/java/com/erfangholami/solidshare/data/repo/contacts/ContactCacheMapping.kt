package com.erfangholami.solidshare.data.repo.contacts

import com.erfangholami.solidshare.data.local.cache.CachedEntityEntity
import com.erfangholami.solidshare.data.local.cache.SyncState
import com.erfangholami.solidshare.data.repo.datamodule.DataModuleIds
import com.erfangholami.solidshare.domain.model.ContactDetail
import kotlinx.serialization.json.Json

private val contactJson = Json { ignoreUnknownKeys = true }

fun ContactDetail.toCacheEntity(
    webId: String,
    bookUri: String,
    cachedAt: Long,
    etag: String? = null,
    syncState: SyncState = SyncState.SYNCED,
): CachedEntityEntity = CachedEntityEntity(
    module = DataModuleIds.CONTACTS,
    webId = webId,
    uri = uri,
    sortKey = fullName,
    groupKey = bookUri,
    searchText = fullName.lowercase(),
    detailJson = contactJson.encodeToString(ContactDetail.serializer(), this),
    etag = etag,
    syncState = syncState,
    cachedAt = cachedAt,
)

fun CachedEntityEntity.toContactDetail(): ContactDetail =
    contactJson.decodeFromString(ContactDetail.serializer(), detailJson)
