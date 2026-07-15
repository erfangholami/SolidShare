package com.erfangholami.solidshare.data.repo.outbox

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.resource.Resource
import com.erfangholami.solidshare.data.local.cache.BlobDao
import com.erfangholami.solidshare.data.local.cache.BlobState
import com.erfangholami.solidshare.data.local.cache.CacheKeyManager
import com.erfangholami.solidshare.data.local.cache.OpType
import com.erfangholami.solidshare.data.local.cache.OutboxDao
import com.erfangholami.solidshare.data.local.cache.ResourceDao
import com.erfangholami.solidshare.data.local.cache.SolidCacheDatabase
import com.erfangholami.solidshare.data.local.cache.SyncState
import com.erfangholami.solidshare.data.local.cache.TEST_CONTAINER
import com.erfangholami.solidshare.data.local.cache.TEST_WEB_ID
import com.erfangholami.solidshare.data.local.cache.blobEntity
import com.erfangholami.solidshare.data.local.cache.inMemoryCacheDb
import com.erfangholami.solidshare.data.local.cache.outboxOp
import com.erfangholami.solidshare.data.local.cache.resourceEntity
import com.erfangholami.solidshare.data.repo.file.FileRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.ResourceType
import com.erfangholami.solidshare.util.NetworkMonitor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OutboxRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: SolidCacheDatabase
    private lateinit var resourceDao: ResourceDao
    private lateinit var blobDao: BlobDao
    private lateinit var outboxDao: OutboxDao
    private lateinit var resourceManager: SolidResourceManager
    private lateinit var fileRepository: FileRepository
    private lateinit var sharingRepository: SharingRepository
    private lateinit var repo: OutboxRepositoryImplementation

    @Before
    fun setUp() {
        db = inMemoryCacheDb()
        resourceDao = db.resourceDao()
        blobDao = db.blobDao()
        outboxDao = db.outboxDao()
        resourceManager = mockk()
        fileRepository = mockk(relaxed = true)
        sharingRepository = mockk(relaxed = true)

        val keyManager = mockk<CacheKeyManager>()
        every { keyManager.encryptStream(any(), any()) } answers {
            firstArg<InputStream>().use { input -> secondArg<File>().outputStream().use { input.copyTo(it) } }
        }
        every { keyManager.decryptStream(any(), any()) } answers {
            firstArg<File>().inputStream().use { it.copyTo(secondArg<OutputStream>()) }
        }

        repo = OutboxRepositoryImplementation(
            context = context,
            resourceManager = resourceManager,
            resourceDao = resourceDao,
            blobDao = blobDao,
            outboxDao = outboxDao,
            keyManager = keyManager,
            networkMonitor = mockk<NetworkMonitor>(relaxed = true),
            workManager = mockk<WorkManager>(relaxed = true),
            fileRepository = fileRepository,
            sharingRepository = sharingRepository,
        )
    }

    @After
    fun tearDown() = db.close()

    // ---- upload ----

    @Test
    fun enqueueUpload_writesPendingResourceAndPinnedBlobAndOp() = runTest {
        repo.enqueueUpload(TEST_WEB_ID, TEST_CONTAINER, source("hello"), "photo.jpg", "image/jpeg")

        val uri = TEST_CONTAINER + "photo.jpg"
        assertEquals(SyncState.PENDING_CREATE, resourceDao.findByIdentifier(TEST_WEB_ID, uri)!!.syncState)
        val blob = blobDao.find(TEST_WEB_ID, uri)!!
        assertEquals(BlobState.PENDING_UPLOAD, blob.state)
        assertTrue("pending upload blob must be pinned so it is viewable offline", blob.pinned)
        assertEquals(OpType.UPLOAD, pending()!!.type)
    }

    @Test
    fun drainUpload_success_marksSyncedBlobCompleteAndClearsOp() = runTest {
        coEvery { resourceManager.createInContainer(any(), any(), any()) } returns success()
        repo.enqueueUpload(TEST_WEB_ID, TEST_CONTAINER, source("hi"), "photo.jpg", "image/jpeg")

        repo.drain()

        val uri = TEST_CONTAINER + "photo.jpg"
        assertEquals(SyncState.SYNCED, resourceDao.findByIdentifier(TEST_WEB_ID, uri)!!.syncState)
        assertEquals(BlobState.COMPLETE, blobDao.find(TEST_WEB_ID, uri)!!.state)
        assertEquals(0, outboxDao.countUnfinished())
    }

    @Test
    fun drainUpload_transientError_keepsPendingAndSchedulesRetry() = runTest {
        coEvery { resourceManager.createInContainer(any(), any(), any()) } returns
            SolidResult.Failure(SolidError.fromThrowable(IOException("network down")))
        repo.enqueueUpload(TEST_WEB_ID, TEST_CONTAINER, source("hi"), "photo.jpg", "image/jpeg")

        repo.drain()

        val uri = TEST_CONTAINER + "photo.jpg"
        assertEquals(SyncState.PENDING_CREATE, resourceDao.findByIdentifier(TEST_WEB_ID, uri)!!.syncState)
        assertEquals("op should remain queued for retry", 1, outboxDao.countUnfinished())
        assertNotNull("op should be retryable in the future", outboxDao.nextActionable(Long.MAX_VALUE))
    }

    @Test
    fun drainUpload_terminalError_marksResourceErrored() = runTest {
        coEvery { resourceManager.createInContainer(any(), any(), any()) } returns
            SolidResult.Failure(SolidError.fromHttp(403, "Forbidden"))
        repo.enqueueUpload(TEST_WEB_ID, TEST_CONTAINER, source("hi"), "photo.jpg", "image/jpeg")

        repo.drain()

        val uri = TEST_CONTAINER + "photo.jpg"
        assertEquals(SyncState.ERROR, resourceDao.findByIdentifier(TEST_WEB_ID, uri)!!.syncState)
        assertEquals("terminal op is not retried", 0, outboxDao.countUnfinished())
    }

    // ---- create folder ----

    @Test
    fun enqueueCreateFolder_thenDrain_createsFolderAndSyncs() = runTest {
        coEvery { resourceManager.createInContainer(any(), any(), any()) } returns success()
        repo.enqueueCreateFolder(TEST_WEB_ID, TEST_CONTAINER, "New Folder")

        val folderUri = TEST_CONTAINER + "New Folder/"
        val optimistic = resourceDao.findByIdentifier(TEST_WEB_ID, folderUri)!!
        assertTrue(optimistic.isContainer)
        assertEquals(SyncState.PENDING_CREATE, optimistic.syncState)

        repo.drain()

        assertEquals(SyncState.SYNCED, resourceDao.findByIdentifier(TEST_WEB_ID, folderUri)!!.syncState)
    }

    // ---- offline writes must actually reach the server (stateful fake server) ----

    @Test
    fun createFolderOffline_afterDrain_folderActuallyExistsOnServer() = runTest {
        val server = fakeServer()
        repo.enqueueCreateFolder(TEST_WEB_ID, TEST_CONTAINER, "Docs")
        assertTrue("nothing must reach the server while offline", server.isEmpty())

        repo.drain()

        assertTrue(
            "the offline-created folder must exist on the server after the drain runs",
            server.contains(TEST_CONTAINER + "Docs/"),
        )
    }

    @Test
    fun createFolderThenUploadInside_afterDrain_bothExistOnServer() = runTest {
        val server = fakeServer()
        repo.enqueueCreateFolder(TEST_WEB_ID, TEST_CONTAINER, "Docs")
        val folderUri = TEST_CONTAINER + "Docs/"
        repo.enqueueUpload(TEST_WEB_ID, folderUri, source("hi"), "note.txt", "text/plain")

        repo.drain()

        assertTrue("the folder must be created on the server", server.contains(folderUri))
        assertTrue(
            "a file uploaded offline into that folder must exist on the server too",
            server.contains(folderUri + "note.txt"),
        )
    }

    @Test
    fun createThenDeleteOffline_afterDrain_serverNeverSeesTheResource() = runTest {
        val server = fakeServer()
        repo.enqueueUpload(TEST_WEB_ID, TEST_CONTAINER, source("hi"), "temp.txt", "text/plain")
        repo.enqueueDelete(TEST_WEB_ID, TEST_CONTAINER + "temp.txt", isContainer = false)

        repo.drain()

        assertTrue("a create cancelled by an offline delete must never reach the server", server.isEmpty())
        assertEquals(0, outboxDao.countUnfinished())
    }

    // ---- delete ----

    @Test
    fun enqueueDelete_ofSyncedResource_marksPendingDeleteThenRemovesOnDrain() = runTest {
        resourceDao.upsert(resourceEntity("doc.txt", syncState = SyncState.SYNCED))
        val uri = TEST_CONTAINER + "doc.txt"

        repo.enqueueDelete(TEST_WEB_ID, uri, isContainer = false)
        assertEquals(SyncState.PENDING_DELETE, resourceDao.findByIdentifier(TEST_WEB_ID, uri)!!.syncState)
        assertEquals(OpType.DELETE, pending()!!.type)

        coEvery { resourceManager.delete(any(), any()) } returns SolidResult.Success(true)
        repo.drain()

        assertNull(resourceDao.findByIdentifier(TEST_WEB_ID, uri))
        assertEquals(0, outboxDao.countUnfinished())
    }

    @Test
    fun enqueueDelete_ofNotYetSyncedCreate_coalescesAndCancelsEverything() = runTest {
        // An offline-created file that hasn't synced yet, with its queued upload.
        resourceDao.upsert(resourceEntity("new.txt", syncState = SyncState.PENDING_CREATE))
        blobDao.upsert(blobEntity("new.txt", state = BlobState.PENDING_UPLOAD))
        outboxDao.insert(outboxOp(OpType.UPLOAD, "new.txt"))
        val uri = TEST_CONTAINER + "new.txt"

        repo.enqueueDelete(TEST_WEB_ID, uri, isContainer = false)

        assertNull("resource row cancelled", resourceDao.findByIdentifier(TEST_WEB_ID, uri))
        assertNull("blob cancelled", blobDao.find(TEST_WEB_ID, uri))
        assertNull("upload op cancelled and no delete op queued", pending())
    }

    @Test
    fun drainDelete_notFound_isTreatedAsSuccess() = runTest {
        resourceDao.upsert(resourceEntity("gone.txt", syncState = SyncState.SYNCED))
        val uri = TEST_CONTAINER + "gone.txt"
        repo.enqueueDelete(TEST_WEB_ID, uri, isContainer = false)
        coEvery { resourceManager.delete(any(), any()) } returns SolidResult.Failure(SolidError.fromHttp(404, "Not Found"))

        repo.drain()

        assertNull(resourceDao.findByIdentifier(TEST_WEB_ID, uri))
        assertEquals(0, outboxDao.countUnfinished())
    }

    @Test
    fun drainDelete_terminalError_marksResourceErroredSoItReappears() = runTest {
        resourceDao.upsert(resourceEntity("locked.txt", syncState = SyncState.SYNCED))
        val uri = TEST_CONTAINER + "locked.txt"
        repo.enqueueDelete(TEST_WEB_ID, uri, isContainer = false)
        coEvery { resourceManager.delete(any(), any()) } returns SolidResult.Failure(SolidError.fromHttp(403, "Forbidden"))

        repo.drain()

        assertEquals(SyncState.ERROR, resourceDao.findByIdentifier(TEST_WEB_ID, uri)!!.syncState)
    }

    // ---- duplicate ----

    @Test
    fun enqueueDuplicate_writesCopyOptimisticRowAndCopyOp() = runTest {
        repo.enqueueDuplicate(TEST_WEB_ID, item("report.pdf"))

        val copyUri = TEST_CONTAINER + "report_copy.pdf"
        assertEquals(SyncState.PENDING_CREATE, resourceDao.findByIdentifier(TEST_WEB_ID, copyUri)!!.syncState)
        val op = pending()!!
        assertEquals(OpType.COPY, op.type)
        assertEquals(TEST_CONTAINER + "report.pdf", op.sourceUri)
    }

    @Test
    fun drainDuplicate_runsRecursiveCopyMakesPrivateAndSyncs() = runTest {
        val copyUri = TEST_CONTAINER + "report_copy.pdf"
        coEvery { fileRepository.duplicateResource(any(), any()) } returns listOf(copyUri)
        repo.enqueueDuplicate(TEST_WEB_ID, item("report.pdf"))

        repo.drain()

        coVerify { fileRepository.duplicateResource(TEST_WEB_ID, any()) }
        coVerify { sharingRepository.makePrivate(TEST_WEB_ID, copyUri) }
        assertEquals(SyncState.SYNCED, resourceDao.findByIdentifier(TEST_WEB_ID, copyUri)!!.syncState)
    }

    // ---- queue lifecycle ----

    @Test
    fun hasUnfinishedWork_reflectsQueueState() = runTest {
        assertFalse(repo.hasUnfinishedWork())
        outboxDao.insert(outboxOp(OpType.UPLOAD, "x"))
        assertTrue(repo.hasUnfinishedWork())
    }

    @Test
    fun clearForWebId_purgesQueue() = runTest {
        outboxDao.insert(outboxOp(OpType.UPLOAD, "x"))

        repo.clearForWebId(TEST_WEB_ID)

        assertEquals(0, outboxDao.countUnfinished())
    }

    // ---- helpers ----

    private suspend fun pending() = outboxDao.nextActionable(Long.MAX_VALUE)

    private fun success(): SolidResult<String?> = SolidResult.Success(null)

    /**
     * A minimal stateful stand-in for the pod: createInContainer records the created resource URI,
     * delete removes it. Returned set is the server's contents, so tests assert real outcomes
     * ("the folder exists on the server") instead of "a mock was called".
     */
    private fun fakeServer(): MutableSet<String> {
        val created = mutableSetOf<String>()
        coEvery { resourceManager.createInContainer(any(), any(), any()) } answers {
            created.add(thirdArg<Resource>().getIdentifier())
            SolidResult.Success(null)
        }
        coEvery { resourceManager.delete(any(), any()) } answers {
            created.remove(secondArg<String>())
            SolidResult.Success(true)
        }
        return created
    }

    private fun source(content: String): Uri {
        val uri = Uri.parse("content://test/${content.hashCode()}")
        shadowOf(context.contentResolver).registerInputStream(uri, content.byteInputStream())
        return uri
    }

    private fun item(name: String): ContainerItem = ContainerItem(
        identifier = TEST_CONTAINER + name,
        isContainer = false,
        name = name,
        extension = name.substringAfterLast('.', ""),
        mimeType = "application/pdf",
        resourceType = ResourceType.PDF,
        resourceTypes = emptyList(),
        sizeBytes = 100,
        lastModified = 1_000,
        etag = null,
    )
}
