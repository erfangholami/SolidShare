package com.erfangholami.solidshare.data.repo.tickets

import com.erfangholami.solidshare.data.local.cache.testOutbox
import androidx.work.WorkManager
import com.erfangholami.androidsolidservices.api.datamodule.tickets.SolidTicketsDataModule
import com.erfangholami.androidsolidservices.api.datamodule.tickets.TicketStore
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicketImages
import com.erfangholami.androidsolidservices.shared.model.tickets.Ticket as LibTicket
import com.erfangholami.androidsolidservices.shared.model.tickets.TicketArtifact
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.solidshare.data.local.cache.TicketBlobStore
import com.erfangholami.solidshare.data.local.cache.CachedEntityDao
import com.erfangholami.solidshare.data.repo.outbox.ModuleOutbox
import com.erfangholami.solidshare.data.passimport.PkpassParser
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketFile
import com.erfangholami.solidshare.domain.model.TicketImageUris
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TicketsRepositoryImagesTest {

    private val webId = "https://alice.pod/profile/card#me"
    private val ticketDir = "https://alice.pod/tickets/u1/"
    private val ticketUri = "${ticketDir}ticket#this"

    private val store = mockk<TicketStore>()
    private val module = mockk<SolidTicketsDataModule> { every { tickets } returns store }
    private val auth = mockk<AuthRepository> {
        coEvery { getStorages(webId) } returns listOf("https://alice.pod/")
    }
    private val blobStore = mockk<TicketBlobStore>(relaxed = true) {
        coEvery { read(any(), any(), any()) } returns null
        coEvery { readText(any(), any(), any()) } returns null
        coEvery { has(any(), any(), any()) } returns false
    }
    private val repo = TicketsRepositoryImplementation(
        module,
        auth,
        mockk<SharingRepository>(relaxed = true),
        mockk<CachedEntityDao>(relaxed = true),
        mockk<ModuleOutbox>(relaxed = true),
        blobStore,
    )

    private val libTicket = LibTicket(uri = ticketUri, title = "T")

    private fun pkpassWith(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun captureCreateImages(): () -> NewTicketImages? {
        var captured: NewTicketImages? = null
        coEvery {
            store.create(any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            captured = arg(5)
            SolidResult.Success(libTicket)
        }
        return { captured }
    }

    @Test
    fun `create extracts the pass images from the pkpass artifact and hands them to the library`() =
        runBlocking {
            val captured = captureCreateImages()
            val bytes = pkpassWith(
                "logo.png" to byteArrayOf(1),
                "icon.png" to byteArrayOf(2),
                "strip@2x.png" to byteArrayOf(3),
            )

            repo.createTicket(webId, TicketDraft(title = "T"), TicketFile(PkpassParser.MIME_TYPE, bytes))

            val images = captured()
            assertArrayEquals(byteArrayOf(1), images?.logo)
            assertArrayEquals(byteArrayOf(2), images?.icon)
            assertArrayEquals(byteArrayOf(3), images?.strip)
            assertNull(images?.thumbnail)
            assertNull(images?.footer)
            assertNull(images?.background)
        }

    @Test
    fun `create passes no images for a non-pkpass artifact`() = runBlocking {
        val captured = captureCreateImages()

        repo.createTicket(webId, TicketDraft(title = "T"), TicketFile("application/pdf", byteArrayOf(9)))

        assertNull(captured())
    }

    @Test
    fun `getTicketImages downloads each stored role into a PassImages`() = runBlocking {
        coEvery { store.getArtifact(webId, "${ticketDir}logo.png") } returns
            SolidResult.Success(TicketArtifact("${ticketDir}logo.png", "image/png", byteArrayOf(1)))
        coEvery { store.getArtifact(webId, "${ticketDir}strip.png") } returns
            SolidResult.Success(TicketArtifact("${ticketDir}strip.png", "image/png", byteArrayOf(3)))

        val images = repo.getTicketImages(
            webId,
            ticketUri,
            TicketImageUris(logo = "${ticketDir}logo.png", strip = "${ticketDir}strip.png"),
        )

        assertArrayEquals(byteArrayOf(1), images?.logo)
        assertArrayEquals(byteArrayOf(3), images?.strip)
        assertNull(images?.icon)
        assertNull(images?.background)
    }

    @Test
    fun `getTicketImages serves cached blobs without touching the network`() = runBlocking {
        coEvery { blobStore.read(webId, ticketUri, TicketBlobStore.LOGO) } returns byteArrayOf(7)

        val images = repo.getTicketImages(webId, ticketUri, null)

        assertArrayEquals(byteArrayOf(7), images?.logo)
        assertNull(images?.strip)
    }

    @Test
    fun `getTicketImages returns null when nothing can be fetched`() = runBlocking {
        coEvery { store.getArtifact(any(), any()) } throws RuntimeException("gone")

        assertNull(
            repo.getTicketImages(
                webId,
                ticketUri,
                TicketImageUris(logo = "${ticketDir}logo.png"),
            ),
        )
    }
}
