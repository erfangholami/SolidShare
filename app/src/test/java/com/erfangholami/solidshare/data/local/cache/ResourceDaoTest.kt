package com.erfangholami.solidshare.data.local.cache

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResourceDaoTest {

    private lateinit var db: SolidCacheDatabase
    private lateinit var dao: ResourceDao

    @Before
    fun setUp() {
        db = inMemoryCacheDb()
        dao = db.resourceDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun replaceContainer_removesMissingSynced_butPreservesPendingRows() = runTest {
        dao.upsertAll(
            listOf(
                resourceEntity("a", syncState = SyncState.SYNCED),
                resourceEntity("b", syncState = SyncState.SYNCED),
                resourceEntity("c", syncState = SyncState.PENDING_CREATE),
                resourceEntity("d", syncState = SyncState.PENDING_DELETE),
            ),
        )

        // Server truth only lists "a" — a refresh must not wipe local pending work.
        dao.replaceContainer(TEST_WEB_ID, TEST_CONTAINER, listOf(resourceEntity("a")))

        // getContainer excludes PENDING_DELETE; "b" (synced, missing) was removed; "c" (pending) kept.
        assertEquals(listOf("a", "c"), dao.getContainer(TEST_WEB_ID, TEST_CONTAINER).map { it.name })
        // "d" (pending delete) is still in the table, just hidden from listings.
        assertNotNull(dao.findByIdentifier(TEST_WEB_ID, TEST_CONTAINER + "d"))
        // "b" is gone entirely.
        assertNull(dao.findByIdentifier(TEST_WEB_ID, TEST_CONTAINER + "b"))
    }

    @Test
    fun replaceContainer_emptyServerList_removesSyncedButKeepsPendingCreate() = runTest {
        dao.upsertAll(
            listOf(
                resourceEntity("a", syncState = SyncState.SYNCED),
                resourceEntity("c", syncState = SyncState.PENDING_CREATE),
            ),
        )

        dao.replaceContainer(TEST_WEB_ID, TEST_CONTAINER, emptyList())

        assertEquals(listOf("c"), dao.getContainer(TEST_WEB_ID, TEST_CONTAINER).map { it.name })
    }

    @Test
    fun replaceContainer_doesNotClobberPendingRowThatServerStillLists() = runTest {
        // A file deleted offline (PENDING_DELETE) that the server hasn't dropped yet.
        dao.upsert(resourceEntity("gone.txt", syncState = SyncState.PENDING_DELETE, etag = "old"))

        // A refresh whose server truth still includes it must NOT resurrect it to SYNCED.
        dao.replaceContainer(
            TEST_WEB_ID,
            TEST_CONTAINER,
            listOf(resourceEntity("gone.txt", syncState = SyncState.SYNCED, etag = "new")),
        )

        val row = dao.findByIdentifier(TEST_WEB_ID, TEST_CONTAINER + "gone.txt")!!
        assertEquals("local pending delete must survive the refresh", SyncState.PENDING_DELETE, row.syncState)
        assertEquals("server row must not overwrite the pending one", "old", row.etag)
        assertTrue(
            "the deleted file must stay hidden from listings",
            dao.getContainer(TEST_WEB_ID, TEST_CONTAINER).none { it.name == "gone.txt" },
        )
    }

    @Test
    fun observeContainer_excludesPendingDelete() = runTest {
        dao.upsertAll(
            listOf(
                resourceEntity("a", syncState = SyncState.SYNCED),
                resourceEntity("gone", syncState = SyncState.PENDING_DELETE),
            ),
        )

        assertEquals(listOf("a"), dao.observeContainer(TEST_WEB_ID, TEST_CONTAINER).first().map { it.name })
    }

    @Test
    fun observeContainer_ordersFoldersFirstThenNameCaseInsensitive() = runTest {
        dao.upsertAll(
            listOf(
                resourceEntity("Zebra"),
                resourceEntity("photos", isContainer = true),
                resourceEntity("apple"),
            ),
        )

        assertEquals(
            listOf("photos", "apple", "Zebra"),
            dao.observeContainer(TEST_WEB_ID, TEST_CONTAINER).first().map { it.name },
        )
    }

    @Test
    fun observePendingUris_returnsAllPendingStates() = runTest {
        dao.upsertAll(
            listOf(
                resourceEntity("synced", syncState = SyncState.SYNCED),
                resourceEntity("create", syncState = SyncState.PENDING_CREATE),
                resourceEntity("update", syncState = SyncState.PENDING_UPDATE),
                resourceEntity("delete", syncState = SyncState.PENDING_DELETE),
                resourceEntity("error", syncState = SyncState.ERROR),
            ),
        )

        assertEquals(
            setOf(TEST_CONTAINER + "create", TEST_CONTAINER + "update", TEST_CONTAINER + "delete"),
            dao.observePendingUris(TEST_WEB_ID).first().toSet(),
        )
    }

    @Test
    fun observeErrorUris_returnsErrorAndConflict() = runTest {
        dao.upsertAll(
            listOf(
                resourceEntity("ok", syncState = SyncState.SYNCED),
                resourceEntity("err", syncState = SyncState.ERROR),
                resourceEntity("conflict", syncState = SyncState.CONFLICT),
            ),
        )

        assertEquals(
            setOf(TEST_CONTAINER + "err", TEST_CONTAINER + "conflict"),
            dao.observeErrorUris(TEST_WEB_ID).first().toSet(),
        )
    }

    @Test
    fun updateSyncState_changesState() = runTest {
        dao.upsert(resourceEntity("a", syncState = SyncState.PENDING_CREATE))

        dao.updateSyncState(TEST_WEB_ID, TEST_CONTAINER + "a", SyncState.SYNCED)

        assertEquals(SyncState.SYNCED, dao.findByIdentifier(TEST_WEB_ID, TEST_CONTAINER + "a")!!.syncState)
    }

    @Test
    fun deleteByIdentifier_removesOneRow() = runTest {
        dao.upsertAll(listOf(resourceEntity("a"), resourceEntity("b")))

        dao.deleteByIdentifier(TEST_WEB_ID, TEST_CONTAINER + "a")

        assertNull(dao.findByIdentifier(TEST_WEB_ID, TEST_CONTAINER + "a"))
        assertNotNull(dao.findByIdentifier(TEST_WEB_ID, TEST_CONTAINER + "b"))
    }

    @Test
    fun lastCachedAt_returnsMaxTimestamp() = runTest {
        dao.upsertAll(
            listOf(
                resourceEntity("a", cachedAt = 100),
                resourceEntity("b", cachedAt = 300),
                resourceEntity("c", cachedAt = 200),
            ),
        )

        assertEquals(300L, dao.lastCachedAt(TEST_WEB_ID, TEST_CONTAINER))
    }

    @Test
    fun purgeForWebId_onlyRemovesThatUsersRows() = runTest {
        val other = "https://ben.example/profile/card#me"
        dao.upsert(resourceEntity("mine"))
        dao.upsert(resourceEntity("theirs", webId = other))

        dao.purgeForWebId(TEST_WEB_ID)

        assertNull(dao.findByIdentifier(TEST_WEB_ID, TEST_CONTAINER + "mine"))
        assertNotNull(dao.findByIdentifier(other, TEST_CONTAINER + "theirs"))
    }
}
