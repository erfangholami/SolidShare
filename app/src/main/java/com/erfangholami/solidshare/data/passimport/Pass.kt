package com.erfangholami.solidshare.data.passimport

import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.erfangholami.solidshare.domain.model.TicketBeaconInfo
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketEventInfo
import com.erfangholami.solidshare.domain.model.TicketExtra
import com.erfangholami.solidshare.domain.model.TicketJourney
import com.erfangholami.solidshare.domain.model.TicketLocationInfo
import com.erfangholami.solidshare.domain.model.TicketMembershipInfo
import com.erfangholami.solidshare.domain.model.TicketPassInfo
import com.erfangholami.solidshare.domain.model.TicketReservationInfo
import com.erfangholami.solidshare.domain.model.TicketSeatInfo
import com.erfangholami.solidshare.domain.model.TicketSource
import com.erfangholami.solidshare.domain.model.TicketStyle
import com.erfangholami.solidshare.domain.model.TicketWifiInfo
import com.erfangholami.solidshare.domain.model.TransportMode

class PassBarcode(
    val payload: String,
    val format: TicketBarcodeFormat,
    val encoding: String? = null,
    val altText: String? = null,
)

class PassData(
    val description: String? = null,
    val passInfo: TicketPassInfo? = null,
    val reservation: TicketReservationInfo? = null,
    val membership: TicketMembershipInfo? = null,
    val locations: List<TicketLocationInfo> = emptyList(),
    val beacons: List<TicketBeaconInfo> = emptyList(),
    val wifi: List<TicketWifiInfo> = emptyList(),
    val voided: Boolean? = null,
    val silenceRequested: Boolean? = null,
    val relevantStart: String? = null,
    val relevantEnd: String? = null,
)

sealed class Pass {
    abstract val title: String
    abstract val category: TicketCategory
    abstract val issuer: String?
    abstract val number: String?
    abstract val holder: String?
    abstract val barcode: PassBarcode?
    abstract val style: TicketStyle?
    abstract val extras: List<TicketExtra>
    abstract val validFrom: String?
    abstract val validThrough: String?
    abstract val source: TicketSource
    abstract val data: PassData

    protected open fun enrich(draft: TicketDraft): TicketDraft = draft

    fun toDraft(): TicketDraft = enrich(
        TicketDraft(
            title = title,
            description = data.description?.takeIf { it != title },
            number = number,
            token = barcode?.payload,
            barcodeFormat = barcode?.format ?: TicketBarcodeFormat.NONE,
            barcodeEncoding = barcode?.encoding,
            barcodeAltText = barcode?.altText,
            category = category,
            issuer = issuer,
            holder = holder,
            validFrom = validFrom,
            validThrough = validThrough,
            style = style,
            extras = extras,
            passInfo = data.passInfo,
            reservation = data.reservation,
            membership = data.membership,
            locations = data.locations,
            beacons = data.beacons,
            wifi = data.wifi,
            voided = data.voided,
            silenceRequested = data.silenceRequested,
            relevantStart = data.relevantStart,
            relevantEnd = data.relevantEnd,
            source = source,
        ),
    )

    protected fun extraValue(vararg keys: String): String? {
        val wanted = keys.map(::normalizePassKey)
        return extras.firstOrNull { extra ->
            extra.label?.let { normalizePassKey(it) in wanted } == true
        }?.value
    }
}

class TransportPass(
    override val title: String,
    val journey: TicketJourney,
    override val issuer: String? = null,
    override val number: String? = null,
    override val holder: String? = null,
    override val barcode: PassBarcode? = null,
    override val style: TicketStyle? = null,
    override val extras: List<TicketExtra> = emptyList(),
    override val validFrom: String? = null,
    override val validThrough: String? = null,
    override val source: TicketSource = TicketSource.MANUAL,
    override val data: PassData = PassData(),
    val seat: TicketSeatInfo? = null,
    val startIso: String? = null,
) : Pass() {

    override val category: TicketCategory = when (journey.mode) {
        TransportMode.FLIGHT -> TicketCategory.FLIGHT
        TransportMode.TRAIN -> TicketCategory.TRAIN
        TransportMode.BUS -> TicketCategory.BUS
        TransportMode.BOAT -> TicketCategory.BOAT
    }

    val mode: TransportMode get() = journey.mode
    val from: String? get() = journey.from?.code ?: journey.from?.name
    val to: String? get() = journey.to?.code ?: journey.to?.name
    val gate: String? get() = journey.from?.gate
    val terminal: String? get() = journey.from?.terminal
    val platform: String? get() = journey.from?.platform
    val departureTime: String? get() = journey.from?.time
    val arrivalTime: String? get() = journey.to?.time
    val serviceNumber: String? get() = journey.serviceNumber
    val boardingTime: String? get() =
        journey.boardingTime ?: extraValue("boarding", "boardingtime", "boardinguntil")
    val travelClass: String? get() = extraValue("class", "cabinclass", "travelclass", "fareclass", "cabin")

    override fun enrich(draft: TicketDraft): TicketDraft = draft.copy(
        journey = journey,
        seat = seat,
        event = startIso?.let { TicketEventInfo(start = it) },
    )
}

