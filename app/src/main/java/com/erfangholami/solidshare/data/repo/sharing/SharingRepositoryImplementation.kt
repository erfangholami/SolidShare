package com.erfangholami.solidshare.data.repo.sharing

import com.erfangholami.androidsolidservices.api.sharing.SharingManager
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.solidshare.domain.model.AccessGrant
import com.erfangholami.solidshare.domain.model.CatalogEntry
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.ParsedShareLink
import com.erfangholami.solidshare.domain.model.ReceivedShare
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareNotification
import com.erfangholami.solidshare.domain.model.ShareReceiver
import com.erfangholami.solidshare.domain.model.ShareRequest
import javax.inject.Inject

class SharingRepositoryImplementation @Inject constructor(
    private val sharingManager: SharingManager,
) : SharingRepository {

    override suspend fun getStoredGivenShares(webId: String): List<GivenShare> =
        sharingManager.getStoredGivenShares(webId).unwrap().map { it.toDomain() }

    override suspend fun refreshGivenShares(webId: String): List<GivenShare> =
        sharingManager.refreshGivenShares(webId).unwrap().map { it.toDomain() }

    override suspend fun rebuildGivenIndex(webId: String): List<GivenShare> =
        sharingManager.rebuildGivenIndex(webId).unwrap().map { it.toDomain() }

    override suspend fun getGivenSharesForResource(
        webId: String,
        resourceUri: String,
    ): List<GivenShare> {
        val aclShares = sharingManager.getGivenSharesForResource(webId, resourceUri)
            .unwrap().map { it.toDomain() }
        val createdByKey = runCatching { sharingManager.getStoredGivenShares(webId).unwrap() }
            .getOrDefault(emptyList())
            .map { it.toDomain() }
            .mapNotNull { stored ->
                stored.createdAt?.let { receiverResourceKey(stored.receiver, stored.resourceUri) to it }
            }
            .toMap()
        return aclShares.map { share ->
            createdByKey[receiverResourceKey(share.receiver, share.resourceUri)]
                ?.let { share.copy(createdAt = it) } ?: share
        }
    }

    private fun receiverResourceKey(receiver: ShareReceiver, resourceUri: String): String {
        val subject = when (receiver) {
            is ShareReceiver.WebIdReceiver -> receiver.webId
            is ShareReceiver.GroupReceiver -> receiver.groupUri
            ShareReceiver.Public -> "public"
        }
        return "$subject|$resourceUri"
    }

    override suspend fun createShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
        notifyReceiver: Boolean,
    ): GivenShare =
        sharingManager
            .createShare(webId, resourceUri, mode.toLib(), receiver.toLib(), notifyReceiver)
            .unwrap().toDomain()

    override suspend fun updateShare(
        webId: String,
        resourceUri: String,
        mode: ShareMode,
        receiver: ShareReceiver,
    ): GivenShare =
        sharingManager.updateShare(webId, resourceUri, mode.toLib(), receiver.toLib())
            .unwrap().toDomain()

    override suspend fun revokeShare(
        webId: String,
        resourceUri: String,
        receiver: ShareReceiver,
    ) {
        sharingManager.revokeShare(webId, resourceUri, receiver.toLib()).unwrap()
    }

    override suspend fun getStoredReceivedShares(webId: String): List<ReceivedShare> =
        sharingManager.getStoredReceivedShares(webId).unwrap().map { it.toDomain() }

    override suspend fun refreshReceivedShares(webId: String): List<ReceivedShare> =
        sharingManager.refreshReceivedShares(webId).unwrap().map { it.toDomain() }

    override suspend fun addReceivedShare(
        webId: String,
        resourceUri: String,
        ownerHint: String?,
    ): ReceivedShare? =
        sharingManager.addReceivedShare(webId, resourceUri, ownerHint).unwrap()?.toDomain()

    override suspend fun removeReceivedShare(
        webId: String,
        resourceUri: String,
        ownerWebId: String,
    ) {
        sharingManager.removeReceivedShare(webId, resourceUri, ownerWebId).unwrap()
    }

    override suspend fun syncReceivedSharesFromNotifications(
        webId: String,
        notifications: List<ShareNotification>,
    ): List<ReceivedShare> =
        sharingManager.syncReceivedShares(webId, notifications.map { it.toLib() })
            .unwrap().map { it.toDomain() }

    override suspend fun getAccessGrants(webId: String): List<AccessGrant> =
        sharingManager.getAccessGrants(webId).unwrap().map { it.toDomain() }

    override suspend fun acceptShareRequest(
        webId: String,
        request: ShareRequest,
    ): GivenShare = sharingManager.acceptShareRequest(webId, request.toLib()).unwrap().toDomain()

    override suspend fun rejectShareRequest(
        webId: String,
        request: ShareRequest,
        reason: String?,
    ) {
        sharingManager.rejectShareRequest(webId, request.toLib(), reason).unwrap()
    }

    override suspend fun publishCatalogEntry(webId: String, entry: CatalogEntry) {
        sharingManager.publishCatalogEntry(webId, entry.toLib()).unwrap()
    }

    override suspend fun removeCatalogEntry(webId: String, resourceUri: String) {
        sharingManager.removeCatalogEntry(webId, resourceUri).unwrap()
    }

    override suspend fun getOwnerCatalog(
        viewerWebId: String,
        ownerWebId: String,
    ): List<CatalogEntry> =
        sharingManager.getOwnerCatalog(viewerWebId, ownerWebId).unwrap().map { it.toDomain() }

    override fun deepLinkFor(resourceUri: String, ownerWebId: String?): String =
        sharingManager.getShareDeepLink(resourceUri, ownerWebId)

    override fun parseDeepLink(deepLink: String): ParsedShareLink? =
        sharingManager.parseShareDeepLink(deepLink)?.toDomain()

    override fun bareUrlFor(resourceUri: String): String =
        sharingManager.getShareBareUrl(resourceUri)

    private fun <T> SolidNetworkResponse<T>.unwrap(): T = when (this) {
        is SolidNetworkResponse.Success -> data
        is SolidNetworkResponse.Error -> throw SharingError.Unknown("HTTP $errorCode: $errorMessage")
        is SolidNetworkResponse.Exception -> throw exception.toSharingError()
    }
}
