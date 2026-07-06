package com.erfangholami.solidshare.data.repo.tickets

import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketEventInfo
import com.erfangholami.solidshare.domain.model.TicketSeatInfo
import com.erfangholami.solidshare.domain.model.TicketSource
import com.erfangholami.solidshare.domain.model.TicketVenue
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class TicketQrEvent(
    val name: String? = null,
    val start: String? = null,
    val end: String? = null,
    val venue: String? = null,
    val address: String? = null,
)

@Serializable
private data class TicketQrSeat(
    val section: String? = null,
    val row: String? = null,
    val number: String? = null,
)

@Serializable
private data class TicketQrPayload(
    val solidshare: String? = null,
    val v: Int = 1,
    val title: String? = null,
    val category: String? = null,
    val token: String? = null,
    val format: String? = null,
    val number: String? = null,
    val holder: String? = null,
    val issuer: String? = null,
    val price: String? = null,
    val currency: String? = null,
    val description: String? = null,
    val event: TicketQrEvent? = null,
    val seat: TicketQrSeat? = null,
    val validFrom: String? = null,
    val validThrough: String? = null,
    val issued: String? = null,
)

object TicketQrCodec {

    const val LINK_PREFIX = "https://solidshare.app/t#"
    private const val MARKER = "ticket"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(raw: String): TicketDraft? {
        val trimmed = raw.trim()
        val payload = when {
            trimmed.startsWith(LINK_PREFIX) -> decodeLink(trimmed) ?: return null
            trimmed.startsWith("{") -> decodeJson(trimmed)?.takeIf { it.solidshare == MARKER }
                ?: return null

            else -> return null
        }
        val title = payload.title?.takeIf { it.isNotBlank() } ?: return null
        return TicketDraft(
            title = title,
            description = payload.description,
            number = payload.number,
            token = payload.token,
            barcodeFormat = payload.format
                ?.let { runCatching { TicketBarcodeFormat.valueOf(it) }.getOrNull() }
                ?: if (payload.token != null) TicketBarcodeFormat.QR_CODE
                else TicketBarcodeFormat.NONE,
            category = payload.category
                ?.let { runCatching { TicketCategory.valueOf(it) }.getOrNull() }
                ?: TicketCategory.GENERIC,
            issuer = payload.issuer,
            holder = payload.holder,
            seat = payload.seat
                ?.takeIf { it.section != null || it.row != null || it.number != null }
                ?.let { TicketSeatInfo(number = it.number, row = it.row, section = it.section) },
            price = payload.price,
            currency = payload.currency,
            dateIssued = payload.issued,
            event = payload.event
                ?.takeIf {
                    it.name != null || it.start != null || it.end != null ||
                            it.venue != null || it.address != null
                }
                ?.let {
                    TicketEventInfo(
                        name = it.name,
                        start = it.start,
                        end = it.end,
                        venue = if (it.venue != null || it.address != null) {
                            TicketVenue(it.venue, it.address)
                        } else {
                            null
                        },
                    )
                },
            validFrom = payload.validFrom,
            validThrough = payload.validThrough,
            source = TicketSource.SCAN,
        )
    }

    private fun decodeLink(link: String): TicketQrPayload? {
        val fragment = link.removePrefix(LINK_PREFIX)
        if (fragment.isBlank()) return null
        return runCatching {
            val bytes = Base64.getUrlDecoder().decode(fragment)
            decodeJson(String(bytes, Charsets.UTF_8))
        }.getOrNull()
    }

    private fun decodeJson(text: String): TicketQrPayload? =
        runCatching { json.decodeFromString<TicketQrPayload>(text) }.getOrNull()
}
