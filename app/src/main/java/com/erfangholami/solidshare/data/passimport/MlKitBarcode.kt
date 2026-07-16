package com.erfangholami.solidshare.data.passimport

import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.google.mlkit.vision.barcode.common.Barcode

fun mlKitFormatToDomain(format: Int): TicketBarcodeFormat = when (format) {
    Barcode.FORMAT_QR_CODE -> TicketBarcodeFormat.QR_CODE
    Barcode.FORMAT_AZTEC -> TicketBarcodeFormat.AZTEC
    Barcode.FORMAT_PDF417 -> TicketBarcodeFormat.PDF_417
    Barcode.FORMAT_CODE_128 -> TicketBarcodeFormat.CODE_128
    Barcode.FORMAT_CODE_39 -> TicketBarcodeFormat.CODE_39
    Barcode.FORMAT_CODE_93 -> TicketBarcodeFormat.CODE_93
    Barcode.FORMAT_EAN_13 -> TicketBarcodeFormat.EAN_13
    Barcode.FORMAT_EAN_8 -> TicketBarcodeFormat.EAN_8
    Barcode.FORMAT_UPC_A -> TicketBarcodeFormat.UPC_A
    Barcode.FORMAT_UPC_E -> TicketBarcodeFormat.UPC_E
    Barcode.FORMAT_ITF -> TicketBarcodeFormat.ITF
    Barcode.FORMAT_CODABAR -> TicketBarcodeFormat.CODABAR
    Barcode.FORMAT_DATA_MATRIX -> TicketBarcodeFormat.DATA_MATRIX
    else -> TicketBarcodeFormat.QR_CODE
}

val TicketScanFormats: IntArray = intArrayOf(
    Barcode.FORMAT_QR_CODE,
    Barcode.FORMAT_AZTEC,
    Barcode.FORMAT_PDF417,
    Barcode.FORMAT_CODE_128,
    Barcode.FORMAT_CODE_39,
    Barcode.FORMAT_CODE_93,
    Barcode.FORMAT_EAN_13,
    Barcode.FORMAT_EAN_8,
    Barcode.FORMAT_UPC_A,
    Barcode.FORMAT_UPC_E,
    Barcode.FORMAT_ITF,
    Barcode.FORMAT_CODABAR,
    Barcode.FORMAT_DATA_MATRIX,
)
