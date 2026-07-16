package com.erfangholami.solidshare.data.passimport

import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketEventInfo
import com.erfangholami.solidshare.domain.model.TicketExtra
import com.erfangholami.solidshare.domain.model.TicketExtraPlacement
import com.erfangholami.solidshare.domain.model.TicketJourney
import com.erfangholami.solidshare.domain.model.TicketSeatInfo
import com.erfangholami.solidshare.domain.model.TicketSource
import com.erfangholami.solidshare.domain.model.TicketStop
import com.erfangholami.solidshare.domain.model.TicketStyle
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

    fun parse(bytes: ByteArray): Pass? {
        val passJson = readZipEntry(bytes) ?: return null
        val root = runCatching {
            json.parseToJsonElement(passJson).jsonObject
        }.getOrNull() ?: return null

        val styleKey = listOf("eventTicket", "boardingPass", "coupon", "storeCard", "generic")
            .firstOrNull { root.containsKey(it) }
        val style = styleKey?.let { root[it]?.jsonObject }

        val semantics = root["semantics"]?.jsonObject

        val category = categoryOf(styleKey, style).refinedBy(semantics)
        val eventKind = eventKindOf(semantics)

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
        val passBarcode = token?.let {
            PassBarcode(
                payload = it,
                format = barcodeFormat,
                encoding = barcode?.string("messageEncoding") ?: "iso-8859-1",
                altText = barcode?.string("altText"),
            )
        }

        val bag = FieldBag(collectFields(style))
        val relevantDate = root.string("relevantDate")

        val eventName = if (category == TicketCategory.EVENT || category == TicketCategory.CINEMA) {
            semantics?.string("eventName") ?: bag.takePrimary()?.value
        } else {
            semantics?.string("eventName")
        }

        val semanticJourney = journeyFrom(semantics, category)
        val fieldJourney = category.transportMode()?.let { mode ->
            journeyFromFields(bag, mode, relevantDate)
        }
        val journey = mergeJourneys(semanticJourney, fieldJourney)

        val seat = semantics?.seat()
            ?: bagSeat(bag)

        val holder = semantics?.holder()
            ?: bag.takeValue(
                "passenger", "passengername", "name", "traveler", "traveller",
                "holder", "holdername", "member", "membername", "attendee",
            )

        val number = semantics?.string("confirmationNumber")
            ?: bag.takeValue(
                "pnr", "confirmation", "confirmationnumber", "confirmationcode",
                "booking", "bookingreference", "bookingcode", "bookingnumber",
                "reference", "recordlocator", "reservation", "reservationnumber",
                "ordernumber", "ticketnumber",
            )
            ?: root.string("serialNumber")

        val venue = semantics?.venue()
            ?: bag.takeValue("venue", "location", "place", "where", "stadium", "arena", "theatre", "theater", "hall", "cinema")
                ?.let { TicketVenue(name = it) }

        val title = eventName
            ?: semantics?.flightTitle()
            ?: journey?.routeTitle()
            ?: root.string("description")
            ?: root.string("logoText")
            ?: root.string("organizationName")
            ?: return null

        val event = TicketEventInfo(
            name = eventName,
            start = semantics?.string("eventStartDate") ?: relevantDate,
            end = semantics?.string("eventEndDate"),
            venue = venue,
        ).takeIf { it.name != null || it.start != null || it.end != null || it.venue != null }

        val (price, currency) = semantics?.price() ?: (null to null)

        val issuer = semantics?.string("transitProvider") ?: root.string("organizationName")
        val extras = bag.rest().map { it.toExtra() }
        val passStyle = styleFrom(root)
        val validThrough = root.string("expirationDate")

        return when {
            journey != null -> TransportPass(
                title = title,
                journey = journey,
                issuer = issuer,
                number = number,
                holder = holder,
                barcode = passBarcode,
                style = passStyle,
                extras = extras,
                validThrough = validThrough,
                source = TicketSource.PKPASS,
                seat = seat,
                startIso = semantics?.string("eventStartDate") ?: relevantDate,
            )

            (category == TicketCategory.EVENT || category == TicketCategory.CINEMA) && event != null -> EventPass(
                title = title,
                event = event,
                category = category,
                issuer = issuer,
                number = number,
                holder = holder,
                barcode = passBarcode,
                style = passStyle,
                extras = extras,
                validThrough = validThrough,
                source = TicketSource.PKPASS,
                seat = seat,
                price = price,
                currency = currency,
                kind = eventKind,
            )

            category == TicketCategory.LOYALTY -> StorePass(
                title = title,
                issuer = issuer,
                number = number,
                holder = holder,
                barcode = passBarcode,
                style = passStyle,
                extras = extras,
                validThrough = validThrough,
                source = TicketSource.PKPASS,
            )

            category == TicketCategory.COUPON -> CouponPass(
                title = title,
                issuer = issuer,
                number = number,
                holder = holder,
                barcode = passBarcode,
                style = passStyle,
                extras = extras,
                validThrough = validThrough,
                source = TicketSource.PKPASS,
            )

            else -> GenericPass(
                title = title,
                category = category,
                issuer = issuer,
                number = number,
                holder = holder,
                barcode = passBarcode,
                style = passStyle,
                extras = extras,
                validThrough = validThrough,
                source = TicketSource.PKPASS,
                event = event,
                seat = seat,
            )
        }
    }

    private data class PassField(
        val key: String?,
        val label: String?,
        val value: String,
        val placement: TicketExtraPlacement,
    ) {
        fun toExtra(): TicketExtra = TicketExtra(
            label = label ?: key?.prettified(),
            value = value,
            placement = placement,
        )
    }

    private class FieldBag(fields: List<PassField>) {
        private val remaining = fields.toMutableList()

        fun take(vararg names: String): PassField? {
            val wanted = names.map { normalizePassKey(it) }
            val index = remaining.indexOfFirst { field ->
                field.key?.let { normalizePassKey(it) in wanted } == true ||
                    field.label?.let { normalizePassKey(it) in wanted } == true
            }
            if (index < 0) return null
            return remaining.removeAt(index)
        }

        fun takeValue(vararg names: String): String? = take(*names)?.value

        fun takePrimary(): PassField? {
            val index = remaining.indexOfFirst { it.placement == TicketExtraPlacement.PRIMARY }
            if (index < 0) return null
            return remaining.removeAt(index)
        }

        fun rest(): List<PassField> = remaining.toList()
    }

    private fun String.prettified(): String = this
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .replace(Regex("[_-]+"), " ")
        .trim()
        .replaceFirstChar { it.uppercase() }

    private fun collectFields(style: JsonObject?): List<PassField> {
        fun tier(key: String, placement: TicketExtraPlacement): List<PassField> =
            style?.get(key)?.jsonArray
                ?.mapNotNull { element ->
                    val field = element.jsonObject
                    field.string("value")?.let { value ->
                        PassField(
                            key = field.string("key"),
                            label = field.string("label"),
                            value = value,
                            placement = placement,
                        )
                    }
                }
                ?: emptyList()
        return tier("headerFields", TicketExtraPlacement.HEADER) +
            tier("primaryFields", TicketExtraPlacement.PRIMARY) +
            tier("secondaryFields", TicketExtraPlacement.SECONDARY) +
            tier("auxiliaryFields", TicketExtraPlacement.AUXILIARY) +
            tier("backFields", TicketExtraPlacement.BACK)
    }

    private fun TicketCategory.transportMode(): TransportMode? = when (this) {
        TicketCategory.FLIGHT -> TransportMode.FLIGHT
        TicketCategory.TRAIN -> TransportMode.TRAIN
        TicketCategory.BUS -> TransportMode.BUS
        TicketCategory.BOAT -> TransportMode.BOAT
        else -> null
    }

    private fun journeyFromFields(
        bag: FieldBag,
        mode: TransportMode,
        relevantDate: String?,
    ): TicketJourney? {
        val fromField = bag.take(
            "origin", "from", "fromstation", "fromairport", "departurestation",
            "departureairport", "departurecity", "departurestop", "departureport",
        )
        val toField = bag.take(
            "destination", "to", "tostation", "toairport", "arrivalstation",
            "arrivalairport", "arrivalcity", "arrivalstop", "arrivalport",
        )
        val primaryFrom = fromField ?: bag.takePrimary()
        val primaryTo = toField ?: bag.takePrimary()

        val departureTime = bag.takeValue("departuretime", "departs", "departuredatetime", "depart")
            ?: relevantDate
        val arrivalTime = bag.takeValue("arrivaltime", "arrives", "arrivaldatetime")
        val gate = bag.takeValue("gate", "gatenumber", "boardinggate", "boardingdoor", "door")
        val terminal = bag.takeValue("terminal", "departureterminal")
        val platform = bag.takeValue("platform", "track")
        val carrier = bag.takeValue("airline", "carrier", "operator", "company")
        val serviceNumber = bag.takeValue(
            "flight", "flightnumber", "flightno", "flightcode",
            "train", "trainnumber", "bus", "busnumber",
            "service", "servicenumber", "vessel", "route",
        )

        val from = primaryFrom.toStop(time = departureTime, gate = gate, terminal = terminal, platform = platform)
        val to = primaryTo.toStop(time = arrivalTime)

        if (from == null && to == null && carrier == null && serviceNumber == null) return null
        return TicketJourney(
            mode = mode,
            carrier = carrier,
            serviceNumber = serviceNumber,
            from = from,
            to = to,
        )
    }

    private fun PassField?.toStop(
        time: String? = null,
        gate: String? = null,
        terminal: String? = null,
        platform: String? = null,
    ): TicketStop? {
        val code = this?.value?.takeIf { it.looksLikeStationCode() }
        val name = this?.label?.takeIf { it.isNotBlank() && !it.looksLikeFieldLabel() }
            ?: this?.value?.takeIf { code == null }
        val stop = TicketStop(
            name = name,
            code = code,
            time = time,
            terminal = terminal,
            gate = gate,
            platform = platform,
        )
        return stop.takeIf {
            it.name != null || it.code != null || it.time != null ||
                it.terminal != null || it.gate != null || it.platform != null
        }
    }

    private fun String.looksLikeStationCode(): Boolean =
        length in 2..5 && all { it.isLetterOrDigit() } && any { it.isLetter() } && this == uppercase()

    private fun String.looksLikeFieldLabel(): Boolean =
        normalizePassKey(this) in setOf(
            "origin", "from", "destination", "to", "departure", "arrival",
            "fromstation", "tostation", "departurestation", "arrivalstation",
        )

    private fun bagSeat(bag: FieldBag): TicketSeatInfo? {
        val seat = TicketSeatInfo(
            number = bag.takeValue("seat", "seatnumber", "seatno", "place"),
            row = bag.takeValue("row", "seatrow"),
            section = bag.takeValue("section", "seatsection", "block", "coach", "carriage", "wagon"),
        )
        return seat.takeIf { it.number != null || it.row != null || it.section != null }
    }

    private fun mergeJourneys(primary: TicketJourney?, secondary: TicketJourney?): TicketJourney? {
        if (primary == null) return secondary
        if (secondary == null) return primary
        return primary.copy(
            carrier = primary.carrier ?: secondary.carrier,
            serviceNumber = primary.serviceNumber ?: secondary.serviceNumber,
            from = mergeStops(primary.from, secondary.from),
            to = mergeStops(primary.to, secondary.to),
        )
    }

    private fun mergeStops(primary: TicketStop?, secondary: TicketStop?): TicketStop? {
        if (primary == null) return secondary
        if (secondary == null) return primary
        return primary.copy(
            name = primary.name ?: secondary.name,
            code = primary.code ?: secondary.code,
            cityName = primary.cityName ?: secondary.cityName,
            time = primary.time ?: secondary.time,
            terminal = primary.terminal ?: secondary.terminal,
            gate = primary.gate ?: secondary.gate,
            platform = primary.platform ?: secondary.platform,
        )
    }

    private fun TicketJourney.routeTitle(): String? {
        val fromLabel = from?.code ?: from?.name
        val toLabel = to?.code ?: to?.name
        if (fromLabel == null || toLabel == null) return null
        return "$fromLabel → $toLabel"
    }

    private fun styleFrom(root: JsonObject): TicketStyle? =
        TicketStyle(
            backgroundColor = root.string("backgroundColor"),
            foregroundColor = root.string("foregroundColor"),
            labelColor = root.string("labelColor"),
            logoText = root.string("logoText"),
        ).takeIf { !it.isEmpty }

    private fun journeyFrom(semantics: JsonObject?, category: TicketCategory): TicketJourney? {
        val mode = category.transportMode() ?: return null
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
            "PKTransitTypeBoat" -> TicketCategory.BOAT
            "PKTransitTypeGeneric" -> TicketCategory.BUS
            else -> TicketCategory.FLIGHT
        }

        "coupon" -> TicketCategory.COUPON
        "storeCard" -> TicketCategory.LOYALTY
        else -> TicketCategory.GENERIC
    }

    private fun TicketCategory.refinedBy(semantics: JsonObject?): TicketCategory {
        if (this == TicketCategory.EVENT && semantics?.string("eventType") == "PKEventTypeMovie") {
            return TicketCategory.CINEMA
        }
        if (this != TicketCategory.GENERIC || semantics == null) return this
        return when {
            semantics.string("flightCode") != null ||
                semantics.string("departureAirportCode") != null -> TicketCategory.FLIGHT

            semantics.string("eventName") != null -> TicketCategory.EVENT
            else -> this
        }
    }

    private fun eventKindOf(semantics: JsonObject?): EventKind = when (semantics?.string("eventType")) {
        "PKEventTypeMovie" -> EventKind.CINEMA
        "PKEventTypeConcert" -> EventKind.CONCERT
        "PKEventTypeSports" -> EventKind.SPORTS
        "PKEventTypeTheater" -> EventKind.THEATER
        "PKEventTypeConference" -> EventKind.CONFERENCE
        "PKEventTypeSocialGathering" -> EventKind.SOCIAL
        else -> EventKind.OTHER
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
}
