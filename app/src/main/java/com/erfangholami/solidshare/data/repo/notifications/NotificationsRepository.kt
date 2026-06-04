package com.erfangholami.solidshare.data.repo.notifications

import com.erfangholami.solidshare.domain.model.NotificationItem
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareNotification
import com.erfangholami.solidshare.domain.model.ShareRequest

interface NotificationsRepository {

    suspend fun listNotifications(webId: String): List<ShareNotification>
    suspend fun listRequests(webId: String): List<ShareRequest>

    suspend fun listFeed(webId: String): List<NotificationItem>

    suspend fun compactInbox(webId: String, olderThanIso: String? = null): Int

    suspend fun deleteNotification(webId: String, notificationUri: String): Boolean

    suspend fun ensureInbox(webId: String): String

    suspend fun sendOffer(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
        mode: ShareMode,
    )

    suspend fun sendUndo(
        ownerWebId: String,
        receiverWebId: String,
        resourceUri: String,
    )

    suspend fun sendRequest(
        requesterWebId: String,
        ownerWebId: String,
        resourceUri: String,
        requestedMode: ShareMode,
        summary: String? = null,
    )

    suspend fun sendReject(
        ownerWebId: String,
        requesterWebId: String,
        resourceUri: String,
        reason: String? = null,
    )
}
