package com.erfangholami.solidshare.presentation.util

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

suspend fun Clipboard.copyText(text: String, label: String = "text") {
    setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
}

suspend fun Clipboard.pasteText(): String? =
    getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
