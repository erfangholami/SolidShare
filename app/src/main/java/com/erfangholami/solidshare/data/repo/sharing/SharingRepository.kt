package com.erfangholami.solidshare.data.repo.sharing

import com.erfangholami.solidshare.domain.model.AccessGrant
import com.erfangholami.solidshare.domain.model.CatalogEntry
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.ParsedShareLink
import com.erfangholami.solidshare.domain.model.ReceivedShare
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareNotification
import com.erfangholami.solidshare.domain.model.ShareReceiver
import com.erfangholami.solidshare.domain.model.ShareRequest


interface SharingRepository {

    suspend fun getStoredGivenShares(webId: String): List<GivenShare>
    suspend fun refreshGivenShares(webId: String): List<GivenShare>
    suspend fun rebuildGivenIndex(webId: String): List<GivenShare>

    suspend fun getGivenSharesForResource(
        webId: String,
        resourceUri: String,
    ): List<GivenShare>

    suspend fun createShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
        notifyReceiver: Boolean = true,
    ): GivenShare

    suspend fun updateShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
    ): GivenShare

    suspend fun revokeShare(
        webId: String,
        resourceUri: String,
        receiver: ShareReceiver,
    )

    suspend fun getStoredReceivedShares(webId: String): List<ReceivedShare>
    suspend fun refreshReceivedShares(webId: String): List<ReceivedShare>

    suspend fun addReceivedShare(
        webId: String,
        resourceUri: String,
        ownerHint: String? = null,
    ): ReceivedShare?

    suspend fun removeReceivedShare(
        webId: String,
        resourceUri: String,
        ownerWebId: String,
    )

    suspend fun syncReceivedSharesFromNotifications(
        webId: String,
        notifications: List<ShareNotification>,
    ): List<ReceivedShare>

    suspend fun getAccessGrants(webId: String): List<AccessGrant>

    suspend fun acceptShareRequest(
        webId: String,
        request: ShareRequest,
    ): GivenShare

    suspend fun rejectShareRequest(
        webId: String,
        request: ShareRequest,
        reason: String? = null,
    )

    suspend fun publishCatalogEntry(webId: String, entry: CatalogEntry)
    suspend fun removeCatalogEntry(webId: String, resourceUri: String)
    suspend fun getOwnerCatalog(
        viewerWebId: String,
        ownerWebId: String,
    ): List<CatalogEntry>

    fun deepLinkFor(resourceUri: String, ownerWebId: String? = null): String
    fun parseDeepLink(deepLink: String): ParsedShareLink?
    fun bareUrlFor(resourceUri: String): String
}
