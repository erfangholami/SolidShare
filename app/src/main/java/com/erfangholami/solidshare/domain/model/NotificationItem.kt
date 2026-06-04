package com.erfangholami.solidshare.domain.model

enum class NotificationKind {
    ACCESS_OFFER,
    ACCESS_REVOKED,
    REQUEST_REJECTED,
    REQUEST_ACCEPTED,
    ACCESS_REQUEST,
}

data class NotificationItem(
    val id: String,
    val kind: NotificationKind,
    val counterpartWebId: String,
    val resourceUri: String,
    val mode: ShareMode?,
    val summary: String?,
    val publishedAt: String?,
    val requestUri: String?,
)
