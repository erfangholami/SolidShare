package com.erfangholami.solidshare.data.local.cache

import com.erfangholami.solidshare.domain.model.ResourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class CacheMappersTest {

    @Test
    fun toDomain_copiesResourceFields() {
        val entity = resourceEntity("photo.jpg")

        val domain = entity.toDomain()

        assertEquals(entity.identifier, domain.identifier)
        assertEquals(entity.name, domain.name)
        assertEquals(entity.mimeType, domain.mimeType)
        assertEquals(entity.etag, domain.etag)
        assertEquals(entity.sizeBytes, domain.sizeBytes)
        assertEquals(entity.access, domain.access)
    }

    @Test
    fun toCacheEntity_thenToDomain_isLossless() {
        val original = resourceEntity("Report.pdf", isContainer = false, syncState = SyncState.PENDING_CREATE)

        val rebuilt = original.toDomain().toCacheEntity(
            webId = original.webId,
            parentContainerUri = original.parentContainerUri,
            cachedAt = original.cachedAt,
            syncState = original.syncState,
        )

        assertEquals(original, rebuilt)
    }

    @Test
    fun folderEntity_mapsToFolderDomain() {
        val folder = resourceEntity("Photos", isContainer = true)

        val domain = folder.toDomain()

        assertEquals(true, domain.isContainer)
        assertEquals(ResourceType.FOLDER, domain.resourceType)
    }
}
