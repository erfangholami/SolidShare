package com.erfangholami.solidshare.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap

const val MIME_TYPE_VIDEO = "video/mp4"
const val MIME_TYPE_IMAGE = "image/jpeg"
const val MIME_TYPE_OCTET_STREAM = "application/octet-stream"

fun createMediaUri(context: Context, isVideo: Boolean): Uri {
    return if (isVideo) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, createTakenVideoName())
            put(MediaStore.Video.Media.MIME_TYPE, MIME_TYPE_VIDEO)
        }
        context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Cannot create video capture URI")
    } else {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, createTakenImageName())
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE_IMAGE)
        }
        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Cannot create photo capture URI")
    }
}

fun createTakenVideoName(): String {
    val timestamp = getDateFormatted()
    return "video_$timestamp.mp4"
}

fun createTakenImageName(): String {
    val timestamp = getDateFormatted()
    return "photo_$timestamp.jpg"
}

fun createMediaName(extension: String?): String {
    val timestamp = getDateFormatted()
    return if (!extension.isNullOrEmpty()) "media_$timestamp.$extension" else "media_$timestamp"
}

fun getPickedFileName(context: Context, uri: Uri): String {
    val timestamp = getDateFormatted()
    return context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        ?: run {
            val ext = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(getVisualMediaType(context, uri)) ?: "bin"
            "file_$timestamp.$ext"
        }
}

fun getVisualMediaType(context: Context, uri: Uri): String {
    return context.contentResolver.getType(uri) ?: MIME_TYPE_OCTET_STREAM
}