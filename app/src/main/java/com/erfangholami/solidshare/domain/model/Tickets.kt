package com.erfangholami.solidshare.domain.model

import kotlinx.serialization.Serializable

enum class TicketCategory { EVENT, FLIGHT, TRAIN, BUS, BOAT, CINEMA, LOYALTY, COUPON, GENERIC }

enum class TicketBarcodeFormat {
    QR_CODE, AZTEC, PDF_417, CODE_128, CODE_39, CODE_93,
    EAN_13, EAN_8, UPC_A, UPC_E, ITF, CODABAR, DATA_MATRIX, NONE,
}

enum class TicketSource { MANUAL, SCAN, PKPASS, GOOGLE_WALLET, PDF, IMAGE, LINK, BCBP, UIC }

enum class TransportMode { FLIGHT, TRAIN, BUS, BOAT }

data class TicketSummaryItem(
    val uri: String,
    val title: String,
    val category: TicketCategory = TicketCategory.GENERIC,
    val eventStart: String? = null,
    val issuer: String? = null,
    val validThrough: String? = null,
    val backgroundColor: String? = null,
    val foregroundColor: String? = null,
)

@Serializable
data class TicketStyle(
    val backgroundColor: String? = null,
    val foregroundColor: String? = null,
    val labelColor: String? = null,
    val logoText: String? = null,
) {
    val isEmpty: Boolean
        get() = backgroundColor == null && foregroundColor == null &&
            labelColor == null && logoText == null
}

enum class TicketExtraPlacement { HEADER, PRIMARY, SECONDARY, AUXILIARY, BACK, ADDITIONAL }

@Serializable
data class TicketExtra(
    val label: String? = null,
    val value: String,
    val placement: TicketExtraPlacement? = null,
)

@Serializable
data class TicketSeatInfo(
    val number: String? = null,
    val row: String? = null,
    val section: String? = null,
)

@Serializable
data class TicketVenue(
    val name: String? = null,
    val address: String? = null,
)

@Serializable
data class TicketEventInfo(
    val name: String? = null,
    val start: String? = null,
    val end: String? = null,
    val venue: TicketVenue? = null,
)

@Serializable
data class TicketStop(
    val name: String? = null,
    val code: String? = null,
    val cityName: String? = null,
    val time: String? = null,
    val terminal: String? = null,
    val gate: String? = null,
    val platform: String? = null,
)

@Serializable
data class TicketJourney(
    val mode: TransportMode,
    val carrier: String? = null,
    val serviceNumber: String? = null,
    val from: TicketStop? = null,
    val to: TicketStop? = null,
    val duration: String? = null,
)

data class Ticket(
    val uri: String,
    val title: String,
    val description: String? = null,
    val number: String? = null,
    val token: String? = null,
    val barcodeFormat: TicketBarcodeFormat = TicketBarcodeFormat.NONE,
    val barcodeEncoding: String? = null,
    val barcodeAltText: String? = null,
    val category: TicketCategory = TicketCategory.GENERIC,
    val issuer: String? = null,
    val holder: String? = null,
    val seat: TicketSeatInfo? = null,
    val price: String? = null,
    val currency: String? = null,
    val dateIssued: String? = null,
    val event: TicketEventInfo? = null,
    val journey: TicketJourney? = null,
    val validFrom: String? = null,
    val validThrough: String? = null,
    val style: TicketStyle? = null,
    val extras: List<TicketExtra> = emptyList(),
    val source: TicketSource = TicketSource.MANUAL,
    val artifactUri: String? = null,
    val createdAt: String? = null,
    val modifiedAt: String? = null,
)

@Serializable
data class TicketDraft(
    val title: String = "",
    val description: String? = null,
    val number: String? = null,
    val token: String? = null,
    val barcodeFormat: TicketBarcodeFormat = TicketBarcodeFormat.NONE,
    val barcodeEncoding: String? = null,
    val barcodeAltText: String? = null,
    val category: TicketCategory = TicketCategory.GENERIC,
    val issuer: String? = null,
    val holder: String? = null,
    val seat: TicketSeatInfo? = null,
    val price: String? = null,
    val currency: String? = null,
    val dateIssued: String? = null,
    val event: TicketEventInfo? = null,
    val journey: TicketJourney? = null,
    val validFrom: String? = null,
    val validThrough: String? = null,
    val style: TicketStyle? = null,
    val extras: List<TicketExtra> = emptyList(),
    val source: TicketSource = TicketSource.MANUAL,
)

data class TicketFile(
    val contentType: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TicketFile) return false
        return contentType == other.contentType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * contentType.hashCode() + bytes.contentHashCode()
}
