package com.erfangholami.solidshare.domain.model

enum class ShareMode {
    READ,
    APPEND,
    WRITE,
}

sealed interface ShareReceiver {
    data class WebIdReceiver(val webId: String) : ShareReceiver
    data class GroupReceiver(val groupUri: String) : ShareReceiver
    data object Public : ShareReceiver
}

data class GivenShare(
    val receiver: ShareReceiver,
    val mode: ShareMode,
    val resourceUri: String,
)

data class ReceivedShare(
    val ownerWebId: String,
    val mode: ShareMode,
    val resourceUri: String,
)

data class ShareRequest(
    val requestUri: String,
    val requesterWebId: String,
    val resourceUri: String,
    val requestedMode: ShareMode,
    val summary: String?,
    val publishedAt: String?,
)

data class ParsedShareLink(
    val resourceUri: String,
    val ownerWebId: String?,
)

data class CatalogEntry(
    val resourceUri: String,
    val title: String,
    val description: String?,
    val depictionUri: String?,
)

enum class AccessGrantDirection {
    GIVEN,
    RECEIVED,
    INCOMING_REQUEST,
}

enum class AccessGrantStatus {
    ACTIVE,
    PENDING,
}

enum class AccessGrantSource {
    APP_INDEX,
    SAI_REGISTRY,
}

data class AccessGrant(
    val direction: AccessGrantDirection,
    val counterpartWebId: String,
    val resourceUri: String,
    val mode: ShareMode,
    val status: AccessGrantStatus,
    val source: AccessGrantSource,
    val grantedAt: String?,
    val requestUri: String?,
)

enum class ShareNotificationType {
    OFFER,
    UNDO,
    REJECT,
    ACCEPTED,
}

data class ShareNotification(
    val notificationUri: String,
    val type: ShareNotificationType,
    val ownerWebId: String,
    val resourceUri: String,
    val mode: ShareMode?,
    val summary: String?,
    val publishedAt: String?,
)
