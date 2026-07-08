package com.erfangholami.solidshare.data.local.cache

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OutboxDaoTest {

    private lateinit var db: SolidCacheDatabase
    private lateinit var dao: OutboxDao

    @Before
    fun setUp() {
        db = inMemoryCacheDb()
        dao = db.outboxDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun nextActionable_prefersEarliestPending() = runTest {
        dao.insert(outboxOp(OpType.UPLOAD, "second", createdAt = 20))
        dao.insert(outboxOp(OpType.UPLOAD, "first", createdAt = 10))

        assertEquals("first", dao.nextActionable(NOW)!!.name)
    }

    @Test
    fun nextActionable_skipsFailedWithFutureRetry_butReturnsDueRetry() = runTest {
        dao.insert(outboxOp(OpType.DELETE, "future", status = OpStatus.FAILED, nextRetryAt = NOW + 10_000, createdAt = 1))
        dao.insert(outboxOp(OpType.DELETE, "due", status = OpStatus.FAILED, nextRetryAt = NOW - 1, createdAt = 2))

        assertEquals("due", dao.nextActionable(NOW)!!.name)
    }

    @Test
    fun nextActionable_ignoresErrorAndInFlight() = runTest {
        dao.insert(outboxOp(OpType.UPLOAD, "errored", status = OpStatus.ERROR, createdAt = 1))
        dao.insert(outboxOp(OpType.UPLOAD, "inflight", status = OpStatus.IN_FLIGHT, createdAt = 2))

        assertNull(dao.nextActionable(NOW))
    }

    @Test
    fun resetInFlight_movesInFlightBackToPending() = runTest {
        val id = dao.insert(outboxOp(OpType.UPLOAD, "x", status = OpStatus.IN_FLIGHT, createdAt = 1))

        dao.resetInFlight()

        assertEquals("x", dao.nextActionable(NOW)!!.name)
        assertEquals(id, dao.nextActionable(NOW)!!.id)
    }

    @Test
    fun setRetry_updatesStatusAttemptsAndBackoff() = runTest {
        val id = dao.insert(outboxOp(OpType.UPLOAD, "x", createdAt = 1))

        dao.setRetry(id, OpStatus.FAILED, attempts = 3, nextRetryAt = NOW + 5_000, error = "boom", now = NOW)

        // Not due yet, so not actionable.
        assertNull(dao.nextActionable(NOW))
        // Becomes actionable once the retry time passes.
        assertEquals("x", dao.nextActionable(NOW + 6_000)!!.name)
    }

    @Test
    fun countUnfinished_excludesErrorRows() = runTest {
        dao.insert(outboxOp(OpType.UPLOAD, "p", status = OpStatus.PENDING, createdAt = 1))
        dao.insert(outboxOp(OpType.UPLOAD, "f", status = OpStatus.FAILED, createdAt = 2))
        dao.insert(outboxOp(OpType.UPLOAD, "e", status = OpStatus.ERROR, createdAt = 3))

        assertEquals(2, dao.countUnfinished())
    }

    @Test
    fun deleteByTarget_removesMatchingOps() = runTest {
        dao.insert(outboxOp(OpType.UPLOAD, "target", createdAt = 1))
        dao.insert(outboxOp(OpType.UPLOAD, "keep", createdAt = 2))

        dao.deleteByTarget(TEST_WEB_ID, TEST_CONTAINER + "target")

        assertEquals("keep", dao.nextActionable(NOW)!!.name)
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
