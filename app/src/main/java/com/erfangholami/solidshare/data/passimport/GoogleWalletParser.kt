package com.erfangholami.solidshare.data.passimport

import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketEventInfo
import com.erfangholami.solidshare.domain.model.TicketSource
import com.erfangholami.solidshare.domain.model.TicketVenue
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object GoogleWalletParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val objectArrays = listOf(
        "eventTicketObjects" to TicketCategory.EVENT,
        "flightObjects" to TicketCategory.FLIGHT,
        "transitObjects" to TicketCategory.TRAIN,
        "loyaltyObjects" to TicketCategory.LOYALTY,
        "offerObjects" to TicketCategory.COUPON,
        "giftCardObjects" to TicketCategory.LOYALTY,
        "genericObjects" to TicketCategory.GENERIC,
    )

    fun matches(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.contains("pay.google.com/gp/v/save/") ||
                trimmed.contains("wallet.google.com/gw/program/save/") ||
                trimmed.contains("pay.google.com/gp/v/object/")
    }

    fun parse(raw: String): TicketDraft? {
        if (!matches(raw)) return null
        val link = raw.trim()
        val jwt = link.substringAfter("/save/", "")
            .substringBefore('?')
            .substringBefore('#')
        val payload = decodeJwtPayload(jwt)
        val fallback = TicketDraft(
            title = "Google Wallet",
            description = link,
            source = TicketSource.GOOGLE_WALLET,
        )
        if (payload == null) return fallback

        val walletPayload = payload["payload"]?.jsonObject ?: return fallback
        val (arrayKey, category) = objectArrays.firstOrNull { (key, _) ->
            walletPayload[key]?.jsonArray?.isNotEmpty() == true
        } ?: return fallback
        val pass = walletPayload[arrayKey]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return fallback

        val barcode = pass["barcode"]?.jsonObject
        val token = barcode?.string("value")
        val barcodeFormat = barcode?.string("type")?.normalizeBarcodeType()
            ?: if (token != null) TicketBarcodeFormat.QR_CODE else TicketBarcodeFormat.NONE

        val title = pass.localized("eventName")
            ?: pass.localized("header")
            ?: pass.localized("localizedIssuerName")
            ?: pass.string("issuerName")
            ?: fallback.title
        val venueObject = pass["venue"]?.jsonObject
        val venue = TicketVenue(
            name = venueObject?.localized("name"),
            address = venueObject?.localized("address"),
        ).takeIf { it.name != null || it.address != null }
        val start = pass["dateTime"]?.jsonObject?.string("start")
        val event = TicketEventInfo(
            name = pass.localized("eventName"),
            start = start,
            venue = venue,
        ).takeIf { it.name != null || it.start != null || it.venue != null }

        return TicketDraft(
            title = title,
            description = if (token == null) link else null,
            number = pass.string("ticketNumber") ?: pass.string("id"),
            token = token,
            barcodeFormat = barcodeFormat,
            category = category,
            issuer = pass.localized("localizedIssuerName") ?: pass.string("issuerName"),
            holder = pass.string("ticketHolderName"),
            seat = null,
            event = event,
            validThrough = pass["validTimeInterval"]?.jsonObject
                ?.get("end")?.jsonObject?.string("date"),
            source = TicketSource.GOOGLE_WALLET,
        )
    }

    private fun decodeJwtPayload(jwt: String): JsonObject? {
        val segment = jwt.split('.').getOrNull(1) ?: return null
        if (segment.isBlank()) return null
        return runCatching {
            val padded = segment.padEnd((segment.length + 3) / 4 * 4, '=')
            val decoded = Base64.getUrlDecoder().decode(padded)
            json.parseToJsonElement(String(decoded, Charsets.UTF_8)).jsonObject
        }.getOrNull()
    }

    private fun String.normalizeBarcodeType(): TicketBarcodeFormat {
        val normalized = replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .uppercase()
            .removePrefix("BARCODE_TYPE_")
        return when (normalized) {
            "QR_CODE", "QRCODE" -> TicketBarcodeFormat.QR_CODE
            "AZTEC" -> TicketBarcodeFormat.AZTEC
            "PDF_417", "PDF417" -> TicketBarcodeFormat.PDF_417
            "CODE_128" -> TicketBarcodeFormat.CODE_128
            "CODE_39" -> TicketBarcodeFormat.CODE_39
            "EAN_13" -> TicketBarcodeFormat.EAN_13
            "EAN_8" -> TicketBarcodeFormat.EAN_8
            "UPC_A" -> TicketBarcodeFormat.UPC_A
            "UPC_E" -> TicketBarcodeFormat.UPC_E
            "ITF_14", "ITF" -> TicketBarcodeFormat.ITF
            "CODABAR" -> TicketBarcodeFormat.CODABAR
            "DATA_MATRIX" -> TicketBarcodeFormat.DATA_MATRIX
            else -> TicketBarcodeFormat.QR_CODE
        }
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.localized(key: String): String? {
        val node = this[key] ?: return null
        val obj = runCatching { node.jsonObject }.getOrNull()
            ?: return node.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }
        return obj["defaultValue"]?.jsonObject?.string("value")
            ?: obj.string("value")
    }
}
