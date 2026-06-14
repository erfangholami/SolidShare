package com.erfangholami.solidshare.presentation.components

import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.NotificationItem
import com.erfangholami.solidshare.domain.model.NotificationKind
import com.erfangholami.solidshare.domain.model.PublicProfile
import com.erfangholami.solidshare.domain.model.ReceivedShare
import com.erfangholami.solidshare.domain.model.ResourceType
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareReceiver

internal object PreviewSamples {

    const val WEB_ID = "https://alice.solidcommunity.net/profile/card#me"
    const val OWNER_WEB_ID = "https://owner.solidcommunity.net/profile/card#me"
    const val RESOURCE = "https://alice.solidcommunity.net/photos/trip.jpg"
    const val FOLDER = "https://alice.solidcommunity.net/documents/"

    fun webIdOf(name: String): String = "https://$name.solidcommunity.net/profile/card#me"

    fun profile(
        webId: String = WEB_ID,
        name: String? = "Alice Cooper",
        givenName: String? = "Alice",
        familyName: String? = "Cooper",
        photoUri: String? = null,
        emails: List<String> = listOf("alice@example.org"),
        phones: List<String> = listOf("+1 555 0100"),
        organization: String? = "Acme Co.",
        role: String? = "Research Engineer",
        oidcIssuer: String? = "https://solidcommunity.net",
    ): PublicProfile = PublicProfile(
        webId = webId,
        profileDocumentUrl = webId.substringBefore("#"),
        name = name,
        givenName = givenName,
        familyName = familyName,
        photoUri = photoUri,
        emails = emails,
        phones = phones,
        organization = organization,
        role = role,
        oidcIssuer = oidcIssuer,
    )

    fun profiles(vararg names: String): List<PublicProfile> =
        names.map { profile(webId = webIdOf(it), name = it.replaceFirstChar { c -> c.uppercase() }) }

    fun givenShare(
        name: String = "ben",
        mode: ShareMode = ShareMode.READ,
        resourceUri: String = RESOURCE,
    ): GivenShare = GivenShare(
        receiver = ShareReceiver.WebIdReceiver(webIdOf(name)),
        mode = mode,
        resourceUri = resourceUri,
        createdAt = "2026-05-01T10:00:00Z",
    )

    fun publicShare(mode: ShareMode = ShareMode.READ, resourceUri: String = RESOURCE): GivenShare =
        GivenShare(ShareReceiver.Public, mode, resourceUri, createdAt = "2026-05-01T10:00:00Z")

    fun receivedShare(
        name: String = "owner",
        mode: ShareMode = ShareMode.READ,
        resourceUri: String = RESOURCE,
    ): ReceivedShare = ReceivedShare(
        ownerWebId = webIdOf(name),
        mode = mode,
        resourceUri = resourceUri,
        addedAt = "2026-05-02T09:30:00Z",
    )

    fun file(
        name: String = "trip.jpg",
        identifier: String = RESOURCE,
        isContainer: Boolean = false,
        resourceType: ResourceType = ResourceType.IMAGE,
        extension: String? = "jpg",
        mimeType: String? = "image/jpeg",
        sizeBytes: Long? = 2_400_000L,
        lastModified: Long? = 1_716_000_000_000L,
        itemCount: Int? = null,
    ): ContainerItem = ContainerItem(
        identifier = identifier,
        isContainer = isContainer,
        name = name,
        extension = extension,
        mimeType = mimeType,
        resourceType = resourceType,
        resourceTypes = emptyList(),
        sizeBytes = sizeBytes,
        lastModified = lastModified,
        etag = "\"abc123\"",
        createdTime = 1_715_000_000_000L,
        itemCount = itemCount,
    )

    fun folder(
        name: String = "Documents",
        identifier: String = FOLDER,
        itemCount: Int? = 12,
    ): ContainerItem = file(
        name = name,
        identifier = identifier,
        isContainer = true,
        resourceType = ResourceType.FOLDER,
        extension = null,
        mimeType = null,
        sizeBytes = null,
        itemCount = itemCount,
    )

    fun notification(
        id: String = "urn:notif:1",
        kind: NotificationKind = NotificationKind.ACCESS_OFFER,
        counterpartWebId: String = OWNER_WEB_ID,
        resourceUri: String = RESOURCE,
        mode: ShareMode? = ShareMode.READ,
        summary: String? = "Shared a photo with you",
        publishedAt: String? = "2026-05-02T09:30:00Z",
        requestUri: String? = null,
    ): NotificationItem = NotificationItem(
        id = id,
        kind = kind,
        counterpartWebId = counterpartWebId,
        resourceUri = resourceUri,
        mode = mode,
        summary = summary,
        publishedAt = publishedAt,
        requestUri = requestUri,
    )
}
