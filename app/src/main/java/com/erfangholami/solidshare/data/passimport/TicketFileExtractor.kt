package com.erfangholami.solidshare.data.passimport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class BarcodeHit(val payload: String, val format: TicketBarcodeFormat)

data class Extraction(val barcode: BarcodeHit?, val text: String?)

@Singleton
class TicketFileExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val scanner: BarcodeScanner by lazy {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(TicketScanFormats.first(), *TicketScanFormats.drop(1).toIntArray())
                .build(),
        )
    }

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun extract(bytes: ByteArray, type: TicketFileType): Extraction =
        withContext(Dispatchers.Default) {
            when (type) {
                TicketFileType.PDF -> extractPdf(bytes)
                TicketFileType.IMAGE -> {
                    val bitmap = decodeImage(bytes) ?: return@withContext Extraction(null, null)
                    val result = Extraction(scanBitmap(bitmap), recognizeBitmap(bitmap))
                    bitmap.recycle()
                    result
                }

                else -> Extraction(null, null)
            }
        }

    private suspend fun extractPdf(bytes: ByteArray): Extraction {
        val file = writeTemp(bytes) ?: return Extraction(null, null)
        var barcode: BarcodeHit? = null
        val text = StringBuilder()
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val pages = minOf(renderer.pageCount, MAX_PDF_PAGES)
                    for (index in 0 until pages) {
                        val bitmap = renderPage(renderer, index) ?: continue
                        if (barcode == null) barcode = scanBitmap(bitmap)
                        recognizeBitmap(bitmap)?.let { text.appendLine(it) }
                        bitmap.recycle()
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            file.delete()
        }
        return Extraction(barcode, text.toString().trim().ifBlank { null })
    }

    private fun renderPage(renderer: PdfRenderer, index: Int): Bitmap? = runCatching {
        renderer.openPage(index).use { page ->
            val scale = (TARGET_WIDTH.toFloat() / page.width).coerceAtLeast(1f)
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }.getOrNull()

    private fun decodeImage(bytes: ByteArray): Bitmap? =
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

    private suspend fun scanBitmap(bitmap: Bitmap): BarcodeHit? =
        awaitBarcodes(InputImage.fromBitmap(bitmap, 0))?.firstNotNullOfOrNull { barcode ->
            barcode.rawValue
                ?.takeIf { it.isNotBlank() }
                ?.let { BarcodeHit(it, mlKitFormatToDomain(barcode.format)) }
        }

    private suspend fun recognizeBitmap(bitmap: Bitmap): String? =
        awaitText(InputImage.fromBitmap(bitmap, 0))?.takeIf { it.isNotBlank() }

    private suspend fun awaitBarcodes(image: InputImage): List<Barcode>? =
        suspendCancellableCoroutine { continuation ->
            scanner.process(image)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(null) }
        }

    private suspend fun awaitText(image: InputImage): String? =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { continuation.resume(it.text) }
                .addOnFailureListener { continuation.resume(null) }
        }

    private fun writeTemp(bytes: ByteArray): File? = runCatching {
        File.createTempFile("ticket-pdf-", ".pdf", context.cacheDir).apply { writeBytes(bytes) }
    }.getOrNull()

    private companion object {
        const val MAX_PDF_PAGES = 3
        const val TARGET_WIDTH = 1600
    }
}
