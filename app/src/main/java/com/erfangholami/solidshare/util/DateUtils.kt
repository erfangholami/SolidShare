package com.erfangholami.solidshare.util

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale

private const val MEDIA_NAME_TIMESTAMP_FORMAT = "yyyyMMdd_HHmmss"

fun getDateFormatted(): String {
    return SimpleDateFormat(MEDIA_NAME_TIMESTAMP_FORMAT, Locale.getDefault()).format(Date())
}

fun epochMillisOrNull(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso) }.getOrNull()?.toEpochMilli()
        ?: runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { ZonedDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
}

fun formatRelativeTime(iso: String?): String? =
    formatRelativeTime(epochMillisOrNull(iso))

fun formatRelativeTime(epochMillis: Long?): String? {
    val millis = epochMillis ?: return null
    return DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
}