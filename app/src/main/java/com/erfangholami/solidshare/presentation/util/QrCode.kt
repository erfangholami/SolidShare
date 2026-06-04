package com.erfangholami.solidshare.presentation.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.erfangholami.solidshare.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

fun generateQrBitmap(
    content: String,
    sizePx: Int,
    foreground: Int = Color.BLACK,
    background: Int = Color.WHITE,
    logo: Bitmap? = null,
): Bitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to if (logo != null) ErrorCorrectionLevel.H else ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val matrix: BitMatrix = MultiFormatWriter()
        .encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)

    val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    for (x in 0 until matrix.width) {
        for (y in 0 until matrix.height) {
            bitmap.setPixel(x, y, if (matrix[x, y]) foreground else background)
        }
    }
    if (logo != null) drawCenteredLogo(bitmap, logo, background)
    return bitmap
}

@Composable
fun rememberQrLogo(@DrawableRes resId: Int = R.drawable.logo): Bitmap? {
    val context = LocalContext.current
    return remember(resId) { loadQrLogo(context, resId) }
}

fun loadQrLogo(context: Context, @DrawableRes resId: Int): Bitmap? {
    val drawable = ContextCompat.getDrawable(context, resId) ?: return null
    val width = drawable.intrinsicWidth.coerceAtLeast(1)
    val height = drawable.intrinsicHeight.coerceAtLeast(1)
    return drawable.toBitmap(width, height)
}

private fun drawCenteredLogo(qr: Bitmap, logo: Bitmap, plateColor: Int) {
    val canvas = Canvas(qr)
    val targetWidth = qr.width * 0.22f
    val targetHeight = targetWidth * logo.height / logo.width
    val left = (qr.width - targetWidth) / 2f
    val top = (qr.height - targetHeight) / 2f
    val logoRect = RectF(left, top, left + targetWidth, top + targetHeight)

    val pad = targetWidth * 0.16f
    val plate = RectF(
        logoRect.left - pad,
        logoRect.top - pad,
        logoRect.right + pad,
        logoRect.bottom + pad,
    )
    val radius = pad * 1.4f
    val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = plateColor }
    canvas.drawRoundRect(plate, radius, radius, platePaint)

    val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    canvas.drawBitmap(logo, null, logoRect, logoPaint)
}

fun generateQrWithCaptionBitmap(
    content: String,
    caption: String,
    qrSizePx: Int = 1024,
    logo: Bitmap? = null,
): Bitmap {
    val qr = generateQrBitmap(content, qrSizePx, logo = logo)
    val padding = qrSizePx / 16
    val textSize = qrSizePx / 24f

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        this.textSize = textSize
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    val lines = wrapText(caption, textPaint, qrSizePx.toFloat())
    val lineHeight = (textPaint.descent() - textPaint.ascent()).toInt()
    val captionHeight = lineHeight * lines.size + padding * 2

    val composed = Bitmap.createBitmap(
        qrSizePx + padding * 2,
        qrSizePx + captionHeight + padding,
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(composed)
    canvas.drawColor(Color.WHITE)
    canvas.drawBitmap(qr, padding.toFloat(), padding.toFloat(), null)

    var y = qrSizePx + padding * 2 - textPaint.ascent()
    lines.forEach { line ->
        val width = textPaint.measureText(line)
        canvas.drawText(line, (composed.width - width) / 2f, y, textPaint)
        y += lineHeight
    }
    return composed
}

private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
    if (paint.measureText(text) <= maxWidth) return listOf(text)
    val lines = mutableListOf<String>()
    val bounds = Rect()
    var remaining = text
    while (remaining.isNotEmpty()) {
        var end = remaining.length
        while (end > 0) {
            paint.getTextBounds(remaining, 0, end, bounds)
            if (bounds.width() <= maxWidth) break
            end--
        }
        if (end == 0) end = 1
        lines += remaining.substring(0, end)
        remaining = remaining.substring(end)
    }
    return lines
}
