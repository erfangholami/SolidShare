package com.erfangholami.solidshare.data.repo.tickets

import com.erfangholami.solidshare.data.repo.datamodule.DataModuleIds
import com.erfangholami.solidshare.data.local.cache.testOutbox
import androidx.work.WorkManager
import com.erfangholami.androidsolidservices.api.datamodule.tickets.SolidTicketsDataModule
import com.erfangholami.androidsolidservices.api.datamodule.tickets.TicketStore
import com.erfangholami.androidsolidservices.shared.model.tickets.Ticket as LibTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketList
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketSummary
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.solidshare.data.local.cache.SolidCacheDatabase
import com.erfangholami.solidshare.data.local.cache.SyncState
import com.erfangholami.solidshare.data.local.cache.TicketBlobStore
import com.erfangholami.solidshare.data.local.cache.inMemoryCacheDb
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketDraft
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
class TicketsRepositoryOfflineTest {

    private val webId = "https://alice.pod/profile/card#me"
    private val realUri = "https://alice.pod/tickets/u1/ticket#this"

    private lateinit var db: SolidCacheDatabase
    private lateinit var repo: TicketsRepositoryImplementation

    private val store = mockk<TicketStore>()
    private val module = mockk<SolidTicketsDataModule> { every { tickets } returns store }
    private val auth = mockk<AuthRepository> {
        coEvery { getStorages(webId) } returns listOf("https://alice.pod/")
    }
    private val blobStore = mockk<TicketBlobStore>(relaxed = true) {
        coEvery { read(any(), any(), any()) } returns null
        coEvery { readText(any(), any(), any()) } returns null
        coEvery { has(any(), any(), any()) } returns true
    }

    @Before
    fun setUp() {
        db = inMemoryCacheDb()
        repo = TicketsRepositoryImplementation(
            module,
            auth,
            mockk<SharingRepository>(relaxed = true),
            db.cachedEntityDao(),
            testOutbox(db),
            blobStore,
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `queueCreate shows a provisional ticket and drain replaces it with the server one`() =
        runTest {
            coEvery { store.create(any(), any(), any(), any(), any(), any(), any(), any()) } returns
                SolidResult.Success(LibTicket(uri = realUri, title = "Concert"))

            val provisionalUri = repo.queueCreate(webId, TicketDraft(title = "Concert"))

            val pending = repo.observeTickets(webId).first()
            assertEquals(listOf(provisionalUri), pending.map { it.uri })

            assertTrue(repo.drain(webId))

            val synced = repo.observeTickets(webId).first()
            assertEquals(listOf(realUri), synced.map { it.uri })
            assertTrue(testOutbox(db).pendingWork().isEmpty())
        }

    @Test
    fun `editing a provisional ticket rewrites the queued create`() = runTest {
        var createdTitle: String? = null
        coEvery { store.create(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            createdTitle = arg<com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket>(1).title
            SolidResult.Success(LibTicket(uri = realUri, title = createdTitle.orEmpty()))
        }

        val provisionalUri = repo.queueCreate(webId, TicketDraft(title = "Draft"))
        repo.queueUpdate(webId, provisionalUri, TicketDraft(title = "Final"))

        assertTrue(repo.drain(webId))

        assertEquals("Final", createdTitle)
        coVerify(exactly = 1) { store.create(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `deleting a provisional ticket cancels the queued create`() = runTest {
        val provisionalUri = repo.queueCreate(webId, TicketDraft(title = "Oops"))

        repo.queueDelete(webId, provisionalUri)

        assertTrue(repo.observeTickets(webId).first().isEmpty())
        assertTrue(testOutbox(db).pendingWork().isEmpty())
    }

    @Test
    fun `queueDelete hides the row immediately and drain deletes on the pod`() = runTest {
        db.cachedEntityDao().upsert(sampleTicket(realUri).toCacheEntity(webId, 1L))
        coEvery { store.delete(webId, realUri) } returns
            SolidResult.Success(LibTicket(uri = realUri, title = "T"))

        repo.queueDelete(webId, realUri)
        assertTrue(repo.observeTickets(webId).first().isEmpty())

        assertTrue(repo.drain(webId))
        assertNull(db.cachedEntityDao().findByUri(DataModuleIds.TICKETS, webId, realUri))
        coVerify { store.delete(webId, realUri) }
    }

    @Test
    fun `refresh prunes stale synced rows but keeps pending work`() = runTest {
        val staleUri = "https://alice.pod/tickets/old/ticket#this"
        db.cachedEntityDao().upsert(sampleTicket(staleUri).toCacheEntity(webId, 1L))
        val provisionalUri = repo.queueCreate(webId, TicketDraft(title = "Pending"))

        coEvery { store.list(webId) } returns SolidResult.Success(
            TicketList(listOf(TicketSummary(uri = realUri, title = "Fresh"))),
        )
        coEvery { store.get(webId, realUri) } returns
            SolidResult.Success(LibTicket(uri = realUri, title = "Fresh"))

        repo.refreshTickets(webId)

        val uris = repo.observeTickets(webId).first().map { it.uri }
        assertEquals(setOf(realUri, provisionalUri), uris.toSet())
        assertNull(db.cachedEntityDao().findByUri(DataModuleIds.TICKETS, webId, staleUri))
    }

    @Test
    fun `getTicket serves the cache without a network call`() = runTest {
        db.cachedEntityDao().upsert(sampleTicket(realUri).toCacheEntity(webId, 1L))

        val ticket = repo.getTicket(webId, realUri)

        assertNotNull(ticket)
        assertEquals(realUri, ticket.uri)
        coVerify(exactly = 0) { store.get(any(), any()) }
    }

    private fun sampleTicket(uri: String): Ticket = Ticket(uri = uri, title = "T")
}
