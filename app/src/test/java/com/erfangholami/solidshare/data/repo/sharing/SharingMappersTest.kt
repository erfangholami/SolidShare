package com.erfangholami.solidshare.data.repo.sharing

import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare as LibGivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ParsedShareLink as LibParsedShareLink
import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare as LibReceivedShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode as LibShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver as LibShareReceiver
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharingMappersTest {

    private val ticketType = "https://schema.org/Ticket"

    @Test
    fun `a typed given share keeps its entity type and name across the mapping`() {
        val domain = LibGivenShare(
            receiver = LibShareReceiver.WebIdReceiver("https://bob.pod/profile/card#me"),
            mode = LibShareMode.READ,
            resourceUri = "https://alice.pod/tickets/u1/",
            createdAt = "2026-08-01T10:00:00Z",
            resourceType = ticketType,
            resourceName = "Coldplay",
        ).toDomain()

        assertEquals(ticketType, domain.resourceType)
        assertEquals("Coldplay", domain.resourceName)
    }

    @Test
    fun `a typed received share keeps its entity type and name across the mapping`() {
        val domain = LibReceivedShare(
            ownerWebId = "https://alice.pod/profile/card#me",
            mode = LibShareMode.READ,
            resourceUri = "https://alice.pod/tickets/u1/",
            addedAt = "2026-08-01T10:00:00Z",
            resourceType = ticketType,
            resourceName = "Coldplay",
        ).toDomain()

        assertEquals(ticketType, domain.resourceType)
        assertEquals("Coldplay", domain.resourceName)
    }

    @Test
    fun `a parsed share link keeps its type hint across the mapping`() {
        val domain = LibParsedShareLink(
            resourceUri = "https://alice.pod/tickets/u1/",
            ownerWebId = "https://alice.pod/profile/card#me",
            resourceType = ticketType,
        ).toDomain()

        assertEquals(ticketType, domain.resourceType)
    }
}
