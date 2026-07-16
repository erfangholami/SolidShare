package com.erfangholami.solidshare.data.passimport

import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketSource
import com.erfangholami.solidshare.domain.model.TransportMode
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BcbpParserTest {

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
    fun `decodes a Format-M boarding pass into a flight journey`() {
        val draft = BcbpParser.parse(boardingPass(), reference = LocalDate.of(2026, 1, 1))!!

        assertEquals("AMS → JFK", draft.title)
        assertEquals(TicketCategory.FLIGHT, draft.category)
        assertEquals(TicketSource.BCBP, draft.source)
        assertEquals("ABC123", draft.number)
        assertEquals("Erfan Gholami", draft.holder)
        assertEquals("27A", draft.seat?.number)

        val journey = draft.journey!!
        assertEquals(TransportMode.FLIGHT, journey.mode)
        assertEquals("KL", journey.carrier)
        assertEquals("KL641", journey.serviceNumber)
        assertEquals("AMS", journey.from?.code)
        assertEquals("JFK", journey.to?.code)
        assertEquals("2026-09-01", journey.from?.time)
        assertEquals("2026-09-01", draft.event?.start)
    }

    @Test
    fun `an ambiguous Julian day resolves to the next upcoming flight`() {
        val draft = BcbpParser.parse(boardingPass(), reference = LocalDate.of(2026, 10, 1))!!
        assertEquals("2027-09-01", draft.journey?.from?.time)
    }

    @Test
    fun `rejects non-boarding-pass payloads`() {
        assertNull(BcbpParser.parse("https://solidshare.app/t#abc"))
        assertNull(BcbpParser.parse("just some qr text"))
        assertNull(BcbpParser.parse(""))
    }
}