enum class EventKind { CONCERT, SPORTS, CINEMA, THEATER, CONFERENCE, SOCIAL, OTHER }

class EventPass(
    override val title: String,
    val event: TicketEventInfo,
    override val category: TicketCategory = TicketCategory.EVENT,
    override val issuer: String? = null,
    override val number: String? = null,
    override val holder: String? = null,
    override val barcode: PassBarcode? = null,
    override val style: TicketStyle? = null,
    override val extras: List<TicketExtra> = emptyList(),
    override val validFrom: String? = null,
    override val validThrough: String? = null,
    override val source: TicketSource = TicketSource.MANUAL,
    override val data: PassData = PassData(),
    val seat: TicketSeatInfo? = null,
    val price: String? = null,
    val currency: String? = null,
    val kind: EventKind = EventKind.OTHER,
) : Pass() {

    val eventName: String? get() = event.name
    val start: String? get() = event.start
    val venueName: String? get() = event.venue?.name

    override fun enrich(draft: TicketDraft): TicketDraft = draft.copy(
        event = event,
        seat = seat,
        price = price,
        currency = currency,
    )
}

class StorePass(
    override val title: String,
    override val issuer: String? = null,
    override val number: String? = null,
    override val holder: String? = null,
    override val barcode: PassBarcode? = null,
    override val style: TicketStyle? = null,
    override val extras: List<TicketExtra> = emptyList(),
    override val validFrom: String? = null,
    override val validThrough: String? = null,
    override val source: TicketSource = TicketSource.MANUAL,
    override val data: PassData = PassData(),
) : Pass() {

    override val category: TicketCategory = TicketCategory.LOYALTY

    val memberNumber: String? get() = number
    val balance: String? get() = extraValue("balance", "points", "pointsbalance", "credit", "saldo")
}

class CouponPass(
    override val title: String,
    override val issuer: String? = null,
    override val number: String? = null,
    override val holder: String? = null,
    override val barcode: PassBarcode? = null,
    override val style: TicketStyle? = null,
    override val extras: List<TicketExtra> = emptyList(),
    override val validFrom: String? = null,
    override val validThrough: String? = null,
    override val source: TicketSource = TicketSource.MANUAL,
    override val data: PassData = PassData(),
) : Pass() {

    override val category: TicketCategory = TicketCategory.COUPON

    val offer: String? get() = extraValue("offer", "discount", "deal", "promotion")
}

class GenericPass(
    override val title: String,
    override val category: TicketCategory = TicketCategory.GENERIC,
    override val issuer: String? = null,
    override val number: String? = null,
    override val holder: String? = null,
    override val barcode: PassBarcode? = null,
    override val style: TicketStyle? = null,
    override val extras: List<TicketExtra> = emptyList(),
    override val validFrom: String? = null,
    override val validThrough: String? = null,
    override val source: TicketSource = TicketSource.MANUAL,
    override val data: PassData = PassData(),
    val event: TicketEventInfo? = null,
    val seat: TicketSeatInfo? = null,
) : Pass() {

    override fun enrich(draft: TicketDraft): TicketDraft = draft.copy(
        event = event,
        seat = seat,
    )
}

internal fun normalizePassKey(raw: String): String =
    raw.lowercase().filter { it.isLetterOrDigit() }

private val BASIC_ISO_DATE_TIME =
    Regex("""(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})?(Z|[+-]\d{2}:?\d{2})?""")

private val EXTENDED_DATE_TIME =
    Regex("""(\d{4}-\d{2}-\d{2}T\d{2}:\d{2})(:\d{2}(?:\.\d+)?)?(Z|[+-]\d{2}:?\d{2})?""")

fun normalizePassDateTime(raw: String): String {
    BASIC_ISO_DATE_TIME.matchEntire(raw)?.let { match ->
        val values = match.groupValues
        val seconds = values[6].ifEmpty { "00" }
        val offset = normalizePassOffset(values[7])
        return "${values[1]}-${values[2]}-${values[3]}T${values[4]}:${values[5]}:$seconds$offset"
    }
    EXTENDED_DATE_TIME.matchEntire(raw)?.let { match ->
        val seconds = match.groupValues[2].ifEmpty { ":00" }
        val offset = normalizePassOffset(match.groupValues[3])
        return "${match.groupValues[1]}$seconds$offset"
    }
    return raw
}

private fun normalizePassOffset(offset: String): String = when {
    offset.isEmpty() || offset == "Z" || offset.contains(':') -> offset
    else -> "${offset.take(3)}:${offset.drop(3)}"
}
