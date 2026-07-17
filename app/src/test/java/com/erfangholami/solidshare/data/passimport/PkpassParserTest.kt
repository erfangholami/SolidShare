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

    private fun pkpass(passJson: String, extraEntries: Map<String, ByteArray> = emptyMap()): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("pass.json"))
            zip.write(passJson.toByteArray())
            zip.closeEntry()
            extraEntries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
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
        assertEquals("Boarding pass", draft.description)

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
        assertEquals("2026-08-14T09:10:00+02:00", draft.event?.start)

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

    @Test
    fun `pass strings localization resolves label and value keys`() {
        val strings = """
            "gate_label" = "Poort";
            "gate_value" = "D07";
            "brand" = "KLM Koninklijke";
        """.trimIndent().toByteArray()
        val pass = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Boarding pass",
                  "organizationName": "Org",
                  "logoText": "brand",
                  "boardingPass": {
                    "transitType": "PKTransitTypeAir",
                    "auxiliaryFields": [
                      { "key": "g", "label": "gate_label", "value": "gate_value" }
                    ]
                  }
                }
                """.trimIndent(),
                extraEntries = mapOf("nl.lproj/pass.strings" to strings),
            ),
            language = "nl",
        )!!

        assertEquals("KLM Koninklijke", pass.style?.logoText)
        assertTrue(pass.extras.any { it.label == "Poort" && it.value == "D07" })
    }

    @Test
    fun `date styled field values render human readable`() {
        val pass = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Event",
                  "organizationName": "Org",
                  "generic": {
                    "secondaryFields": [
                      { "key": "d", "label": "When", "value": "2026-08-14T19:30:00+02:00",
                        "dateStyle": "PKDateStyleMedium", "timeStyle": "PKDateStyleShort" },
                      { "key": "p", "label": "Paid", "value": "49.50", "currencyCode": "EUR" }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )!!

        val whenField = pass.extras.first { it.label == "When" }
        assertTrue(whenField.value != "2026-08-14T19:30:00+02:00")
        assertTrue(!whenField.value.contains('T'))
        val paid = pass.extras.first { it.label == "Paid" }
        assertTrue(paid.value.contains("49"))
        assertTrue(paid.value.contains("€") || paid.value.contains("EUR"))
    }

    @Test
    fun `a voided pass is expired immediately`() {
        val pass = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Old coupon",
                  "organizationName": "Org",
                  "voided": true,
                  "coupon": {}
                }
                """.trimIndent(),
            ),
        )!!

        assertEquals(true, pass.toDraft().voided)
        assertNull(pass.validThrough)
    }

    @Test
    fun `broader semantics land as structured extras`() {
        val pass = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Concert",
                  "organizationName": "Org",
                  "eventTicket": {},
                  "semantics": {
                    "eventName": "Solid Live",
                    "performerNames": ["Alice", "Bob"],
                    "wifiAccess": [{ "ssid": "VenueWifi", "password": "solid123" }],
                    "balance": { "amount": "12.50", "currencyCode": "EUR" },
                    "boardingGroup": "B",
                    "boardingSequenceNumber": "37",
                    "seats": [
                      { "seatNumber": "12A" },
                      { "seatNumber": "12B" }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )!!

        val draft = pass.toDraft()
        assertEquals(listOf("Alice", "Bob"), draft.event?.performers)
        assertEquals("VenueWifi", draft.wifi.single().ssid)
        assertEquals("solid123", draft.wifi.single().password)
        assertEquals("12.50", draft.membership?.balance)
        assertEquals("EUR", draft.membership?.balanceCurrency)
        assertEquals("B", draft.reservation?.boardingGroup)
        assertEquals("37", draft.reservation?.sequenceNumber)
        val labels = pass.extras.associate { it.label to it.value }
        assertEquals("12A, 12B", labels["Seats"])
    }

    @Test
    fun `flight duration and airline code enrich the journey`() {
        val pass = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Boarding pass",
                  "organizationName": "Org",
                  "boardingPass": { "transitType": "PKTransitTypeAir" },
                  "semantics": {
                    "airlineCode": "KL",
                    "flightCode": "KL641",
                    "departureAirportCode": "AMS",
                    "destinationAirportCode": "JFK",
                    "duration": 28800
                  }
                }
                """.trimIndent(),
            ),
        ) as TransportPass

        assertEquals("KL", pass.journey.carrier)
        assertEquals("8h", pass.journey.duration)
    }

    @Test
    fun `auxiliary rows keep their issuer-defined order`() {
        val pass = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Boarding pass",
                  "organizationName": "Org",
                  "boardingPass": {
                    "transitType": "PKTransitTypeAir",
                    "auxiliaryFields": [
                      { "key": "b", "label": "Row one B", "value": "2", "row": 1 },
                      { "key": "a", "label": "Row zero A", "value": "1", "row": 0 }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )!!

        val labels = pass.extras.map { it.label }
        assertTrue(labels.indexOf("Row zero A") < labels.indexOf("Row one B"))
    }

    @Test
    fun `strings files parse both encodings and escapes`() {
        val utf8 = PkpassParser.parseStringsFile(
            "\"key_a\" = \"Value \\\"quoted\\\"\";\n\"key_b\" = \"Line\\nBreak\";".toByteArray()
        )
        assertEquals("Value \"quoted\"", utf8["key_a"])
        assertEquals("Line\nBreak", utf8["key_b"])

        val utf16 = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            "\"k\" = \"v\";".toByteArray(Charsets.UTF_16BE)
        assertEquals("v", PkpassParser.parseStringsFile(utf16)["k"])
    }

    @Test
    fun `pass identity and web service block are captured in full`() {
        val draft = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Ticket",
                  "organizationName": "Org",
                  "passTypeIdentifier": "pass.com.example.event",
                  "serialNumber": "SN-123",
                  "teamIdentifier": "TEAM99",
                  "webServiceURL": "https://example.com/passes/",
                  "authenticationToken": "tok-abc",
                  "groupingIdentifier": "trip-1",
                  "sharingProhibited": true,
                  "eventTicket": {}
                }
                """.trimIndent(),
            ),
        )!!.toDraft()

        val info = draft.passInfo!!
        assertEquals("pass.com.example.event", info.passTypeIdentifier)
        assertEquals("SN-123", info.serialNumber)
        assertEquals("TEAM99", info.teamIdentifier)
        assertEquals("https://example.com/passes/", info.webServiceUrl)
        assertEquals("tok-abc", info.authenticationToken)
        assertEquals("trip-1", info.groupingIdentifier)
        assertEquals(true, info.sharingProhibited)
    }

    @Test
    fun `relevantDates interval and legacy relevantDate both normalize`() {
        val interval = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Show",
                  "organizationName": "Org",
                  "relevantDates": [
                    { "startDate": "20260707T074030Z", "endDate": "2026-07-07T09:40Z" }
                  ],
                  "eventTicket": {}
                }
                """.trimIndent(),
            ),
        )!!.toDraft()
        assertEquals("2026-07-07T07:40:30Z", interval.relevantStart)
        assertEquals("2026-07-07T09:40:00Z", interval.relevantEnd)

        val single = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Show",
                  "organizationName": "Org",
                  "relevantDate": "2026-07-07T09:40+0200",
                  "eventTicket": {}
                }
                """.trimIndent(),
            ),
        )!!.toDraft()
        assertEquals("2026-07-07T09:40:00+02:00", single.relevantStart)
        assertNull(single.relevantEnd)
    }

    @Test
    fun `locations with altitude and beacons are captured`() {
        val draft = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Ticket",
                  "organizationName": "Org",
                  "maxDistance": 150,
                  "locations": [
                    {
                      "latitude": 52.37,
                      "longitude": 4.89,
                      "altitude": 2.5,
                      "relevantText": "Welcome to the venue"
                    }
                  ],
                  "beacons": [
                    {
                      "proximityUUID": "f1e2d3c4",
                      "major": 7,
                      "minor": 21,
                      "relevantText": "Near gate"
                    }
                  ],
                  "eventTicket": {}
                }
                """.trimIndent(),
            ),
        )!!.toDraft()

        val location = draft.locations.single()
        assertEquals(52.37, location.latitude!!, 0.0001)
        assertEquals(4.89, location.longitude!!, 0.0001)
        assertEquals(2.5, location.altitude!!, 0.0001)
        assertEquals(150, location.maxDistance)
        assertEquals("Welcome to the venue", location.relevantText)

        val beacon = draft.beacons.single()
        assertEquals("f1e2d3c4", beacon.proximityUuid)
        assertEquals(7, beacon.major)
        assertEquals(21, beacon.minor)
        assertEquals("Near gate", beacon.relevantText)
    }

    @Test
    fun `additionalInfo and footer fields land on their own placements`() {
        val pass = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Boarding pass",
                  "organizationName": "Org",
                  "boardingPass": {
                    "transitType": "PKTransitTypeAir",
                    "additionalInfoFields": [
                      { "key": "info", "label": "Info", "value": "Carry-on only" }
                    ],
                    "footerFields": [
                      { "key": "foot", "label": "Notice", "value": "Gate may change" }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )!!

        assertTrue(
            pass.extras.any {
                it.placement == TicketExtraPlacement.ADDITIONAL && it.value == "Carry-on only"
            },
        )
        assertTrue(
            pass.extras.any {
                it.placement == TicketExtraPlacement.FOOTER && it.value == "Gate may change"
            },
        )
    }

    @Test
    fun `attributedValue strips html and keeps the link`() {
        val pass = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Ticket",
                  "organizationName": "Org",
                  "eventTicket": {
                    "backFields": [
                      {
                        "key": "site",
                        "label": "Website",
                        "value": "example.com",
                        "attributedValue": "<a href='https://example.com/help'>Visit our site</a>",
                        "changeMessage": "Gate changed to %@",
                        "textAlignment": "PKTextAlignmentRight"
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )!!

        val extra = pass.extras.single { it.label == "Website" }
        assertEquals("Visit our site", extra.value)
        assertEquals("https://example.com/help", extra.linkUrl)
        assertEquals("Gate changed to %@", extra.changeMessage)
        assertEquals("right", extra.textAlignment)
    }

    @Test
    fun `strip and footer colors plus logo symbol are captured`() {
        val draft = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Ticket",
                  "organizationName": "Org",
                  "backgroundColor": "#1F5",
                  "stripColor": "rgb(0, 54, 113)",
                  "footerBackgroundColor": "#001122",
                  "logoSymbolName": "airplane",
                  "eventTicket": {}
                }
                """.trimIndent(),
            ),
        )!!.toDraft()

        val style = draft.style!!
        assertEquals("#1F5", style.backgroundColor)
        assertEquals("rgb(0, 54, 113)", style.stripColor)
        assertEquals("#001122", style.footerBackgroundColor)
        assertEquals("airplane", style.logoSymbolName)
    }

    @Test
    fun `silenceRequested and description are captured`() {
        val draft = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Night ferry crossing",
                  "organizationName": "FerryCo",
                  "generic": {},
                  "semantics": { "silenceRequested": true }
                }
                """.trimIndent(),
            ),
        )!!.toDraft()

        assertEquals(true, draft.silenceRequested)
        assertEquals("Night ferry crossing", draft.title)
        assertNull(draft.description)
    }

    @Test
    fun `journey status, vehicle, boarding and rich seat semantics are captured`() {
        val draft = PkpassParser.parse(
            pkpass(
                """
                {
                  "description": "Train to Berlin",
                  "organizationName": "DB",
                  "boardingPass": { "transitType": "PKTransitTypeTrain" },
                  "semantics": {
                    "departureStationName": "Amsterdam Centraal",
                    "destinationStationName": "Berlin Hbf",
                    "transitStatus": "Delayed",
                    "transitStatusReason": "Signal failure",
                    "vehicleName": "ICE International",
                    "vehicleNumber": "ICE 123",
                    "vehicleType": "High-speed rail",
                    "carNumber": "7",
                    "currentBoardingDate": "2026-08-14T09:10+0200",
                    "seats": [
                      {
                        "seatNumber": "41",
                        "seatType": "window",
                        "seatIdentifier": "coach7-41",
                        "seatDescription": "Window seat, quiet zone"
                      }
                    ]
                  }
                }
                """.trimIndent(),
            ),
        )!!.toDraft()

        val journey = draft.journey!!
        assertEquals("Delayed", journey.transitStatus)
        assertEquals("Signal failure", journey.transitStatusReason)
        assertEquals("ICE International", journey.vehicleName)
        assertEquals("ICE 123", journey.vehicleNumber)
        assertEquals("High-speed rail", journey.vehicleType)
        assertEquals("7", journey.coachNumber)
        assertEquals("2026-08-14T09:10:00+02:00", journey.boardingTime)

        val seat = draft.seat!!
        assertEquals("41", seat.number)
        assertEquals("window", seat.type)
        assertEquals("coach7-41", seat.identifier)
        assertEquals("Window seat, quiet zone", seat.description)
    }

    @Test
    fun `basic iso and no-seconds datetimes normalize`() {
        assertEquals("2026-07-07T07:40:30Z", normalizePassDateTime("20260707T074030Z"))
        assertEquals("2026-07-07T07:40:00+02:00", normalizePassDateTime("20260707T0740+0200"))
        assertEquals("2026-07-07T09:40:00Z", normalizePassDateTime("2026-07-07T09:40Z"))
        assertEquals("2026-07-07T09:40:00+02:00", normalizePassDateTime("2026-07-07T09:40+02:00"))
        assertEquals("2026-07-07T09:40:00+02:00", normalizePassDateTime("2026-07-07T09:40+0200"))
        assertEquals("2026-07-07T09:40:30+02:00", normalizePassDateTime("2026-07-07T09:40:30+0200"))
        assertEquals("2026-07-07T09:40:30Z", normalizePassDateTime("2026-07-07T09:40:30Z"))
        assertEquals("not-a-date", normalizePassDateTime("not-a-date"))
    }

    @Test
    fun `localized lproj images override root images`() {
        fun png(marker: Byte) = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, marker,
        )

        val bytes = pkpass(
            """{ "description": "T", "organizationName": "O", "eventTicket": {} }""",
            extraEntries = mapOf(
                "logo.png" to png(1),
                "nl.lproj/logo.png" to png(2),
                "artwork@2x.png" to png(3),
            ),
        )

        val localized = PkpassImages.extract(bytes, language = "nl")!!
        assertEquals(2, localized.logo!!.last().toInt())
        assertEquals(3, localized.background!!.last().toInt())

        val fallback = PkpassImages.extract(bytes, language = "de")!!
        assertEquals(1, fallback.logo!!.last().toInt())
    }
}
