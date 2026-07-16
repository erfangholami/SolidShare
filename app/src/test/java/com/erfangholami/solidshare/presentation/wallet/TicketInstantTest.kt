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
}
