package com.erfangholami.solidshare.data.passimport

import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketEventInfo
import com.erfangholami.solidshare.domain.model.TicketSeatInfo
import com.erfangholami.solidshare.domain.model.TicketSource
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

        val category = when (styleKey) {
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
        val seat = TicketSeatInfo(
            number = fields.valueForKeys("seat", "seatNumber"),
            row = fields.valueForKeys("row", "seatRow"),
            section = fields.valueForKeys("section", "seatSection"),
        ).takeIf { it.number != null || it.row != null || it.section != null }

        val title = root.string("description")
            ?: root.string("logoText")
            ?: root.string("organizationName")
            ?: return null
        val eventName = style?.get("primaryFields")?.jsonArray
            ?.firstOrNull()?.jsonObject?.string("value")
        val eventStart = root.string("relevantDate")
        val event = TicketEventInfo(
            name = eventName,
            start = eventStart,
        ).takeIf { it.name != null || it.start != null }

        return TicketDraft(
            title = title,
            description = fieldLines.joinToString("\n").ifBlank { null },
            number = root.string("serialNumber"),
            token = token,
            barcodeFormat = barcodeFormat,
            category = category,
            issuer = root.string("organizationName"),
            seat = seat,
            event = event,
            validThrough = root.string("expirationDate"),
            source = TicketSource.PKPASS,
        )
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
