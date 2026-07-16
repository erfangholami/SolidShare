package com.erfangholami.solidshare.data.repo.tickets

import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketEventInfo
import com.erfangholami.solidshare.domain.model.TicketJourney
import com.erfangholami.solidshare.domain.model.TicketStop
import com.erfangholami.solidshare.domain.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class WithDerivedEventStartTest {

    private fun journey(departure: String?) = TicketJourney(
        mode = TransportMode.TRAIN,
        from = TicketStop(code = "AMS", time = departure),
        to = TicketStop(code = "PAR"),
    )

    @Test
    fun `fills a blank event start from the journey departure`() {
        val draft = TicketDraft(title = "Trip", journey = journey("2026-08-01T09:30+02:00"))
        assertEquals("2026-08-01T09:30+02:00", draft.withDerivedEventStart().event?.start)
    }

    @Test
    fun `keeps an explicit event start`() {
        val draft = TicketDraft(
            title = "Trip",
            event = TicketEventInfo(start = "2026-08-02"),
            journey = journey("2026-08-01T09:30+02:00"),
        )
        assertEquals("2026-08-02", draft.withDerivedEventStart().event?.start)
    }

    @Test
    fun `preserves other event fields when deriving`() {
        val draft = TicketDraft(
            title = "Trip",
            event = TicketEventInfo(name = "Amsterdam to Paris"),
            journey = journey("2026-08-01"),
        )
        val derived = draft.withDerivedEventStart()
        assertEquals("Amsterdam to Paris", derived.event?.name)
        assertEquals("2026-08-01", derived.event?.start)
    }

    @Test
    fun `is a no-op without a journey departure`() {
        val noJourney = TicketDraft(title = "Concert")
        assertSame(noJourney, noJourney.withDerivedEventStart())

        val noTime = TicketDraft(title = "Trip", journey = journey(null))
        assertSame(noTime, noTime.withDerivedEventStart())
        assertNull(noTime.withDerivedEventStart().event)
    }
}
