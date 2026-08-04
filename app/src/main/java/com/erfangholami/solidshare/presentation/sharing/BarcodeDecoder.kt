package com.erfangholami.solidshare.presentation.sharing

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import zxingcpp.BarcodeReader

data class ScannedBarcode(
    val value: String,
    val format: TicketBarcodeFormat,
)

/**
 * Decodes barcodes from camera frames and picked images.
 *
 * Backed by zxing-cpp, which is free software and ships in every distribution. ML Kit was the
 * previous decoder and is deliberately gone: it is proprietary and pulls Google Play Services in
 * with it, which F-Droid's inclusion policy rejects outright, and it added roughly 20 MB of native
 * libraries across the four ABIs.
 *
 * Both [decode] overloads block, and are called from CameraX's analysis executor or a background
 * dispatcher — never from the main thread.
 *
 * @param formats the accepted symbologies. Narrowing the set materially improves both hit rate and
 *   latency, so callers pass the smallest set they can accept: the share/profile scanner wants QR
 *   only, the ticket scanner accepts the full ticket range.
 */
class BarcodeDecoder(formats: List<TicketBarcodeFormat>) {

    private val reader = BarcodeReader(
        BarcodeReader.Options(
            formats = formats.mapNotNull { it.toZxing() }.toSet()
                .ifEmpty { setOf(BarcodeReader.Format.QR_CODE) },
            tryHarder = true,
            tryRotate = true,
            tryInvert = true,
        ),
    )

    fun decode(proxy: ImageProxy): ScannedBarcode? =
        runCatching { reader.read(proxy) }.getOrNull().firstUsable()

    fun decode(bitmap: Bitmap): ScannedBarcode? =
        runCatching { reader.read(bitmap) }.getOrNull().firstUsable()

    private fun List<BarcodeReader.Result>?.firstUsable(): ScannedBarcode? =
        this?.firstNotNullOfOrNull { result ->
            result.text
                ?.takeIf { it.isNotBlank() }
                ?.let { ScannedBarcode(it, result.format.toTicketFormat()) }
        }
}

private fun TicketBarcodeFormat.toZxing(): BarcodeReader.Format? = when (this) {
    TicketBarcodeFormat.QR_CODE -> BarcodeReader.Format.QR_CODE
    TicketBarcodeFormat.AZTEC -> BarcodeReader.Format.AZTEC
    TicketBarcodeFormat.PDF_417 -> BarcodeReader.Format.PDF_417
    TicketBarcodeFormat.CODE_128 -> BarcodeReader.Format.CODE_128
    TicketBarcodeFormat.CODE_39 -> BarcodeReader.Format.CODE_39
    TicketBarcodeFormat.CODE_93 -> BarcodeReader.Format.CODE_93
    TicketBarcodeFormat.EAN_13 -> BarcodeReader.Format.EAN_13
    TicketBarcodeFormat.EAN_8 -> BarcodeReader.Format.EAN_8
    TicketBarcodeFormat.UPC_A -> BarcodeReader.Format.UPC_A
    TicketBarcodeFormat.UPC_E -> BarcodeReader.Format.UPC_E
    TicketBarcodeFormat.ITF -> BarcodeReader.Format.ITF
    TicketBarcodeFormat.CODABAR -> BarcodeReader.Format.CODABAR
    TicketBarcodeFormat.DATA_MATRIX -> BarcodeReader.Format.DATA_MATRIX
    TicketBarcodeFormat.NONE -> null
}

private fun BarcodeReader.Format.toTicketFormat(): TicketBarcodeFormat = when (this) {
    BarcodeReader.Format.QR_CODE -> TicketBarcodeFormat.QR_CODE
    BarcodeReader.Format.AZTEC -> TicketBarcodeFormat.AZTEC
    BarcodeReader.Format.PDF_417 -> TicketBarcodeFormat.PDF_417
    BarcodeReader.Format.CODE_128 -> TicketBarcodeFormat.CODE_128
    BarcodeReader.Format.CODE_39 -> TicketBarcodeFormat.CODE_39
    BarcodeReader.Format.CODE_93 -> TicketBarcodeFormat.CODE_93
    BarcodeReader.Format.EAN_13 -> TicketBarcodeFormat.EAN_13
    BarcodeReader.Format.EAN_8 -> TicketBarcodeFormat.EAN_8
    BarcodeReader.Format.UPC_A -> TicketBarcodeFormat.UPC_A
    BarcodeReader.Format.UPC_E -> TicketBarcodeFormat.UPC_E
    BarcodeReader.Format.ITF -> TicketBarcodeFormat.ITF
    BarcodeReader.Format.CODABAR -> TicketBarcodeFormat.CODABAR
    BarcodeReader.Format.DATA_MATRIX -> TicketBarcodeFormat.DATA_MATRIX
    else -> TicketBarcodeFormat.NONE
}
