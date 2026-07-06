package com.erfangholami.solidshare.data.local.cache

import androidx.room.Entity
import androidx.room.Index
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.ResourceAccess
import com.erfangholami.solidshare.domain.model.ResourceType

enum class SyncState {
    SYNCED,
    PENDING_CREATE,
    PENDING_UPDATE,
    PENDING_DELETE,
    CONFLICT,
    ERROR,
}

@Entity(
    tableName = "cached_resource",
    primaryKeys = ["webId", "identifier"],
    indices = [Index(value = ["webId", "parentContainerUri"])],
)
data class CachedResourceEntity(
    val webId: String,
    val identifier: String,
    val parentContainerUri: String,
    val isContainer: Boolean,
    val name: String,
    val extension: String?,
    val mimeType: String?,
    val resourceType: ResourceType,
    val resourceTypes: List<String>,
    val sizeBytes: Long?,
    val lastModified: Long?,
    val etag: String?,
    val access: ResourceAccess,
    val createdTime: Long?,
    val itemCount: Int?,
    val syncState: SyncState,
    val cachedAt: Long,
)

fun CachedResourceEntity.toDomain(): ContainerItem = ContainerItem(
    identifier = identifier,
    isContainer = isContainer,
    name = name,
    extension = extension,
    mimeType = mimeType,
    resourceType = resourceType,
    resourceTypes = resourceTypes,
    sizeBytes = sizeBytes,
    lastModified = lastModified,
    etag = etag,
    access = access,
    createdTime = createdTime,
    itemCount = itemCount,
)

fun ContainerItem.toCacheEntity(
    webId: String,
    parentContainerUri: String,
    cachedAt: Long,
    syncState: SyncState = SyncState.SYNCED,
): CachedResourceEntity = CachedResourceEntity(
    webId = webId,
    identifier = identifier,
    parentContainerUri = parentContainerUri,
    isContainer = isContainer,
    name = name,
    extension = extension,
    mimeType = mimeType,
    resourceType = resourceType,
    resourceTypes = resourceTypes,
    sizeBytes = sizeBytes,
    lastModified = lastModified,
    etag = etag,
    access = access,
    createdTime = createdTime,
    itemCount = itemCount,
    syncState = syncState,
    cachedAt = cachedAt,
)
