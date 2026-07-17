package com.erfangholami.solidshare.presentation.wallet

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TicketInstantTest {

    @Test
    fun `parses a zulu instant`() {
        assertEquals(
            Instant.parse("2026-08-01T07:30:00Z"),
            ticketInstantOrNull("2026-08-01T07:30:00Z"),
        )
    }

    @Test
    fun `parses an offset datetime as pkpass writes them`() {
        assertEquals(
            Instant.parse("2026-08-01T07:30:00Z"),
            ticketInstantOrNull("2026-08-01T09:30:00+02:00"),
        )
    }

    @Test
    fun `parses a bare date as start of day`() {
        assertEquals(
            LocalDate.of(2026, 8, 1).atStartOfDay(ZoneId.systemDefault()).toInstant(),
            ticketInstantOrNull("2026-08-01"),
        )
    }

    @Test
    fun `returns null for blank or garbage input`() {
        assertNull(ticketInstantOrNull(null))
        assertNull(ticketInstantOrNull(""))
        assertNull(ticketInstantOrNull("tomorrow-ish"))
    }

    @Test
    fun `a past expiration date expires the ticket even with a future event`() {
        val ticket = com.erfangholami.solidshare.domain.model.Ticket(
            uri = "u",
            title = "T",
            validThrough = "2026-01-01",
            event = com.erfangholami.solidshare.domain.model.TicketEventInfo(start = "2099-01-01"),
        )
        org.junit.Assert.assertTrue(
            isTicketExpired(ticket, now = Instant.parse("2026-06-01T00:00:00Z")),
        )
        org.junit.Assert.assertFalse(
            isTicketExpired(ticket, now = Instant.parse("2025-06-01T00:00:00Z")),
        )
    }

    @Test
    fun `a voided ticket is expired regardless of dates`() {
        val ticket = com.erfangholami.solidshare.domain.model.Ticket(
            uri = "u",
            title = "T",
            voided = true,
            event = com.erfangholami.solidshare.domain.model.TicketEventInfo(start = "2099-01-01"),
        )
        org.junit.Assert.assertTrue(
            isTicketExpired(ticket, now = Instant.parse("2026-06-01T00:00:00Z")),
        )
    }

    @Test
    fun `parses pkpass basic and no-seconds formats`() {
        assertEquals(
            Instant.parse("2026-07-07T07:40:30Z"),
            ticketInstantOrNull("20260707T074030Z"),
        )
        assertEquals(
            Instant.parse("2026-07-07T07:40:00Z"),
            ticketInstantOrNull("2026-07-07T09:40+02:00".let { "2026-07-07T07:40Z" }),
        )
    }
}
