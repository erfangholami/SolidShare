package com.erfangholami.solidshare.data.passimport

import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketSeatInfo
import com.erfangholami.solidshare.domain.model.TransportMode
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PkpassParserTest {

    private fun pkpass(passJson: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("pass.json"))
            zip.write(passJson.toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    @Test
    fun `semantics win over display fields for an event ticket`() {
        val draft = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Generic fallback description",
                  "organizationName": "Ticketmaster",
                  "serialNumber": "SER-1",
                  "eventTicket": { "primaryFields": [{ "key": "e", "value": "Fallback Name" }] },
                  "barcodes": [{ "format": "PKBarcodeFormatAztec", "message": "TOK" }],
                  "semantics": {
                    "eventName": "Coldplay — Music of the Spheres",
                    "venueName": "Johan Cruijff ArenA",
                    "venueRoom": "Main Stage",
                    "eventStartDate": "2026-07-14T19:30:00Z",
                    "eventEndDate": "2026-07-14T23:00:00Z",
                    "seats": [{ "seatSection": "B12", "seatRow": "F", "seatNumber": "27" }],
                    "passengerName": { "givenName": "Erfan", "familyName": "Gholami" },
                    "confirmationNumber": "PNR7X2Q",
                    "totalPrice": { "amount": "89.50", "currencyCode": "EUR" }
                  }
                }
                """.trimIndent(),
            ),
        )!!

        assertEquals("Coldplay — Music of the Spheres", draft.title)
        assertEquals(TicketCategory.EVENT, draft.category)
        assertEquals("TOK", draft.token)
        assertEquals(TicketBarcodeFormat.AZTEC, draft.barcodeFormat)
        assertEquals("Coldplay — Music of the Spheres", draft.event?.name)
        assertEquals("2026-07-14T19:30:00Z", draft.event?.start)
        assertEquals("2026-07-14T23:00:00Z", draft.event?.end)
        assertEquals("Johan Cruijff ArenA · Main Stage", draft.event?.venue?.name)
        assertEquals(TicketSeatInfo("27", "F", "B12"), draft.seat)
        assertEquals("Erfan Gholami", draft.holder)
        assertEquals("PNR7X2Q", draft.number)
        assertEquals("89.50", draft.price)
        assertEquals("EUR", draft.currency)
    }

    @Test
    fun `a boarding pass builds a journey from flight semantics`() {
        val draft = PkpassParser.parse(
            pkpass(
                """
                {
                  "organizationName": "KLM",
                  "boardingPass": { "transitType": "PKTransitTypeAir" },
                  "barcodes": [{ "format": "PKBarcodeFormatPDF417", "message": "BP" }],
                  "semantics": {
                    "departureAirportCode": "AMS",
                    "departureGate": "D57",
                    "currentDepartureDate": "2026-09-01T10:20:00+02:00",
                    "destinationAirportCode": "JFK",
                    "destinationTerminal": "4",
                    "currentArrivalDate": "2026-09-01T13:40:00-04:00",
                    "flightCode": "KL641",
                    "transitProvider": "KLM",
                    "passengerName": { "givenName": "A", "familyName": "B" }
                  }
                }
                """.trimIndent(),
            ),
        )!!

        assertEquals("AMS → JFK", draft.title)
        assertEquals(TicketCategory.FLIGHT, draft.category)
        assertEquals("KLM", draft.issuer)
        assertEquals("A B", draft.holder)

        val journey = draft.journey!!
        assertEquals(TransportMode.FLIGHT, journey.mode)
        assertEquals("KLM", journey.carrier)
        assertEquals("KL641", journey.serviceNumber)
        assertEquals("AMS", journey.from?.code)
        assertEquals("D57", journey.from?.gate)
        assertEquals("2026-09-01T10:20:00+02:00", journey.from?.time)
        assertEquals("JFK", journey.to?.code)
        assertEquals("4", journey.to?.terminal)
    }

    @Test
    fun `without semantics it falls back to the display-field heuristics`() {
        val draft = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "My Event",
                  "organizationName": "Org",
                  "serialNumber": "S1",
                  "eventTicket": {
                    "primaryFields": [{ "key": "e", "label": "Event", "value": "Primary Event" }],
                    "auxiliaryFields": [
                      { "key": "seat", "label": "Seat", "value": "12" },
                      { "key": "row", "value": "F" }
                    ]
                  },
                  "barcodes": [{ "format": "PKBarcodeFormatQR", "message": "Q" }]
                }
                """.trimIndent(),
            ),
        )!!

        assertEquals("My Event", draft.title)
        assertEquals(TicketCategory.EVENT, draft.category)
        assertEquals("Primary Event", draft.event?.name)
        assertEquals("12", draft.seat?.number)
        assertEquals("F", draft.seat?.row)
        assertEquals("S1", draft.number)
        assertNull(draft.holder)
        assertNull(draft.price)
    }
}
