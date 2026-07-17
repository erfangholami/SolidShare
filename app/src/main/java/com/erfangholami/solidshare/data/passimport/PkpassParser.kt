package com.erfangholami.solidshare.data.passimport

import android.text.format.DateUtils
import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.erfangholami.solidshare.domain.model.TicketBeaconInfo
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketEventInfo
import com.erfangholami.solidshare.domain.model.TicketExtra
import com.erfangholami.solidshare.domain.model.TicketExtraPlacement
import com.erfangholami.solidshare.domain.model.TicketJourney
import com.erfangholami.solidshare.domain.model.TicketLocationInfo
import com.erfangholami.solidshare.domain.model.TicketMembershipInfo
import com.erfangholami.solidshare.domain.model.TicketPassInfo
import com.erfangholami.solidshare.domain.model.TicketReservationInfo
import com.erfangholami.solidshare.domain.model.TicketSeatInfo
import com.erfangholami.solidshare.domain.model.TicketSource
import com.erfangholami.solidshare.domain.model.TicketStop
import com.erfangholami.solidshare.domain.model.TicketStyle
import com.erfangholami.solidshare.domain.model.TicketVenue
import com.erfangholami.solidshare.domain.model.TicketWifiInfo
import com.erfangholami.solidshare.domain.model.TransportMode
import java.io.ByteArrayInputStream
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
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

    fun parse(bytes: ByteArray, language: String = Locale.getDefault().language): Pass? {
        val archive = readArchive(bytes) ?: return null
        val root = runCatching {
            json.parseToJsonElement(archive.passJson).jsonObject
        }.getOrNull() ?: return null
        val strings = archive.stringsFor(language)
        val loc: (String?) -> String? = { raw -> raw?.let { strings[it] ?: it } }

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

        val bag = FieldBag(collectFields(style, loc))
        val (relevantStart, relevantEnd) = relevantInterval(root)

        val eventName = if (category == TicketCategory.EVENT || category == TicketCategory.CINEMA) {
            semantics?.string("eventName") ?: bag.takePrimary()?.value
        } else {
            semantics?.string("eventName")
        }

        val semanticJourney = journeyFrom(semantics, category)
        val fieldJourney = category.transportMode()?.let { mode ->
            journeyFromFields(bag, mode, relevantStart)
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
            ?: semantics?.string("membershipNumber")
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
            ?: loc(root.string("description"))
            ?: loc(root.string("logoText"))
            ?: loc(root.string("organizationName"))
            ?: return null

        val event = TicketEventInfo(
            name = eventName,
            start = semantics?.string("eventStartDate")?.let(::normalizePassDateTime) ?: relevantStart,
            end = semantics?.string("eventEndDate")?.let(::normalizePassDateTime) ?: relevantEnd,
            doorTime = (semantics?.string("doorsOpenDate") ?: semantics?.string("doorsOpen"))
                ?.let(::normalizePassDateTime),
            venue = venue,
            performers = performersFrom(semantics),
        ).takeIf {
            it.name != null || it.start != null || it.end != null || it.doorTime != null ||
                it.venue != null || it.performers.isNotEmpty()
        }

        val (price, currency) = semantics?.price() ?: (null to null)

        val issuer = semantics?.string("transitProvider") ?: loc(root.string("organizationName"))
        val extras = bag.rest().map { it.toExtra() } + semanticExtras(semantics)
        val passStyle = styleFrom(root, loc)
        val validThrough = root.string("expirationDate")?.let(::normalizePassDateTime)
        val data = PassData(
            description = loc(root.string("description")),
            passInfo = passInfoFrom(root),
            reservation = reservationFrom(semantics),
            membership = membershipFrom(semantics),
            locations = locationsFrom(root),
            beacons = beaconsFrom(root),
            wifi = wifiFrom(semantics),
            voided = root["voided"]?.jsonPrimitive?.booleanOrNull,
            silenceRequested = semantics?.get("silenceRequested")?.jsonPrimitive?.booleanOrNull,
            relevantStart = relevantStart,
            relevantEnd = relevantEnd,
        )

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
                data = data,
                seat = seat,
                startIso = semantics?.string("eventStartDate")?.let(::normalizePassDateTime)
                    ?: relevantStart,
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
                data = data,
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
                data = data,
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
                data = data,
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
                data = data,
                event = event,
                seat = seat,
            )
        }
    }

    private data class PassField(
        val key: String?,
        val label: String?,
        val value: String,
        val display: String = value,
        val placement: TicketExtraPlacement,
        val row: Int = 0,
        val changeMessage: String? = null,
        val textAlignment: String? = null,
        val linkUrl: String? = null,
    ) {
        fun toExtra(): TicketExtra = TicketExtra(
            label = label ?: key?.prettified(),
            value = display,
            placement = placement,
            changeMessage = changeMessage,
            textAlignment = textAlignment,
            linkUrl = linkUrl,
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

    private fun collectFields(style: JsonObject?, loc: (String?) -> String?): List<PassField> {
        fun tier(key: String, placement: TicketExtraPlacement): List<PassField> =
            style?.get(key)?.jsonArray
                ?.mapNotNull { element ->
                    val field = element.jsonObject
                    val attributed = field.string("attributedValue")
                    val rawValue = attributed?.let { stripHtml(it) }?.takeIf { it.isNotBlank() }
                        ?: field.string("value")
                        ?: return@mapNotNull null
                    val value = loc(rawValue) ?: rawValue
                    PassField(
                        key = field.string("key"),
                        label = loc(field.string("label")),
                        value = value,
                        display = formatFieldValue(field, value),
                        placement = placement,
                        row = field["row"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                        changeMessage = loc(field.string("changeMessage")),
                        textAlignment = field.string("textAlignment")
                            ?.removePrefix("PKTextAlignment")?.lowercase(),
                        linkUrl = attributed?.let { ANCHOR_HREF.find(it)?.groupValues?.get(1) },
                    )
                }
                ?: emptyList()
        return tier("headerFields", TicketExtraPlacement.HEADER) +
            tier("primaryFields", TicketExtraPlacement.PRIMARY) +
            tier("secondaryFields", TicketExtraPlacement.SECONDARY) +
            tier("auxiliaryFields", TicketExtraPlacement.AUXILIARY).sortedBy { it.row } +
            tier("backFields", TicketExtraPlacement.BACK) +
            tier("additionalInfoFields", TicketExtraPlacement.ADDITIONAL) +
            tier("footerFields", TicketExtraPlacement.FOOTER)
    }

    private fun stripHtml(raw: String): String = raw
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .trim()

    private fun formatFieldValue(field: JsonObject, value: String): String {
        field.string("currencyCode")?.let { code ->
            value.replace(',', '.').toDoubleOrNull()?.let { amount ->
                runCatching {
                    return NumberFormat.getCurrencyInstance().apply {
                        currency = Currency.getInstance(code)
                    }.format(amount)
                }
            }
        }
        field.string("numberStyle")?.let { style ->
            value.replace(',', '.').toDoubleOrNull()?.let { amount ->
                runCatching {
                    return when (style) {
                        "PKNumberStylePercent" -> NumberFormat.getPercentInstance().format(amount)
                        "PKNumberStyleScientific" -> String.format(Locale.getDefault(), "%.3e", amount)
                        else -> NumberFormat.getNumberInstance().format(amount)
                    }
                }
            }
        }
        val dateStyle = field.string("dateStyle").toFormatStyle()
        val timeStyle = field.string("timeStyle").toFormatStyle()
        if (dateStyle == null && timeStyle == null) return value
        val keepWallTime = field["ignoresTimeZone"]?.jsonPrimitive?.booleanOrNull == true
        val temporal = parseTemporal(normalizePassDateTime(value), keepWallTime) ?: return value
        if (field["isRelative"]?.jsonPrimitive?.booleanOrNull == true) {
            temporal.toInstantOrNull()?.let { instant ->
                runCatching {
                    return DateUtils.getRelativeTimeSpanString(instant.toEpochMilli()).toString()
                }
            }
        }
        val formatter = when {
            dateStyle != null && timeStyle != null -> DateTimeFormatter.ofLocalizedDateTime(dateStyle, timeStyle)
            dateStyle != null -> DateTimeFormatter.ofLocalizedDate(dateStyle)
            else -> DateTimeFormatter.ofLocalizedTime(timeStyle)
        }
        return runCatching { formatter.format(temporal) }.getOrDefault(value)
    }

    private fun java.time.temporal.TemporalAccessor.toInstantOrNull(): Instant? = when (this) {
        is LocalDateTime -> atZone(ZoneId.systemDefault()).toInstant()
        is LocalDate -> atStartOfDay(ZoneId.systemDefault()).toInstant()
        else -> null
    }

    private fun String?.toFormatStyle(): FormatStyle? = when (this) {
        "PKDateStyleShort" -> FormatStyle.SHORT
        "PKDateStyleMedium" -> FormatStyle.MEDIUM
        "PKDateStyleLong" -> FormatStyle.LONG
        "PKDateStyleFull" -> FormatStyle.FULL
        else -> null
    }

    private fun parseTemporal(
        value: String,
        keepWallTime: Boolean = false,
    ): java.time.temporal.TemporalAccessor? {
        runCatching {
            val parsed = OffsetDateTime.parse(value)
            return if (keepWallTime) {
                parsed.toLocalDateTime()
            } else {
                parsed.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            }
        }
        runCatching { return Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDateTime() }
        runCatching { return LocalDateTime.parse(value) }
        runCatching { return LocalDate.parse(value) }
        return null
    }

    private fun semanticExtras(semantics: JsonObject?): List<TicketExtra> {
        semantics ?: return emptyList()
        val extras = mutableListOf<TicketExtra>()
        semantics["seats"]?.jsonArray?.takeIf { it.size > 1 }?.let { seats ->
            val numbers = seats.mapNotNull { it.jsonObject.string("seatNumber") }
            if (numbers.size > 1) {
                extras.add(TicketExtra(label = "Seats", value = numbers.joinToString(", "), placement = TicketExtraPlacement.ADDITIONAL))
            }
        }
        return extras
    }

    private fun performersFrom(semantics: JsonObject?): List<String> =
        semantics?.get("performerNames")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    private fun passInfoFrom(root: JsonObject): TicketPassInfo? = TicketPassInfo(
        passTypeIdentifier = root.string("passTypeIdentifier"),
        serialNumber = root.string("serialNumber"),
        teamIdentifier = root.string("teamIdentifier"),
        webServiceUrl = root.string("webServiceURL"),
        authenticationToken = root.string("authenticationToken"),
        groupingIdentifier = root.string("groupingIdentifier"),
        sharingProhibited = root["sharingProhibited"]?.jsonPrimitive?.booleanOrNull,
    ).takeIf { !it.isEmpty }

    private fun reservationFrom(semantics: JsonObject?): TicketReservationInfo? {
        semantics ?: return null
        return TicketReservationInfo(
            boardingGroup = semantics.string("boardingGroup"),
            sequenceNumber = semantics.string("boardingSequenceNumber"),
            priorityStatus = semantics.string("priorityStatus"),
            securityScreening = semantics.string("securityScreening"),
        ).takeIf { !it.isEmpty }
    }

    private fun membershipFrom(semantics: JsonObject?): TicketMembershipInfo? {
        semantics ?: return null
        val balance = semantics["balance"]?.jsonObject
        return TicketMembershipInfo(
            programName = semantics.string("membershipProgramName"),
            number = semantics.string("membershipProgramNumber"),
            balance = balance?.string("amount"),
            balanceCurrency = balance?.string("currencyCode"),
        ).takeIf { !it.isEmpty }
    }

    private fun wifiFrom(semantics: JsonObject?): List<TicketWifiInfo> =
        semantics?.get("wifiAccess")?.jsonArray
            ?.mapNotNull { element ->
                val wifi = element.jsonObject
                wifi.string("ssid")?.let { TicketWifiInfo(ssid = it, password = wifi.string("password")) }
            }
            ?: emptyList()

    private fun relevantInterval(root: JsonObject): Pair<String?, String?> {
        root["relevantDates"]?.jsonArray?.forEach { element ->
            val entry = element.jsonObject
            entry.string("date")?.let { return normalizePassDateTime(it) to null }
            val start = entry.string("startDate")?.let(::normalizePassDateTime)
            val end = entry.string("endDate")?.let(::normalizePassDateTime)
            if (start != null || end != null) return start to end
        }
        return root.string("relevantDate")?.let(::normalizePassDateTime) to null
    }

    private fun locationsFrom(root: JsonObject): List<TicketLocationInfo> {
        val maxDistance = root["maxDistance"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toInt()
        return root["locations"]?.jsonArray
            ?.mapNotNull { element ->
                val location = element.jsonObject
                val latitude = location["latitude"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val longitude = location["longitude"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val text = location.string("relevantText")
                if (latitude == null && longitude == null && text == null) return@mapNotNull null
                TicketLocationInfo(
                    latitude = latitude,
                    longitude = longitude,
                    altitude = location["altitude"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
                    maxDistance = maxDistance,
                    relevantText = text,
                )
            }
            ?: emptyList()
    }

    private fun beaconsFrom(root: JsonObject): List<TicketBeaconInfo> =
        root["beacons"]?.jsonArray
            ?.mapNotNull { element ->
                val beacon = element.jsonObject
                TicketBeaconInfo(
                    proximityUuid = beacon.string("proximityUUID"),
                    major = beacon["major"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                    minor = beacon["minor"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                    relevantText = beacon.string("relevantText"),
                ).takeUnless { it.proximityUuid == null && it.relevantText == null }
            }
            ?: emptyList()

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
            boardingTime = primary.boardingTime ?: secondary.boardingTime,
            transitStatus = primary.transitStatus ?: secondary.transitStatus,
            transitStatusReason = primary.transitStatusReason ?: secondary.transitStatusReason,
            vehicleName = primary.vehicleName ?: secondary.vehicleName,
            vehicleNumber = primary.vehicleNumber ?: secondary.vehicleNumber,
            vehicleType = primary.vehicleType ?: secondary.vehicleType,
            coachNumber = primary.coachNumber ?: secondary.coachNumber,
            duration = primary.duration ?: secondary.duration,
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

    private fun styleFrom(root: JsonObject, loc: (String?) -> String?): TicketStyle? =
        TicketStyle(
            backgroundColor = root.string("backgroundColor"),
            foregroundColor = root.string("foregroundColor"),
            labelColor = root.string("labelColor"),
            stripColor = root.string("stripColor"),
            footerBackgroundColor = root.string("footerBackgroundColor"),
            logoText = loc(root.string("logoText")),
            logoSymbolName = root.string("logoSymbolName"),
        ).takeIf { !it.isEmpty }

    private fun journeyFrom(semantics: JsonObject?, category: TicketCategory): TicketJourney? {
        val mode = category.transportMode() ?: return null
        semantics ?: return null
        val from = semantics.stop("departure")
        val to = semantics.stop("destination")
        val carrier = semantics.string("transitProvider") ?: semantics.string("airlineCode")
        val serviceNumber = semantics.string("flightCode") ?: semantics.string("flightNumber")
        val journey = TicketJourney(
            mode = mode,
            carrier = carrier,
            serviceNumber = serviceNumber,
            from = from,
            to = to,
            boardingTime = (semantics.string("currentBoardingDate") ?: semantics.string("originalBoardingDate"))
                ?.let(::normalizePassDateTime),
            transitStatus = semantics.string("transitStatus"),
            transitStatusReason = semantics.string("transitStatusReason"),
            vehicleName = semantics.string("vehicleName"),
            vehicleNumber = semantics.string("vehicleNumber"),
            vehicleType = semantics.string("vehicleType"),
            coachNumber = semantics.string("carNumber"),
            duration = semantics.string("duration")?.toDoubleOrNull()?.let { formatDuration(it.toLong()) },
        )
        return journey.takeIf { it != TicketJourney(mode = mode) }
    }

    private fun formatDuration(seconds: Long): String? {
        if (seconds <= 0) return null
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    private fun JsonObject.stop(side: String): TicketStop? {
        val time = if (side == "departure") {
            string("currentDepartureDate") ?: string("originalDepartureDate")
        } else {
            string("currentArrivalDate") ?: string("originalArrivalDate")
        }?.let(::normalizePassDateTime)
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
            type = seat.string("seatType"),
            identifier = seat.string("seatIdentifier"),
            description = seat.string("seatDescription"),
        ).takeIf { it != TicketSeatInfo() }
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

    private class PkpassArchive(
        val passJson: String,
        val stringsByLanguage: Map<String, Map<String, String>>,
    ) {
        fun stringsFor(language: String): Map<String, String> =
            stringsByLanguage[language.lowercase()]
                ?: stringsByLanguage["en"]
                ?: stringsByLanguage.values.firstOrNull()
                ?: emptyMap()
    }

    private fun readArchive(bytes: ByteArray): PkpassArchive? = runCatching {
        var passJson: String? = null
        val strings = mutableMapOf<String, Map<String, String>>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                when {
                    entry.name == "pass.json" ->
                        passJson = zip.readBytes().toString(Charsets.UTF_8)

                    entry.name.endsWith("/pass.strings", ignoreCase = true) -> {
                        val language = entry.name
                            .substringBefore(".lproj", "")
                            .substringAfterLast('/')
                            .lowercase()
                            .split('-', '_')
                            .firstOrNull()
                        if (!language.isNullOrBlank()) {
                            strings[language] = parseStringsFile(zip.readBytes())
                        }
                    }
                }
            }
        }
        passJson?.let { PkpassArchive(it, strings) }
    }.getOrNull()

    internal fun parseStringsFile(bytes: ByteArray): Map<String, String> {
        val text = when {
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)

            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)

            else -> bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        }
        return STRINGS_ENTRY.findAll(text).associate { match ->
            unescapeStrings(match.groupValues[1]) to unescapeStrings(match.groupValues[2])
        }
    }

    private fun unescapeStrings(raw: String): String = buildString {
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            if (char == '\\' && index + 1 < raw.length) {
                when (val next = raw[index + 1]) {
                    'n' -> append('\n')
                    't' -> append('\t')
                    '"' -> append('"')
                    '\\' -> append('\\')
                    else -> append(next)
                }
                index += 2
            } else {
                append(char)
                index++
            }
        }
    }

    private val STRINGS_ENTRY = Regex(""""((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;""")

    private val ANCHOR_HREF =
        Regex("""<a\s[^>]*href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)


    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}
