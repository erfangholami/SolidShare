package com.erfangholami.solidshare.domain.model

enum class NotificationKind {
    ACCESS_OFFER,
    ACCESS_UPDATED,
    ACCESS_REVOKED,
    REQUEST_REJECTED,
    REQUEST_ACCEPTED,
    ACCESS_REQUEST,
    DECISION_GRANTED,
    DECISION_REJECTED,
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
