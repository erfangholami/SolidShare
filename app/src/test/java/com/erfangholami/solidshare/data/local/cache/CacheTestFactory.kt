package com.erfangholami.solidshare.data.local.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.erfangholami.solidshare.data.repo.outbox.ModuleOutbox
import com.erfangholami.solidshare.data.repo.outbox.OutboxQueue
import com.erfangholami.solidshare.data.repo.outbox.OutboxTrigger
import com.erfangholami.solidshare.domain.model.ResourceAccess
import com.erfangholami.solidshare.domain.model.ResourceType

const val TEST_WEB_ID = "https://alice.example/profile/card#me"
const val TEST_CONTAINER = "https://alice.example/files/"

fun inMemoryCacheDb(): SolidCacheDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        SolidCacheDatabase::class.java,
    ).allowMainThreadQueries().build()

fun testOutbox(db: SolidCacheDatabase): ModuleOutbox =
    ModuleOutbox(db.moduleOutboxDao(), NoOutboxTrigger)

object NoOutboxTrigger : OutboxTrigger {
    override fun requestDrain(queue: OutboxQueue) = Unit
}

fun resourceEntity(
    name: String,
    webId: String = TEST_WEB_ID,
    parent: String = TEST_CONTAINER,
    isContainer: Boolean = false,
    syncState: SyncState = SyncState.SYNCED,
    etag: String? = "etag-$name",
    cachedAt: Long = 0,
): CachedResourceEntity = CachedResourceEntity(
    webId = webId,
    identifier = parent.trimEnd('/') + "/" + name + if (isContainer) "/" else "",
    parentContainerUri = parent,
    isContainer = isContainer,
    name = name,
    extension = if (!isContainer && '.' in name) name.substringAfterLast('.') else null,
    mimeType = if (isContainer) null else "text/plain",
    resourceType = if (isContainer) ResourceType.FOLDER else ResourceType.OTHERS,
    resourceTypes = emptyList(),
    sizeBytes = if (isContainer) null else 10L,
    lastModified = 1_000L,
    etag = etag,
    access = ResourceAccess.FULL,
    createdTime = 1_000L,
    itemCount = if (isContainer) 0 else null,
    syncState = syncState,
    cachedAt = cachedAt,
)

fun blobEntity(
    name: String,
    webId: String = TEST_WEB_ID,
    parent: String = TEST_CONTAINER,
    localPath: String = "/tmp/$name",
    pinned: Boolean = false,
    sizeBytes: Long = 10L,
    lastAccessedAt: Long = 0,
    state: BlobState = BlobState.COMPLETE,
): CachedBlobEntity = CachedBlobEntity(
    webId = webId,
    uri = parent.trimEnd('/') + "/" + name,
    localPath = localPath,
    etag = "etag-$name",
    mimeType = "text/plain",
    sizeBytes = sizeBytes,
    pinned = pinned,
    lastAccessedAt = lastAccessedAt,
    state = state,
)

fun outboxOp(
    type: OpType,
    targetName: String,
    webId: String = TEST_WEB_ID,
    parent: String = TEST_CONTAINER,
    status: OpStatus = OpStatus.PENDING,
    nextRetryAt: Long = 0,
    createdAt: Long = 0,
): OutboxOpEntity = OutboxOpEntity(
    webId = webId,
    type = type,
    targetUri = parent.trimEnd('/') + "/" + targetName,
    parentContainerUri = parent,
    name = targetName,
    mimeType = "text/plain",
    blobPath = null,
    sourceUri = null,
    isContainer = false,
    status = status,
    attempts = 0,
    nextRetryAt = nextRetryAt,
    lastError = null,
    createdAt = createdAt,
    updatedAt = createdAt,
)
