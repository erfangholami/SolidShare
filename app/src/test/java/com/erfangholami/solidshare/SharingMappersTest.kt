package com.erfangholami.solidshare

import com.erfangholami.solidshare.data.repo.sharing.toDomain
import com.erfangholami.solidshare.data.repo.sharing.toLib
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareReceiver
import org.junit.Assert.assertEquals
import org.junit.Test
import com.erfangholami.androidsolidservices.shared.model.sharing.GivenShare as LibGivenShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ReceivedShare as LibReceivedShare
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareMode as LibShareMode
import com.erfangholami.androidsolidservices.shared.model.sharing.ShareReceiver as LibShareReceiver

class SharingMappersTest {

    @Test
    fun shareMode_roundTrips() {
        for (mode in ShareMode.entries) {
            assertEquals(mode, mode.toLib().toDomain())
        }
    }

    @Test
    fun shareMode_mapsToMatchingLibraryValue() {
        assertEquals(LibShareMode.READ, ShareMode.READ.toLib())
        assertEquals(LibShareMode.APPEND, ShareMode.APPEND.toLib())
        assertEquals(LibShareMode.WRITE, ShareMode.WRITE.toLib())
    }

    @Test
    fun shareReceiver_roundTrips() {
        val receivers = listOf(
            ShareReceiver.WebIdReceiver("https://alice.example/profile/card#me"),
            ShareReceiver.GroupReceiver("https://pod.example/groups/team"),
            ShareReceiver.Public,
        )
        for (receiver in receivers) {
            assertEquals(receiver, receiver.toLib().toDomain())
        }
    }

    @Test
    fun givenShare_mapsAllFields() {
        val domain = LibGivenShare(
            LibShareReceiver.WebIdReceiver("https://bob.example/card#me"),
            LibShareMode.WRITE,
            "https://pod.example/doc",
        ).toDomain()

        assertEquals("https://pod.example/doc", domain.resourceUri)
        assertEquals(ShareMode.WRITE, domain.mode)
        assertEquals(ShareReceiver.WebIdReceiver("https://bob.example/card#me"), domain.receiver)
    }

    @Test
    fun receivedShare_mapsAllFields() {
        val domain = LibReceivedShare(
            "https://owner.example/card#me",
            LibShareMode.READ,
            "https://pod.example/x",
        ).toDomain()

        assertEquals("https://owner.example/card#me", domain.ownerWebId)
        assertEquals(ShareMode.READ, domain.mode)
        assertEquals("https://pod.example/x", domain.resourceUri)
    }

    @Test
    fun givenShare_mapsCreatedAt() {
        val domain = LibGivenShare(
            LibShareReceiver.WebIdReceiver("https://bob.example/card#me"),
            LibShareMode.READ,
            "https://pod.example/doc",
            createdAt = "2026-06-04T12:00:00Z",
        ).toDomain()

        assertEquals("2026-06-04T12:00:00Z", domain.createdAt)
    }

    @Test
    fun receivedShare_mapsAddedAt() {
        val domain = LibReceivedShare(
            "https://owner.example/card#me",
            LibShareMode.READ,
            "https://pod.example/x",
            addedAt = "2026-06-04T08:30:00Z",
        ).toDomain()

        assertEquals("2026-06-04T08:30:00Z", domain.addedAt)
    }

}
