package com.erfangholami.solidshare.data.repo.file

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.solidshare.data.local.cache.BlobDao
import com.erfangholami.solidshare.data.local.cache.BlobState
import com.erfangholami.solidshare.data.local.cache.CacheKeyManager
import com.erfangholami.solidshare.data.local.cache.CachedBlobEntity
import com.erfangholami.solidshare.data.local.cache.ResourceDao
import com.erfangholami.solidshare.data.local.cache.SolidCacheDatabase
import com.erfangholami.solidshare.data.local.cache.SyncState
import com.erfangholami.solidshare.data.local.cache.TEST_CONTAINER
import com.erfangholami.solidshare.data.local.cache.TEST_WEB_ID
import com.erfangholami.solidshare.data.local.cache.inMemoryCacheDb
import com.erfangholami.solidshare.data.local.cache.resourceEntity
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.ResourceType
import com.erfangholami.solidshare.util.NetworkMonitor
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileRepositoryCacheTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: SolidCacheDatabase
    private lateinit var resourceDao: ResourceDao
    private lateinit var blobDao: BlobDao
    private lateinit var resourceManager: SolidResourceManager
    private lateinit var repo: FileRepositoryImplementation
    private var online = true

    @Before
    fun setUp() {
        db = inMemoryCacheDb()
        resourceDao = db.resourceDao()
        blobDao = db.blobDao()
        resourceManager = mockk()

        val keyManager = mockk<CacheKeyManager>()
        every { keyManager.encryptStream(any(), any()) } answers {
            firstArg<InputStream>().use { input -> secondArg<File>().outputStream().use { input.copyTo(it) } }
        }
        every { keyManager.decryptStream(any(), any()) } answers {
            firstArg<File>().inputStream().use { it.copyTo(secondArg<OutputStream>()) }
        }
        val networkMonitor = mockk<NetworkMonitor>()
        every { networkMonitor.currentlyOnline() } answers { online }

        repo = FileRepositoryImplementation(
            context = context,
            resourceManager = resourceManager,
            resourceDao = resourceDao,
            blobDao = blobDao,
            keyManager = keyManager,
            networkMonitor = networkMonitor,
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun observeContainer_emitsCachedItemsAsDomain() = runTest {
        resourceDao.upsertAll(listOf(resourceEntity("a"), resourceEntity("b")))

        val names = repo.observeContainer(TEST_WEB_ID, TEST_CONTAINER).first().map { it.name }

        assertEquals(listOf("a", "b"), names)
    }

    @Test
    fun cacheContainer_thenGetCachedContainer_roundTrips() = runTest {
        repo.cacheContainer(TEST_WEB_ID, TEST_CONTAINER, listOf(domainItem("x"), domainItem("y")))

        assertEquals(
            listOf("x", "y"),
            repo.getCachedContainer(TEST_WEB_ID, TEST_CONTAINER).map { it.name },
        )
        assertNotNull(repo.lastCachedAt(TEST_WEB_ID, TEST_CONTAINER))
    }

    @Test
    fun observePendingAndErrorUris_reflectSyncState() = runTest {
        resourceDao.upsertAll(
            listOf(
                resourceEntity("p", syncState = SyncState.PENDING_CREATE),
                resourceEntity("e", syncState = SyncState.ERROR),
            ),
        )

        assertEquals(setOf(TEST_CONTAINER + "p"), repo.observePendingUris(TEST_WEB_ID).first().toSet())
        assertEquals(setOf(TEST_CONTAINER + "e"), repo.observeErrorUris(TEST_WEB_ID).first().toSet())
    }

    @Test
    fun downloadFile_offline_servesCachedBlobWithoutHittingNetwork() = runTest {
        val uri = TEST_CONTAINER + "photo.jpg"
        val blobFile = File(context.filesDir, "blob-photo").apply { writeText("secret-bytes") }
        // Note the null etag: this is the weak/no-ETag server case that used to break offline open.
        blobDao.upsert(
            CachedBlobEntity(
                webId = TEST_WEB_ID, uri = uri, localPath = blobFile.absolutePath,
                etag = null, mimeType = "image/jpeg", sizeBytes = blobFile.length(),
                pinned = true, lastAccessedAt = 0, state = BlobState.COMPLETE,
            ),
        )
        online = false

        val result = repo.downloadFile(TEST_WEB_ID, uri)

        assertEquals("secret-bytes", File(result.path).readText())
        assertEquals("image/jpeg", result.mimeType)
        coVerify(exactly = 0) { resourceManager.read<SolidNonRDFResource>(any(), any(), any()) }
    }

    @Test
    fun pinOffline_marksAlreadyCachedBlobPinned() = runTest {
        val uri = TEST_CONTAINER + "doc.pdf"
        blobDao.upsert(
            CachedBlobEntity(
                webId = TEST_WEB_ID, uri = uri, localPath = "/tmp/doc", etag = "e",
                mimeType = "application/pdf", sizeBytes = 1, pinned = false,
                lastAccessedAt = 0, state = BlobState.COMPLETE,
            ),
        )

        repo.pinOffline(TEST_WEB_ID, uri)

        assertTrue(blobDao.find(TEST_WEB_ID, uri)!!.pinned)
        coVerify(exactly = 0) { resourceManager.read<SolidNonRDFResource>(any(), any(), any()) }
    }

    @Test
    fun unpinOffline_marksBlobUnpinned() = runTest {
        val uri = TEST_CONTAINER + "doc.pdf"
        blobDao.upsert(
            CachedBlobEntity(
                webId = TEST_WEB_ID, uri = uri, localPath = "/tmp/doc", etag = "e",
                mimeType = "application/pdf", sizeBytes = 1, pinned = true,
                lastAccessedAt = 0, state = BlobState.COMPLETE,
            ),
        )

        repo.unpinOffline(TEST_WEB_ID, uri)

        assertFalse(blobDao.find(TEST_WEB_ID, uri)!!.pinned)
    }

    @Test
    fun makeThenRemoveOffline_togglesAvailableOfflineMembership() = runTest {
        // A file already on disk from auto-cache-on-view, but not explicitly saved offline.
        val uri = TEST_CONTAINER + "doc.pdf"
        blobDao.upsert(
            CachedBlobEntity(
                webId = TEST_WEB_ID, uri = uri, localPath = "/tmp/doc", etag = "e",
                mimeType = "application/pdf", sizeBytes = 1, pinned = false,
                lastAccessedAt = 0, state = BlobState.COMPLETE,
            ),
        )

        assertTrue(
            "an auto-cached file is not 'available offline' until pinned",
            repo.observeAvailableOffline(TEST_WEB_ID).first().isEmpty(),
        )

        repo.pinOffline(TEST_WEB_ID, uri)
        assertEquals(listOf(uri), repo.observeAvailableOffline(TEST_WEB_ID).first())

        // "Remove offline copy" must actually take it back out of the offline set.
        repo.unpinOffline(TEST_WEB_ID, uri)
        assertTrue(
            "removing the offline copy must drop it from availableOffline",
            repo.observeAvailableOffline(TEST_WEB_ID).first().isEmpty(),
        )
    }

    @Test
    fun clearCacheForWebId_removesResourcesAndBlobs() = runTest {
        resourceDao.upsert(resourceEntity("a"))
        val blobFile = File(context.filesDir, "to-clear").apply { writeText("x") }
        blobDao.upsert(
            CachedBlobEntity(
                webId = TEST_WEB_ID, uri = TEST_CONTAINER + "a", localPath = blobFile.absolutePath,
                etag = null, mimeType = "text/plain", sizeBytes = 1, pinned = true,
                lastAccessedAt = 0, state = BlobState.COMPLETE,
            ),
        )

        repo.clearCacheForWebId(TEST_WEB_ID)

        assertNull(resourceDao.findByIdentifier(TEST_WEB_ID, TEST_CONTAINER + "a"))
        assertNull(blobDao.find(TEST_WEB_ID, TEST_CONTAINER + "a"))
        assertFalse("blob file should be deleted from disk", blobFile.exists())
    }

    private fun domainItem(name: String): ContainerItem = ContainerItem(
        identifier = TEST_CONTAINER + name,
        isContainer = false,
        name = name,
        extension = null,
        mimeType = "text/plain",
        resourceType = ResourceType.OTHERS,
        resourceTypes = emptyList(),
        sizeBytes = 5,
        lastModified = 1,
        etag = "e",
    )
}
