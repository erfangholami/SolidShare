package com.erfangholami.solidshare.data.repo.sharing

import com.erfangholami.solidshare.domain.model.AccessGrant
import com.erfangholami.solidshare.domain.model.AccessGrantDirection
import com.erfangholami.solidshare.domain.model.AccessGrantSource
import com.erfangholami.solidshare.domain.model.AccessGrantStatus
import com.erfangholami.solidshare.domain.model.CatalogEntry
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.ParsedShareLink
import com.erfangholami.solidshare.domain.model.ReceivedShare
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareNotification
import com.erfangholami.solidshare.domain.model.ShareNotificationType
import com.erfangholami.solidshare.domain.model.ShareReceiver
import com.erfangholami.solidshare.domain.model.ShareRequest
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrant as LibAccessGrant
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrantDirection as LibAccessGrantDirection
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrantSource as LibAccessGrantSource
import com.erfangholami.androidsolidservices.shared.model.sharing.AccessGrantStatus as LibAccessGrantStatus
import com.erfangholami.androidsolidservices.shared.model.sharing.CatalogEntry as LibCatalogEntry
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare as LibGivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ParsedShareLink as LibParsedShareLink
import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare as LibReceivedShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode as LibShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotification as LibShareNotification
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareNotificationType as LibShareNotificationType
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver as LibShareReceiver
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareRequest as LibShareRequest

fun LibShareMode.toDomain(): ShareMode = when (this) {
    LibShareMode.READ -> ShareMode.READ
    LibShareMode.APPEND -> ShareMode.APPEND
    LibShareMode.WRITE -> ShareMode.WRITE
}

fun ShareMode.toLib(): LibShareMode = when (this) {
    ShareMode.READ -> LibShareMode.READ
    ShareMode.APPEND -> LibShareMode.APPEND
    ShareMode.WRITE -> LibShareMode.WRITE
}

fun LibShareReceiver.toDomain(): ShareReceiver = when (this) {
    is LibShareReceiver.WebIdReceiver -> ShareReceiver.WebIdReceiver(webId)
    is LibShareReceiver.GroupReceiver -> ShareReceiver.GroupReceiver(groupUri)
    is LibShareReceiver.Public -> ShareReceiver.Public
}

fun ShareReceiver.toLib(): LibShareReceiver = when (this) {
    is ShareReceiver.WebIdReceiver -> LibShareReceiver.WebIdReceiver(webId)
    is ShareReceiver.GroupReceiver -> LibShareReceiver.GroupReceiver(groupUri)
    is ShareReceiver.Public -> LibShareReceiver.Public
}

fun LibGivenShare.toDomain(): GivenShare =
    GivenShare(receiver.toDomain(), mode.toDomain(), resourceUri)

fun LibReceivedShare.toDomain(): ReceivedShare =
    ReceivedShare(ownerWebId, mode.toDomain(), resourceUri)

fun LibShareRequest.toDomain(): ShareRequest = ShareRequest(
    requestUri = requestUri,
    requesterWebId = requesterWebId,
    resourceUri = resourceUri,
    requestedMode = requestedMode.toDomain(),
    summary = summary,
    publishedAt = publishedAt,
)

fun ShareRequest.toLib(): LibShareRequest = LibShareRequest(
    requestUri = requestUri,
    requesterWebId = requesterWebId,
    resourceUri = resourceUri,
    requestedMode = requestedMode.toLib(),
    summary = summary,
    publishedAt = publishedAt,
)

fun LibParsedShareLink.toDomain(): ParsedShareLink = ParsedShareLink(resourceUri, ownerWebId)

fun LibCatalogEntry.toDomain(): CatalogEntry =
    CatalogEntry(resourceUri, title, description, depictionUri)

fun CatalogEntry.toLib(): LibCatalogEntry =
    LibCatalogEntry(resourceUri, title, description, depictionUri)

fun LibAccessGrantDirection.toDomain(): AccessGrantDirection = when (this) {
    LibAccessGrantDirection.GIVEN -> AccessGrantDirection.GIVEN
    LibAccessGrantDirection.RECEIVED -> AccessGrantDirection.RECEIVED
    LibAccessGrantDirection.INCOMING_REQUEST -> AccessGrantDirection.INCOMING_REQUEST
}

fun LibAccessGrantStatus.toDomain(): AccessGrantStatus = when (this) {
    LibAccessGrantStatus.ACTIVE -> AccessGrantStatus.ACTIVE
    LibAccessGrantStatus.PENDING -> AccessGrantStatus.PENDING
}

fun LibAccessGrantSource.toDomain(): AccessGrantSource = when (this) {
    LibAccessGrantSource.APP_INDEX -> AccessGrantSource.APP_INDEX
    LibAccessGrantSource.SAI_REGISTRY -> AccessGrantSource.SAI_REGISTRY
}

fun LibAccessGrant.toDomain(): AccessGrant = AccessGrant(
    direction = direction.toDomain(),
    counterpartWebId = counterpartWebId,
    resourceUri = resourceUri,
    mode = mode.toDomain(),
    status = status.toDomain(),
    source = source.toDomain(),
    grantedAt = grantedAt,
    requestUri = requestUri,
)

fun LibShareNotificationType.toDomain(): ShareNotificationType = when (this) {
    LibShareNotificationType.OFFER -> ShareNotificationType.OFFER
    LibShareNotificationType.UNDO -> ShareNotificationType.UNDO
    LibShareNotificationType.REJECT -> ShareNotificationType.REJECT
    LibShareNotificationType.ACCEPTED -> ShareNotificationType.ACCEPTED
}

fun LibShareNotification.toDomain(): ShareNotification = ShareNotification(
    notificationUri = notificationUri,
    type = type.toDomain(),
    ownerWebId = ownerWebId,
    resourceUri = resourceUri,
    mode = mode?.toDomain(),
    summary = summary,
    publishedAt = publishedAt,
)
