package com.erfangholami.solidshare.data.passimport

import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketEventInfo
import com.erfangholami.solidshare.domain.model.TicketJourney
import com.erfangholami.solidshare.domain.model.TicketSeatInfo
import com.erfangholami.solidshare.domain.model.TicketSource
import com.erfangholami.solidshare.domain.model.TicketStop
import com.erfangholami.solidshare.domain.model.TicketVenue
import com.erfangholami.solidshare.domain.model.TransportMode
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object PkpassParser {

    const val MIME_TYPE = "application/vnd.apple.pkpass"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(bytes: ByteArray): TicketDraft? {
        val passJson = readZipEntry(bytes) ?: return null
        val root = runCatching {
            json.parseToJsonElement(passJson).jsonObject
        }.getOrNull() ?: return null

        val styleKey = listOf("eventTicket", "boardingPass", "coupon", "storeCard", "generic")
            .firstOrNull { root.containsKey(it) }
        val style = styleKey?.let { root[it]?.jsonObject }

        val semantics = root["semantics"]?.jsonObject

        val category = categoryOf(styleKey, style).refinedBy(semantics)

        val barcode = root["barcodes"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: root["barcode"]?.jsonObject
        val token = barcode?.string("message")
        val barcodeFormat = when (barcode?.string("format")) {
            "PKBarcodeFormatQR" -> TicketBarcodeFormat.QR_CODE
            "PKBarcodeFormatPDF417" -> TicketBarcodeFormat.PDF_417
            "PKBarcodeFormatAztec" -> TicketBarcodeFormat.AZTEC
            "PKBarcodeFormatCode128" -> TicketBarcodeFormat.CODE_128
            else -> if (token != null) TicketBarcodeFormat.QR_CODE else TicketBarcodeFormat.NONE
        }

        val fields = listOf("headerFields", "primaryFields", "secondaryFields", "auxiliaryFields")
            .flatMap { key ->
                style?.get(key)?.jsonArray?.mapNotNull { it.jsonObject } ?: emptyList()
            }
        val fieldLines = fields.mapNotNull { field ->
            val value = field.string("value") ?: return@mapNotNull null
            val label = field.string("label")
            if (label.isNullOrBlank()) value else "$label: $value"
        }
        val heuristicSeat = TicketSeatInfo(
            number = fields.valueForKeys("seat", "seatNumber"),
            row = fields.valueForKeys("row", "seatRow"),
            section = fields.valueForKeys("section", "seatSection"),
        ).takeIf { it.number != null || it.row != null || it.section != null }
        val heuristicEventName = style?.get("primaryFields")?.jsonArray
            ?.firstOrNull()?.jsonObject?.string("value")

        val title = semantics?.string("eventName")
            ?: semantics?.flightTitle()
            ?: root.string("description")
            ?: root.string("logoText")
            ?: root.string("organizationName")
            ?: return null

        val event = TicketEventInfo(
            name = semantics?.string("eventName") ?: heuristicEventName,
            start = semantics?.string("eventStartDate") ?: root.string("relevantDate"),
            end = semantics?.string("eventEndDate"),
            venue = semantics?.venue(),
        ).takeIf { it.name != null || it.start != null || it.end != null || it.venue != null }

        val (price, currency) = semantics?.price() ?: (null to null)

        return TicketDraft(
            title = title,
            description = fieldLines.joinToString("\n").ifBlank { null },
            number = semantics?.string("confirmationNumber") ?: root.string("serialNumber"),
            token = token,
            barcodeFormat = barcodeFormat,
            category = category,
            issuer = semantics?.string("transitProvider") ?: root.string("organizationName"),
            holder = semantics?.holder(),
            seat = semantics?.seat() ?: heuristicSeat,
            price = price,
            currency = currency,
            event = event,
            journey = journeyFrom(semantics, category),
            validThrough = root.string("expirationDate"),
            source = TicketSource.PKPASS,
        )
    }

    private fun journeyFrom(semantics: JsonObject?, category: TicketCategory): TicketJourney? {
        val mode = when (category) {
            TicketCategory.FLIGHT -> TransportMode.FLIGHT
            TicketCategory.TRAIN -> TransportMode.TRAIN
            TicketCategory.BUS -> TransportMode.BUS
            else -> return null
        }
        semantics ?: return null
        val from = semantics.stop("departure")
        val to = semantics.stop("destination")
        val carrier = semantics.string("transitProvider")
        val serviceNumber = semantics.string("flightCode") ?: semantics.string("flightNumber")
        if (from == null && to == null && carrier == null && serviceNumber == null) return null
        return TicketJourney(
            mode = mode,
            carrier = carrier,
            serviceNumber = serviceNumber,
            from = from,
            to = to,
        )
    }

    private fun JsonObject.stop(side: String): TicketStop? {
        val time = if (side == "departure") {
            string("currentDepartureDate") ?: string("originalDepartureDate")
        } else {
            string("currentArrivalDate") ?: string("originalArrivalDate")
        }
        val stop = TicketStop(
            name = string("${side}AirportName") ?: string("${side}StationName"),
            code = string("${side}AirportCode"),
            cityName = string("${side}CityName"),
            time = time,
            terminal = string("${side}Terminal"),
            gate = string("${side}Gate"),
            platform = string("${side}Platform"),
        )
        return stop.takeIf {
            it.name != null || it.code != null || it.cityName != null || it.time != null ||
                it.terminal != null || it.gate != null || it.platform != null
        }
    }

    private fun categoryOf(styleKey: String?, style: JsonObject?): TicketCategory = when (styleKey) {
        "eventTicket" -> TicketCategory.EVENT
        "boardingPass" -> when (style?.string("transitType")) {
            "PKTransitTypeAir" -> TicketCategory.FLIGHT
            "PKTransitTypeTrain" -> TicketCategory.TRAIN
            "PKTransitTypeBus" -> TicketCategory.BUS
            else -> TicketCategory.FLIGHT
        }

        "coupon" -> TicketCategory.COUPON
        "storeCard" -> TicketCategory.LOYALTY
        else -> TicketCategory.GENERIC
    }

    private fun TicketCategory.refinedBy(semantics: JsonObject?): TicketCategory {
        if (this != TicketCategory.GENERIC || semantics == null) return this
        return when {
            semantics.string("flightCode") != null ||
                semantics.string("departureAirportCode") != null -> TicketCategory.FLIGHT

            semantics.string("eventName") != null -> TicketCategory.EVENT
            else -> this
        }
    }

    private fun JsonObject.venue(): TicketVenue? {
        val name = string("venueName") ?: return null
        val room = string("venueRoom")
        return TicketVenue(name = if (room != null) "$name · $room" else name)
    }

    private fun JsonObject.seat(): TicketSeatInfo? {
        val seat = this["seats"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        return TicketSeatInfo(
            number = seat.string("seatNumber"),
            row = seat.string("seatRow"),
            section = seat.string("seatSection"),
        ).takeIf { it.number != null || it.row != null || it.section != null }
    }

    private fun JsonObject.holder(): String? {
        string("attendeeName")?.let { return it }
        val name = this["passengerName"]?.jsonObject ?: return null
        return listOfNotNull(name.string("givenName"), name.string("familyName"))
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
    }

    private fun JsonObject.price(): Pair<String, String?>? {
        val amount = this["totalPrice"]?.jsonObject ?: return null
        val value = amount.string("amount") ?: return null
        return value to amount.string("currencyCode")
    }

    private fun JsonObject.flightTitle(): String? {
        val departure = string("departureAirportCode")
        val destination = string("destinationAirportCode")
        if (departure != null && destination != null) return "$departure → $destination"
        return string("flightCode")
    }

    private fun readZipEntry(bytes: ByteArray): String? = runCatching {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }
                .firstOrNull { it.name == "pass.json" }
                ?.let { zip.readBytes().toString(Charsets.UTF_8) }
        }
    }.getOrNull()

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun List<JsonObject>.valueForKeys(vararg keys: String): String? =
        firstOrNull { field ->
            field.string("key")?.lowercase() in keys.map { it.lowercase() }
        }?.string("value")
}
