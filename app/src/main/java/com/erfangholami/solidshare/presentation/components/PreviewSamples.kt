package com.erfangholami.solidshare.presentation.components

import com.erfangholami.solidshare.domain.model.AddressBook
import com.erfangholami.solidshare.domain.model.ContactAddress
import com.erfangholami.solidshare.domain.model.ContactAddressType
import com.erfangholami.solidshare.domain.model.ContactDetail
import com.erfangholami.solidshare.domain.model.ContactEmail
import com.erfangholami.solidshare.domain.model.ContactEmailType
import com.erfangholami.solidshare.domain.model.ContactGroup
import com.erfangholami.solidshare.domain.model.ContactLinkType
import com.erfangholami.solidshare.domain.model.ContactListEntry
import com.erfangholami.solidshare.domain.model.ContactPhone
import com.erfangholami.solidshare.domain.model.ContactPhoneType
import com.erfangholami.solidshare.domain.model.ContactWebLink
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.GivenShare
import com.erfangholami.solidshare.domain.model.NotificationItem
import com.erfangholami.solidshare.domain.model.NotificationKind
import com.erfangholami.solidshare.domain.model.PublicProfile
import com.erfangholami.solidshare.domain.model.ReceivedShare
import com.erfangholami.solidshare.domain.model.ResourceType
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareReceiver
import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketEventInfo
import com.erfangholami.solidshare.domain.model.TicketSeatInfo
import com.erfangholami.solidshare.domain.model.TicketSource
import com.erfangholami.solidshare.domain.model.TicketSummaryItem
import com.erfangholami.solidshare.domain.model.TicketVenue

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

    const val ADDRESS_BOOK = "https://alice.solidcommunity.net/contacts/book1/index.ttl#this"
    const val CONTACT =
        "https://alice.solidcommunity.net/contacts/book1/Person/p1/index.ttl#this"
    const val TICKET = "https://alice.solidcommunity.net/tickets/t1.ttl#this"

    fun addressBook(
        uri: String = ADDRESS_BOOK,
        title: String = "Contacts",
        isPrivate: Boolean = true,
        contactCount: Int = 4,
    ): AddressBook = AddressBook(uri, title, isPrivate, contactCount)

    fun addressBooks(): List<AddressBook> = listOf(
        addressBook(),
        addressBook(
            uri = "https://alice.solidcommunity.net/contacts/book2/index.ttl#this",
            title = "Work",
            isPrivate = false,
            contactCount = 2,
        ),
    )

    fun contactEntry(
        name: String = "Ben Miller",
        uri: String = CONTACT,
        bookUri: String = ADDRESS_BOOK,
    ): ContactListEntry = ContactListEntry(uri, bookUri, name)

    fun contactEntries(): List<ContactListEntry> = listOf(
        "Alice Cooper", "Ben Miller", "Bram Stoker", "Carol Jones", "Dana White",
    ).mapIndexed { index, name ->
        contactEntry(
            name = name,
            uri = "https://alice.solidcommunity.net/contacts/book1/Person/p$index/index.ttl#this",
        )
    }

    fun contactDetail(
        uri: String = CONTACT,
        fullName: String = "Ben Miller",
    ): ContactDetail = ContactDetail(
        uri = uri,
        fullName = fullName,
        givenName = "Ben",
        familyName = "Miller",
        nickname = "Benny",
        phones = listOf(ContactPhone("+31 6 1234 5678", ContactPhoneType.CELL)),
        emails = listOf(ContactEmail("ben@example.org", ContactEmailType.HOME)),
        addresses = listOf(
            ContactAddress(
                street = "Kerkstraat 1",
                locality = "Amsterdam",
                postalCode = "1017GA",
                countryName = "Netherlands",
                type = ContactAddressType.HOME,
            ),
        ),
        birthday = "1990-04-01",
        organization = "Acme Co.",
        organizationUnit = "Design",
        jobTitle = "Designer",
        note = "Met at FOSDEM",
        geos = listOf("geo:52.3676,4.9041"),
        languages = listOf("nl", "en-GB"),
        links = listOf(ContactWebLink(ContactLinkType.WEB_ID, webIdOf("ben"))),
    )

    fun contactGroup(
        uri: String = "https://alice.solidcommunity.net/contacts/book1/Group/friends.ttl",
        name: String = "Friends",
        memberUris: List<String> = listOf(CONTACT),
    ): ContactGroup = ContactGroup(uri, name, memberUris)

    fun ticketSummary(
        uri: String = TICKET,
        title: String = "Coldplay — Music of the Spheres",
        category: TicketCategory = TicketCategory.EVENT,
        eventStart: String? = "2026-07-14T19:30:00Z",
        issuer: String? = "Ticketmaster",
    ): TicketSummaryItem = TicketSummaryItem(
        uri = uri,
        title = title,
        category = category,
        eventStart = eventStart,
        issuer = issuer,
        validThrough = "2026-07-14T23:59:00Z",
    )

    fun ticket(
        uri: String = TICKET,
        title: String = "Coldplay — Music of the Spheres",
    ): Ticket = Ticket(
        uri = uri,
        title = title,
        description = "Gate opens 18:00",
        number = "TKT-0042",
        token = "c3RhZGl1bS10aWNrZXQ=",
        barcodeFormat = TicketBarcodeFormat.QR_CODE,
        category = TicketCategory.EVENT,
        issuer = "Ticketmaster",
        holder = "Alice Cooper",
        seat = TicketSeatInfo(number = "27", row = "F", section = "B12"),
        price = "89.50",
        currency = "EUR",
        event = TicketEventInfo(
            name = "Music of the Spheres Tour",
            start = "2026-07-14T19:30:00Z",
            end = "2026-07-14T23:00:00Z",
            venue = TicketVenue("Johan Cruijff ArenA", "Arena Boulevard 1, Amsterdam"),
        ),
        validFrom = "2026-07-14T17:00:00Z",
        validThrough = "2026-07-14T23:59:00Z",
        source = TicketSource.SCAN,
        createdAt = "2026-07-02T09:15:00Z",
    )
}
