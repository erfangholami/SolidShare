package com.erfangholami.solidshare.data.repo.tickets

import com.erfangholami.solidshare.data.repo.datamodule.DataModuleIds
import com.erfangholami.solidshare.data.local.cache.testOutbox
import androidx.work.WorkManager
import com.erfangholami.androidsolidservices.api.datamodule.tickets.SolidTicketsDataModule
import com.erfangholami.androidsolidservices.api.datamodule.tickets.TicketStore
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicketImages
import com.erfangholami.androidsolidservices.shared.model.tickets.Ticket as LibTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketArtifact
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketImages
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.solidshare.data.local.cache.SolidCacheDatabase
import com.erfangholami.solidshare.data.local.cache.TicketBlobStore
import com.erfangholami.solidshare.data.local.cache.inMemoryCacheDb
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.model.Ticket
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedTicketRepositoryTest {

    private val webId = "https://bob.pod/profile/card#me"
    private val sharedContainer = "https://alice.pod/tickets/u1/"
    private val sharedUri = "${sharedContainer}ticket#this"
    private val sharedArtifact = "${sharedContainer}artifact.pkpass"
    private val createdUri = "https://bob.pod/tickets/u9/ticket#this"

    private lateinit var db: SolidCacheDatabase
    private lateinit var repo: TicketsRepositoryImplementation

    private val store = mockk<TicketStore>(relaxed = true) {
        every { shareTarget(any()) } answers {
            val uri = firstArg<String>().substringBefore('#')
            if (uri.endsWith("/ticket")) uri.removeSuffix("ticket") else uri
        }
    }
    private val module = mockk<SolidTicketsDataModule> { every { tickets } returns store }
    private val auth = mockk<AuthRepository> {
        coEvery { getStorages(webId) } returns listOf("https://bob.pod/")
    }
    private val sharing = mockk<SharingRepository>(relaxed = true)

    private val blobs = mutableMapOf<String, ByteArray>()
    private val blobStore = mockk<TicketBlobStore>(relaxed = true) {
        coEvery { write(any(), any(), any(), any()) } answers {
            blobs["${arg<String>(1)}|${arg<String>(2)}"] = arg(3)
        }
        coEvery { read(any(), any(), any()) } answers { blobs["${arg<String>(1)}|${arg<String>(2)}"] }
        coEvery { readText(any(), any(), any()) } returns null
        coEvery { has(any(), any(), any()) } returns true
    }

    @Before
    fun setUp() {
        db = inMemoryCacheDb()
        repo = TicketsRepositoryImplementation(
            module,
            auth,
            sharing,
            db.cachedEntityDao(),
            testOutbox(db),
            blobStore,
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `getSharedTicket resolves a container target without touching the cache`() = runTest {
        coEvery { store.findInContainer(webId, sharedContainer) } returns
            SolidResult.Success(LibTicket(uri = sharedUri, title = "Concert"))

        val ticket = repo.getSharedTicket(webId, sharedContainer)

        assertEquals(sharedUri, ticket.uri)
        assertNull(db.cachedEntityDao().findByUri(DataModuleIds.TICKETS, webId, sharedUri))
        coVerify(exactly = 1) { store.findInContainer(webId, sharedContainer) }
    }

    @Test
    fun `addSharedTicketToWallet copies the pass with its artifact and provenance`() = runTest {
        val shared = Ticket(uri = sharedUri, title = "Concert", artifactUri = sharedArtifact)
        coEvery { store.getArtifact(webId, sharedArtifact) } returns SolidResult.Success(
            TicketArtifact(sharedArtifact, "application/vnd.apple.pkpass", byteArrayOf(1, 2)),
        )
        val created = slot<NewTicket>()
        coEvery {
            store.create(any(), capture(created), any(), any(), any(), any(), any(), any())
        } returns SolidResult.Success(LibTicket(uri = createdUri, title = "Concert"))

        repo.addSharedTicketToWallet(webId, shared)
        assertTrue(repo.drain(webId))

        assertEquals(sharedUri, created.captured.copiedFrom)
        coVerify {
            store.create(
                webId, any(), any(),
                withArg { assertTrue(it.contentEquals(byteArrayOf(1, 2))) },
                "application/vnd.apple.pkpass", any(), any(), any(),
            )
        }
    }

    @Test
    fun `addSharedTicketToWallet without an artifact carries the image bytes through the outbox`() =
        runTest {
            val logoUri = "${sharedContainer}logo.png"
            val shared = Ticket(
                uri = sharedUri,
                title = "Concert",
                images = com.erfangholami.solidshare.domain.model.TicketImageUris(logo = logoUri),
            )
            coEvery { store.getArtifact(webId, logoUri) } returns SolidResult.Success(
                TicketArtifact(logoUri, "image/png", byteArrayOf(7)),
            )
            val images = slot<NewTicketImages>()
            coEvery {
                store.create(any(), any(), any(), null, null, capture(images), any(), any())
            } returns SolidResult.Success(
                LibTicket(
                    uri = createdUri,
                    title = "Concert",
                    images = TicketImages(logo = "https://bob.pod/tickets/u9/logo.png"),
                ),
            )

            repo.addSharedTicketToWallet(webId, shared)
            assertTrue(repo.drain(webId))

            assertTrue(images.captured.logo.contentEquals(byteArrayOf(7)))
        }

    @Test
    fun `getSharedTicketImages derives visuals from the pass file when no image links exist`() =
        runTest {
            val icon = byteArrayOf(0x50, 0x4E, 0x47)
            coEvery { store.getArtifact(webId, sharedArtifact) } returns SolidResult.Success(
                TicketArtifact(
                    sharedArtifact,
                    "application/vnd.apple.pkpass",
                    passFile("Coldplay", "icon.png" to icon),
                ),
            )
            val ticket = Ticket(
                uri = sharedArtifact,
                title = "Coldplay",
                artifactUri = sharedArtifact,
            )

            val visuals = repo.getSharedTicketImages(webId, ticket)

            assertTrue(visuals?.icon.contentEquals(icon))
        }

    private fun passFile(title: String, vararg extras: Pair<String, ByteArray>): ByteArray {
        val passJson = """
            {
              "formatVersion": 1,
              "description": "$title",
              "organizationName": "Preview",
              "passTypeIdentifier": "pass.test",
              "serialNumber": "1",
              "generic": { "primaryFields": [] }
            }
        """.trimIndent().toByteArray()
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("pass.json"))
            zip.write(passJson)
            zip.closeEntry()
            extras.forEach { (name, bytes) ->
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `deleting a ticket purges the share rows of its whole container`() = runTest {
        val ownUri = "https://bob.pod/tickets/u9/ticket#this"
        coEvery { store.delete(webId, ownUri) } returns
            SolidResult.Success(LibTicket(uri = ownUri, title = "Concert"))

        repo.deleteTicket(webId, ownUri)

        coVerify {
            sharing.purgeGivenShares(webId, "https://bob.pod/tickets/u9/", any(), any())
        }
    }

    @Test
    fun `findTicketCopiedFrom finds the copy by provenance`() = runTest {
        db.cachedEntityDao().upsert(
            Ticket(uri = createdUri, title = "Concert", copiedFrom = sharedUri)
                .toCacheEntity(webId, 1L),
        )

        assertEquals(createdUri, repo.findTicketCopiedFrom(webId, sharedUri))
        assertNull(repo.findTicketCopiedFrom(webId, "https://alice.pod/tickets/other/ticket#this"))
    }
}
