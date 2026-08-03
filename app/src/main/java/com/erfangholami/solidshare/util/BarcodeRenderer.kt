package com.erfangholami.solidshare.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object BarcodeRenderer {

    fun render(
        payload: String,
        format: TicketBarcodeFormat,
        widthPx: Int,
        heightPx: Int,
        encoding: String? = null,
    ): Bitmap? {
        val zxingFormat = format.toZxing() ?: return null
        return runCatching {
            val hints = buildMap<EncodeHintType, Any> {
                put(EncodeHintType.MARGIN, 1)
                if (format.is2d()) {
                    put(EncodeHintType.CHARACTER_SET, charsetFor(encoding))
                }
                if (format == TicketBarcodeFormat.QR_CODE) {
                    put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                }
            }
            val matrix = MultiFormatWriter().encode(payload, zxingFormat, widthPx, heightPx, hints)
            val pixels = IntArray(matrix.width * matrix.height)
            for (y in 0 until matrix.height) {
                val row = y * matrix.width
                for (x in 0 until matrix.width) {
                    pixels[row + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            val bitmap = createBitmap(matrix.width, matrix.height)
            bitmap.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
            bitmap
        }.getOrNull()
    }

    private fun charsetFor(encoding: String?): String = when (encoding?.lowercase()?.replace("_", "-")) {
        "iso-8859-1", "latin-1", "latin1" -> "ISO-8859-1"
        "utf-8", "utf8" -> "UTF-8"
        null -> "UTF-8"
        else -> encoding
    }

    fun TicketBarcodeFormat.is2d(): Boolean = this in setOf(
        TicketBarcodeFormat.QR_CODE,
        TicketBarcodeFormat.AZTEC,
        TicketBarcodeFormat.DATA_MATRIX,
    )

    private fun TicketBarcodeFormat.toZxing(): BarcodeFormat? = when (this) {
        TicketBarcodeFormat.QR_CODE -> BarcodeFormat.QR_CODE
        TicketBarcodeFormat.AZTEC -> BarcodeFormat.AZTEC
        TicketBarcodeFormat.PDF_417 -> BarcodeFormat.PDF_417
        TicketBarcodeFormat.CODE_128 -> BarcodeFormat.CODE_128
        TicketBarcodeFormat.CODE_39 -> BarcodeFormat.CODE_39
        TicketBarcodeFormat.CODE_93 -> BarcodeFormat.CODE_93
        TicketBarcodeFormat.EAN_13 -> BarcodeFormat.EAN_13
        TicketBarcodeFormat.EAN_8 -> BarcodeFormat.EAN_8
        TicketBarcodeFormat.UPC_A -> BarcodeFormat.UPC_A
        TicketBarcodeFormat.UPC_E -> BarcodeFormat.UPC_E
        TicketBarcodeFormat.ITF -> BarcodeFormat.ITF
        TicketBarcodeFormat.CODABAR -> BarcodeFormat.CODABAR
        TicketBarcodeFormat.DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
        TicketBarcodeFormat.NONE -> null
    }
}
