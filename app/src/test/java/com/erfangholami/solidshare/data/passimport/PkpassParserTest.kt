package com.erfangholami.solidshare.data.passimport

import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketExtraPlacement
import com.erfangholami.solidshare.domain.model.TicketSeatInfo
import com.erfangholami.solidshare.domain.model.TransportMode
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        )!!.toDraft()

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
    fun `a boarding pass without semantics mines the display fields`() {
        val draft = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Boarding pass",
                  "organizationName": "Transavia",
                  "logoText": "Transavia",
                  "relevantDate": "2026-08-14T09:10+02:00",
                  "boardingPass": {
                    "transitType": "PKTransitTypeAir",
                    "headerFields": [
                      { "key": "gate", "label": "GATE", "value": "D07" },
                      { "key": "seat", "label": "SEAT", "value": "12A" }
                    ],
                    "primaryFields": [
                      { "key": "origin", "label": "Amsterdam", "value": "AMS" },
                      { "key": "destination", "label": "Barcelona", "value": "BCN" }
                    ],
                    "secondaryFields": [
                      { "key": "passenger", "label": "PASSENGER", "value": "Erfan Gholami" },
                      { "key": "flight_number", "label": "FLIGHT", "value": "HV6015" }
                    ],
                    "auxiliaryFields": [
                      { "key": "departure_time", "label": "DEPARTURE", "value": "09:40" },
                      { "key": "boarding_time", "label": "BOARDING", "value": "09:10" },
                      { "key": "class", "label": "CLASS", "value": "Economy" },
                      { "key": "booking_reference", "label": "PNR", "value": "K8Q2ZX" }
                    ],
                    "backFields": [
                      { "key": "terms", "label": "Terms", "value": "Non refundable" }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )!!.toDraft()

        assertEquals(TicketCategory.FLIGHT, draft.category)
        assertEquals("AMS → BCN", draft.title)
        assertNull(draft.description)

        val journey = draft.journey!!
        assertEquals(TransportMode.FLIGHT, journey.mode)
        assertEquals("AMS", journey.from?.code)
        assertEquals("Amsterdam", journey.from?.name)
        assertEquals("BCN", journey.to?.code)
        assertEquals("Barcelona", journey.to?.name)
        assertEquals("09:40", journey.from?.time)
        assertEquals("D07", journey.from?.gate)
        assertEquals("HV6015", journey.serviceNumber)

        assertEquals("12A", draft.seat?.number)
        assertEquals("Erfan Gholami", draft.holder)
        assertEquals("K8Q2ZX", draft.number)
        assertEquals("2026-08-14T09:10+02:00", draft.event?.start)

        val labels = draft.extras.map { it.label }
        assertTrue(labels.contains("BOARDING"))
        assertTrue(labels.contains("CLASS"))
        assertTrue(
            draft.extras.any {
                it.placement == TicketExtraPlacement.BACK && it.value == "Non refundable"
            },
        )
        assertTrue(draft.extras.none { it.value == "D07" })
    }

    @Test
    fun `a boat boarding pass maps to the boat category`() {
        val draft = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Ferry",
                  "organizationName": "Fjord Line",
                  "boardingPass": {
                    "transitType": "PKTransitTypeBoat",
                    "primaryFields": [
                      { "key": "from", "label": "Bergen", "value": "BGO" },
                      { "key": "to", "label": "Stavanger", "value": "SVG" }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )!!.toDraft()

        assertEquals(TicketCategory.BOAT, draft.category)
        assertEquals(TransportMode.BOAT, draft.journey?.mode)
        assertEquals("BGO", draft.journey?.from?.code)
    }

    @Test
    fun `pass colours and logo text land in the draft style`() {
        val draft = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Boarding pass",
                  "organizationName": "KLM",
                  "logoText": "KLM Royal Dutch",
                  "backgroundColor": "rgb(0, 54, 113)",
                  "foregroundColor": "rgb(255, 255, 255)",
                  "labelColor": "#A8C6E8",
                  "boardingPass": { "transitType": "PKTransitTypeAir" }
                }
                """.trimIndent(),
            ),
        )!!.toDraft()

        assertEquals("rgb(0, 54, 113)", draft.style?.backgroundColor)
        assertEquals("rgb(255, 255, 255)", draft.style?.foregroundColor)
        assertEquals("#A8C6E8", draft.style?.labelColor)
        assertEquals("KLM Royal Dutch", draft.style?.logoText)
    }

    @Test
    fun `pass images are extracted preferring the highest scale`() {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("pass.json"))
            zip.write("{}".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("logo.png"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("logo@2x.png"))
            zip.write(byteArrayOf(2, 2))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("strip.png"))
            zip.write(byteArrayOf(3))
            zip.closeEntry()
        }
        val images = PkpassImages.extract(out.toByteArray())!!

        assertEquals(2, images.logo?.size)
        assertEquals(1, images.strip?.size)
        assertNull(images.thumbnail)
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
        )!!.toDraft()

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
        )!!.toDraft()

        assertEquals("Primary Event", draft.title)
        assertEquals(TicketCategory.EVENT, draft.category)
        assertEquals("Primary Event", draft.event?.name)
        assertEquals("12", draft.seat?.number)
        assertEquals("F", draft.seat?.row)
        assertEquals("S1", draft.number)
        assertNull(draft.holder)
        assertNull(draft.price)
    }

    @Test
    fun `a movie event ticket lands in the cinema category`() {
        val pass = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Movie",
                  "organizationName": "Pathé",
                  "eventTicket": { "primaryFields": [{ "key": "m", "value": "Dune III" }] },
                  "semantics": { "eventName": "Dune III", "eventType": "PKEventTypeMovie" }
                }
                """.trimIndent(),
            ),
        ) as EventPass

        assertEquals(TicketCategory.CINEMA, pass.category)
        assertEquals(EventKind.CINEMA, pass.kind)
    }

    @Test
    fun `a generic transit type is not called a flight`() {
        val draft = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Metro",
                  "organizationName": "GVB",
                  "boardingPass": {
                    "transitType": "PKTransitTypeGeneric",
                    "primaryFields": [
                      { "key": "from", "label": "Centraal", "value": "CS" },
                      { "key": "to", "label": "Zuid", "value": "ZD" }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )!!.toDraft()

        assertEquals(TicketCategory.BUS, draft.category)
    }

    @Test
    fun `barcode alt text is kept for display under the barcode`() {
        val pass = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Pass",
                  "organizationName": "Org",
                  "generic": {},
                  "barcodes": [{
                    "format": "PKBarcodeFormatQR",
                    "message": "TOKEN",
                    "altText": "1234 5678"
                  }]
                }
                """.trimIndent(),
            ),
        )!!

        assertEquals("1234 5678", pass.barcode?.altText)
        assertEquals("1234 5678", pass.toDraft().barcodeAltText)
    }

    @Test
    fun `a transport pass exposes its fields directly`() {
        val pass = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Boarding pass",
                  "organizationName": "KLM",
                  "boardingPass": {
                    "transitType": "PKTransitTypeAir",
                    "primaryFields": [
                      { "key": "origin", "label": "Amsterdam", "value": "AMS" },
                      { "key": "destination", "label": "New York", "value": "JFK" }
                    ],
                    "headerFields": [{ "key": "gate", "label": "GATE", "value": "D07" }],
                    "auxiliaryFields": [
                      { "key": "boarding_time", "label": "Boarding", "value": "09:10" },
                      { "key": "class", "label": "Class", "value": "Economy" }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        ) as TransportPass

        assertEquals(TransportMode.FLIGHT, pass.mode)
        assertEquals("AMS", pass.from)
        assertEquals("JFK", pass.to)
        assertEquals("D07", pass.gate)
        assertEquals("09:10", pass.boardingTime)
        assertEquals("Economy", pass.travelClass)
    }

    @Test
    fun `the barcode keeps the pkpass message encoding for faithful re-rendering`() {
        val explicit = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Pass",
                  "organizationName": "Org",
                  "generic": {},
                  "barcodes": [{
                    "format": "PKBarcodeFormatQR",
                    "message": "TOKEN",
                    "messageEncoding": "utf-8"
                  }]
                }
                """.trimIndent(),
            ),
        )!!
        assertEquals("utf-8", explicit.barcode?.encoding)
        assertEquals("utf-8", explicit.toDraft().barcodeEncoding)

        val defaulted = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Pass",
                  "organizationName": "Org",
                  "generic": {},
                  "barcodes": [{ "format": "PKBarcodeFormatQR", "message": "TOKEN" }]
                }
                """.trimIndent(),
            ),
        )!!
        assertEquals("iso-8859-1", defaulted.barcode?.encoding)
    }
}
