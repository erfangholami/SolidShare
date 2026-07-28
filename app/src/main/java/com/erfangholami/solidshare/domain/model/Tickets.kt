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
    val pending: Boolean = false,
)

@Serializable
data class TicketStyle(
    val backgroundColor: String? = null,
    val foregroundColor: String? = null,
    val labelColor: String? = null,
    val stripColor: String? = null,
    val footerBackgroundColor: String? = null,
    val logoText: String? = null,
    val logoSymbolName: String? = null,
) {
    val isEmpty: Boolean
        get() = backgroundColor == null && foregroundColor == null &&
            labelColor == null && stripColor == null && footerBackgroundColor == null &&
            logoText == null && logoSymbolName == null
}

@Serializable
data class TicketPassInfo(
    val passTypeIdentifier: String? = null,
    val serialNumber: String? = null,
    val teamIdentifier: String? = null,
    val webServiceUrl: String? = null,
    val authenticationToken: String? = null,
    val groupingIdentifier: String? = null,
    val sharingProhibited: Boolean? = null,
) {
    val isEmpty: Boolean
        get() = passTypeIdentifier == null && serialNumber == null && teamIdentifier == null &&
            webServiceUrl == null && authenticationToken == null &&
            groupingIdentifier == null && sharingProhibited == null
}

@Serializable
data class TicketLocationInfo(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val maxDistance: Int? = null,
    val relevantText: String? = null,
)

@Serializable
data class TicketBeaconInfo(
    val proximityUuid: String? = null,
    val major: Int? = null,
    val minor: Int? = null,
    val relevantText: String? = null,
)

@Serializable
data class TicketWifiInfo(
    val ssid: String? = null,
    val password: String? = null,
)

@Serializable
data class TicketReservationInfo(
    val boardingGroup: String? = null,
    val boardingZone: String? = null,
    val sequenceNumber: String? = null,
    val fareClass: String? = null,
    val priorityStatus: String? = null,
    val securityScreening: String? = null,
) {
    val isEmpty: Boolean
        get() = boardingGroup == null && boardingZone == null && sequenceNumber == null &&
            fareClass == null && priorityStatus == null && securityScreening == null
}

@Serializable
data class TicketMembershipInfo(
    val programName: String? = null,
    val number: String? = null,
    val status: String? = null,
    val pointsBalance: String? = null,
    val balance: String? = null,
    val balanceCurrency: String? = null,
) {
    val isEmpty: Boolean
        get() = programName == null && number == null && status == null &&
            pointsBalance == null && balance == null && balanceCurrency == null
}

enum class TicketExtraPlacement { HEADER, PRIMARY, SECONDARY, AUXILIARY, BACK, ADDITIONAL, FOOTER }

@Serializable
data class TicketExtra(
    val label: String? = null,
    val value: String,
    val placement: TicketExtraPlacement? = null,
    val changeMessage: String? = null,
    val textAlignment: String? = null,
    val linkUrl: String? = null,
)

@Serializable
data class TicketSeatInfo(
    val number: String? = null,
    val row: String? = null,
    val section: String? = null,
    val type: String? = null,
    val identifier: String? = null,
    val description: String? = null,
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
    val doorTime: String? = null,
    val venue: TicketVenue? = null,
    val performers: List<String> = emptyList(),
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
    val boardingTime: String? = null,
    val transitStatus: String? = null,
    val transitStatusReason: String? = null,
    val vehicleName: String? = null,
    val vehicleNumber: String? = null,
    val vehicleType: String? = null,
    val coachNumber: String? = null,
    val duration: String? = null,
)

@Serializable
data class TicketImageUris(
    val logo: String? = null,
    val icon: String? = null,
    val strip: String? = null,
    val thumbnail: String? = null,
    val footer: String? = null,
    val background: String? = null,
)

@Serializable
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
    val source: TicketSource = TicketSource.MANUAL,
    val artifactUri: String? = null,
    val images: TicketImageUris? = null,
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
