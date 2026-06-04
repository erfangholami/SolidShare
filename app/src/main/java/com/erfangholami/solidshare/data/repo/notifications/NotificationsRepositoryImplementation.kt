package com.erfangholami.solidshare.data.repo.notifications

import com.erfangholami.androidsolidservices.api.notifications.NotificationsManager
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.solidshare.data.repo.sharing.toDomain
import com.erfangholami.solidshare.data.repo.sharing.toLib
import com.erfangholami.solidshare.domain.model.NotificationItem
import com.erfangholami.solidshare.domain.model.NotificationKind
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareNotification
import com.erfangholami.solidshare.domain.model.ShareNotificationType
import com.erfangholami.solidshare.domain.model.ShareRequest
import java.time.Instant
import javax.inject.Inject

class NotificationsRepositoryImplementation @Inject constructor(
    private val notificationsManager: NotificationsManager,
) : NotificationsRepository {

    override suspend fun listNotifications(webId: String): List<ShareNotification> =
        notificationsManager.listNotifications(webId).unwrap().map { it.toDomain() }

    override suspend fun listRequests(webId: String): List<ShareRequest> =
        notificationsManager.listRequests(webId).unwrap().map { it.toDomain() }

    override suspend fun listFeed(webId: String): List<NotificationItem> {
        val notifications = listNotifications(webId).map { it.toFeedItem() }
        val requests = listRequests(webId).map { it.toFeedItem() }
        return (notifications + requests).sortedWith(FEED_ORDER)
    }

    private fun ShareNotification.toFeedItem(): NotificationItem = NotificationItem(
        id = notificationUri,
        kind = when (type) {
            ShareNotificationType.OFFER -> NotificationKind.ACCESS_OFFER
            ShareNotificationType.ACCEPTED -> NotificationKind.REQUEST_ACCEPTED
            ShareNotificationType.UNDO -> NotificationKind.ACCESS_REVOKED
            ShareNotificationType.REJECT -> NotificationKind.REQUEST_REJECTED
        },
        counterpartWebId = ownerWebId,
        resourceUri = resourceUri,
        mode = mode,
        summary = summary,
        publishedAt = publishedAt,
        requestUri = null,
    )

    private fun ShareRequest.toFeedItem(): NotificationItem = NotificationItem(
        id = requestUri,
        kind = NotificationKind.ACCESS_REQUEST,
        counterpartWebId = requesterWebId,
        resourceUri = resourceUri,
        mode = requestedMode,
        summary = summary,
        publishedAt = publishedAt,
        requestUri = requestUri,
    )

    override suspend fun compactInbox(webId: String, olderThanIso: String?): Int =
        notificationsManager.compactInbox(webId, olderThanIso).unwrap()

    override suspend fun deleteNotification(webId: String, notificationUri: String): Boolean =
        notificationsManager.deleteNotification(webId, notificationUri).unwrap()

    override suspend fun ensureInbox(webId: String): String =
        notificationsManager.ensureInbox(webId).unwrap()

    override suspend fun sendOffer(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
        mode: ShareMode,
    ) {
        notificationsManager.sendOffer(ownerWebId, receiverWebId, resourceUri, mode.toLib())
            .unwrap()
    }

    override suspend fun sendUndo(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
    ) {
        notificationsManager.sendUndo(ownerWebId, receiverWebId, resourceUri).unwrap()
    }

    override suspend fun sendRequest(
        requesterWebId: String,
        ownerWebId: String,
        resourceUri: String,
        requestedMode: ShareMode,
        summary: String?,
    ) {
        notificationsManager
            .sendRequest(requesterWebId, ownerWebId, resourceUri, requestedMode.toLib(), summary)
            .unwrap()
    }

    override suspend fun sendReject(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        reason: String?,
    ) {
        notificationsManager.sendReject(ownerWebId, requesterWebId, resourceUri, reason).unwrap()
    }

    private fun <T> SolidNetworkResponse<T>.unwrap(): T = when (this) {
        is SolidNetworkResponse.Success -> data
        is SolidNetworkResponse.Error -> throw Exception("HTTP $errorCode: $errorMessage")
        is SolidNetworkResponse.Exception -> throw exception
    }

    private companion object {
        private fun parsedInstantOrNull(iso: String?): Instant? =
            iso?.let { runCatching { Instant.parse(it) }.getOrNull() }

        private val FEED_ORDER: Comparator<NotificationItem> =
            compareByDescending<NotificationItem> { parsedInstantOrNull(it.publishedAt) != null }
                .thenByDescending { parsedInstantOrNull(it.publishedAt) ?: Instant.MIN }
                .thenBy { it.id }
    }
}
