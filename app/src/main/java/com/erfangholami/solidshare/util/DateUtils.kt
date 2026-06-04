package com.erfangholami.solidshare.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MEDIA_NAME_TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss"

fun getDateFormatted(): String {
    return SimpleDateFormat(MEDIA_NAME_TIMESTAMP_FORMAT, Locale.getDefault()).format(Date())
}