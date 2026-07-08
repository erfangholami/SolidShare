package com.erfangholami.solidshare.data.local.cache

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlobDaoTest {

    private lateinit var db: SolidCacheDatabase
    private lateinit var dao: BlobDao

    @Before
    fun setUp() {
        db = inMemoryCacheDb()
        dao = db.blobDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun setPinned_togglesPinFlag() = runTest {
        dao.upsert(blobEntity("a", pinned = false))

        dao.setPinned(TEST_WEB_ID, TEST_CONTAINER + "a", true)

        assertTrue(dao.find(TEST_WEB_ID, TEST_CONTAINER + "a")!!.pinned)
    }

    @Test
    fun touch_updatesLastAccessedAt() = runTest {
        dao.upsert(blobEntity("a", lastAccessedAt = 1))

        dao.touch(TEST_WEB_ID, TEST_CONTAINER + "a", 999)

        assertEquals(999L, dao.find(TEST_WEB_ID, TEST_CONTAINER + "a")!!.lastAccessedAt)
    }

    @Test
    fun observeAvailableOfflineUris_onlyPinnedCompleteBlobs() = runTest {
        dao.upsert(blobEntity("pinned", state = BlobState.COMPLETE, pinned = true))
        dao.upsert(blobEntity("just-cached", state = BlobState.COMPLETE, pinned = false))
        dao.upsert(blobEntity("uploading", state = BlobState.PENDING_UPLOAD, pinned = true))

        // "available offline" = explicitly pinned COMPLETE blobs; an auto-cached (unpinned) file that
        // happens to be on disk is NOT one, so unpinning it must drop it from the set.
        assertEquals(
            setOf(TEST_CONTAINER + "pinned"),
            dao.observeAvailableOfflineUris(TEST_WEB_ID, BlobState.COMPLETE).first().toSet(),
        )
    }

    @Test
    fun unpinnedSize_sumsOnlyUnpinnedComplete() = runTest {
        dao.upsert(blobEntity("u1", pinned = false, sizeBytes = 10, state = BlobState.COMPLETE))
        dao.upsert(blobEntity("pinned", pinned = true, sizeBytes = 20, state = BlobState.COMPLETE))
        dao.upsert(blobEntity("u2", pinned = false, sizeBytes = 30, state = BlobState.COMPLETE))
        dao.upsert(blobEntity("pending", pinned = false, sizeBytes = 40, state = BlobState.PENDING_UPLOAD))

        assertEquals(40L, dao.unpinnedSize(BlobState.COMPLETE))
    }

    @Test
    fun unpinnedByAge_ordersOldestFirst_excludesPinnedAndPending() = runTest {
        dao.upsert(blobEntity("newer", pinned = false, lastAccessedAt = 200))
        dao.upsert(blobEntity("older", pinned = false, lastAccessedAt = 100))
        dao.upsert(blobEntity("pinned", pinned = true, lastAccessedAt = 1))
        dao.upsert(blobEntity("pending", pinned = false, lastAccessedAt = 1, state = BlobState.PENDING_UPLOAD))

        assertEquals(
            listOf(TEST_CONTAINER + "older", TEST_CONTAINER + "newer"),
            dao.unpinnedByAge(BlobState.COMPLETE).map { it.uri },
        )
    }

    @Test
    fun purgeRowsForWebId_scopedToUser() = runTest {
        val other = "https://ben.example/profile/card#me"
        dao.upsert(blobEntity("mine"))
        dao.upsert(blobEntity("theirs", webId = other))

        dao.purgeRowsForWebId(TEST_WEB_ID)

        assertNull(dao.find(TEST_WEB_ID, TEST_CONTAINER + "mine"))
        assertFalse(dao.forWebId(other).isEmpty())
    }
}
