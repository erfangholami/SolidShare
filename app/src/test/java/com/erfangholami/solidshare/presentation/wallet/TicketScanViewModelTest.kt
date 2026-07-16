package com.erfangholami.solidshare.presentation.wallet

import com.erfangholami.solidshare.data.passimport.mlKitFormatToDomain
import com.erfangholami.solidshare.data.repo.tickets.TicketsRepository
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketSource
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class TicketScanViewModelTest {

    private val repository = mockk<TicketsRepository>()
    private val viewModel = TicketScanViewModel(repository)

    private fun field(value: String, width: Int): String = value.padEnd(width).substring(0, width)

    private fun boardingPass(): String = buildString {
        append("M")
        append("1")
        append(field("GHOLAMI/ERFAN", 20))
        append("E")
        append(field("ABC123", 7))
        append("AMS")
        append("JFK")
        append(field("KL", 3))
        append(field("0641", 5))
        append("244")
        append("Y")
        append(field("027A", 4))
        append(field("00012", 5))
        append("3")
    }

    @Test
    fun `a recognised ticket qr wins`() {
        val codecDraft = TicketDraft(title = "From codec", source = TicketSource.SCAN)
        every { repository.parseTicketQr(any()) } returns codecDraft

        assertEquals(codecDraft, viewModel.draftFrom("anything", 0))
    }

    @Test
    fun `a scanned boarding pass decodes into a flight draft`() {
        every { repository.parseTicketQr(any()) } returns null
        val raw = boardingPass()

        val draft = viewModel.draftFrom(raw, 0)

        assertEquals(TicketCategory.FLIGHT, draft.category)
        assertEquals(TicketSource.BCBP, draft.source)
        assertEquals("AMS", draft.journey?.from?.code)
        assertEquals(raw, draft.token)
        assertEquals(mlKitFormatToDomain(0), draft.barcodeFormat)
    }

    @Test
    fun `anything else falls back to an opaque scan draft`() {
        every { repository.parseTicketQr(any()) } returns null

        val draft = viewModel.draftFrom("  some random code  ", 0)

        assertEquals(TicketSource.SCAN, draft.source)
        assertEquals("some random code", draft.token)
        assertEquals(TicketCategory.GENERIC, draft.category)
    }
}
