package com.erfangholami.solidshare.presentation.container

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.solidshare.data.local.cache.BlobDao
import com.erfangholami.solidshare.data.local.cache.CacheKeyManager
import com.erfangholami.solidshare.data.local.cache.ResourceDao
import com.erfangholami.solidshare.data.local.cache.SolidCacheDatabase
import androidx.room.Room
import com.erfangholami.solidshare.data.local.cache.SyncState
import com.erfangholami.solidshare.data.local.cache.TEST_CONTAINER
import com.erfangholami.solidshare.data.local.cache.TEST_WEB_ID
import com.erfangholami.solidshare.data.local.cache.resourceEntity
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.file.FileRepositoryImplementation
import com.erfangholami.solidshare.data.repo.outbox.OutboxRepository
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.util.NetworkMonitor
import com.erfangholami.solidshare.util.StringProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Regression tests for the reported bug: a folder created while offline vanished from the list once
 * the device came back online. The root cause was in load() — it displayed the raw server listing,
 * which does not yet include the still-pending folder. These tests assert the *behaviour the user
 * expects* (pending stays visible, genuinely-removed items disappear), not the code that produces it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContainerViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dispatcher = StandardTestDispatcher()

    private lateinit var db: SolidCacheDatabase
    private lateinit var resourceDao: ResourceDao
    private lateinit var blobDao: BlobDao
    private lateinit var fileRepository: FileRepositoryImplementation
    private lateinit var authRepository: AuthRepository
    private lateinit var outboxRepository: OutboxRepository
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var stringProvider: StringProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Same-thread executors so Room's suspend DAO calls run synchronously on the test dispatcher;
        // otherwise advanceUntilIdle() returns while load() is still parked on Room's own executor.
        db = Room.inMemoryDatabaseBuilder(context, SolidCacheDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        resourceDao = db.resourceDao()
        blobDao = db.blobDao()

        val keyManager = mockk<CacheKeyManager>()
        every { keyManager.encryptStream(any(), any()) } answers {
            firstArg<InputStream>().use { input -> secondArg<File>().outputStream().use { input.copyTo(it) } }
        }
        every { keyManager.decryptStream(any(), any()) } answers {
            firstArg<File>().inputStream().use { it.copyTo(secondArg<OutputStream>()) }
        }
        networkMonitor = mockk(relaxed = true)
        every { networkMonitor.isOnline } returns MutableStateFlow(true)
        every { networkMonitor.currentlyOnline() } returns true

        // Real repository over an in-memory DB, so the cache methods behave exactly as in production;
        // only the network listing (getContainerContents) is scripted per test.
        val resourceManager = mockk<SolidResourceManager>(relaxed = true)
        fileRepository = spyk(
            FileRepositoryImplementation(
                context = context,
                resourceManager = resourceManager,
                resourceDao = resourceDao,
                blobDao = blobDao,
                keyManager = keyManager,
                networkMonitor = networkMonitor,
            ),
        )
        coEvery { fileRepository.getContainerItemCount(any(), any()) } returns 0

        authRepository = mockk(relaxed = true)
        every { authRepository.activeWebIdFlow } returns MutableStateFlow(TEST_WEB_ID)
        coEvery { authRepository.getActiveWebId() } returns TEST_WEB_ID

        outboxRepository = mockk(relaxed = true)
        stringProvider = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun buildViewModel(): ContainerViewModel = ContainerViewModel(
        stringProvider = stringProvider,
        savedStateHandle = SavedStateHandle(mapOf("containerUrl" to TEST_CONTAINER, "shared" to false)),
        workManager = mockk(relaxed = true),
        authRepository = authRepository,
        fileRepository = fileRepository,
        networkMonitor = networkMonitor,
        outboxRepository = outboxRepository,
        errors = mockk(relaxed = true),
    )

    private fun shownItems(vm: ContainerViewModel): List<ContainerItem> =
        (vm.uiState.value as ContainerViewModel.UiState.Success).items

    @Test
    fun offlineCreatedFolder_staysVisibleAfterOnlineRefresh() = runTest(dispatcher) {
        // Given a folder created while offline (queued, not yet on the server)
        resourceDao.upsert(resourceEntity("Docs", isContainer = true, syncState = SyncState.PENDING_CREATE))
        // And the server does not list it yet (the outbox drain hasn't run)
        coEvery { fileRepository.getContainerContents(any(), any(), any()) } returns emptyList()

        val vm = buildViewModel()
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()

        assertTrue(
            "an offline-created folder must remain visible after an online refresh",
            shownItems(vm).any { it.name == "Docs" && it.isContainer },
        )
    }

    @Test
    fun offlineDeletedResource_staysHiddenAfterOnlineRefresh() = runTest(dispatcher) {
        // Given a file deleted while offline (queued delete) that the server still lists
        resourceDao.upsert(resourceEntity("note.txt", syncState = SyncState.PENDING_DELETE))
        coEvery { fileRepository.getContainerContents(any(), any(), any()) } returns
            listOf(serverItem("note.txt"))

        val vm = buildViewModel()
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()

        assertTrue(
            "a file deleted offline must not reappear when the server still lists it",
            shownItems(vm).none { it.name == "note.txt" },
        )
    }

    @Test
    fun syncedItemRemovedByServer_disappearsAfterRefresh() = runTest(dispatcher) {
        // Contrast case: a genuinely-synced item the server no longer lists SHOULD disappear —
        // proving the tests above aren't just "everything always stays".
        resourceDao.upsert(resourceEntity("old.txt", syncState = SyncState.SYNCED))
        coEvery { fileRepository.getContainerContents(any(), any(), any()) } returns emptyList()

        val vm = buildViewModel()
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()

        assertTrue(
            "a synced item the server dropped should be removed on refresh",
            shownItems(vm).none { it.name == "old.txt" },
        )
    }

    private fun serverItem(name: String): ContainerItem = ContainerItem(
        identifier = TEST_CONTAINER + name,
        isContainer = false,
        name = name,
        extension = "txt",
        mimeType = "text/plain",
        resourceType = com.erfangholami.solidshare.domain.model.ResourceType.OTHERS,
        resourceTypes = emptyList(),
        sizeBytes = 5,
        lastModified = 1,
        etag = "e",
    )
}
