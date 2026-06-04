package com.erfangholami.solidshare

import com.erfangholami.androidsolidservices.api.exceptions.SharingException
import com.erfangholami.solidshare.data.repo.sharing.SharingError
import com.erfangholami.solidshare.data.repo.sharing.toDomain
import com.erfangholami.solidshare.data.repo.sharing.toLib
import com.erfangholami.solidshare.data.repo.sharing.toSharingError
import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
    fun toSharingError_mapsAccessDeniedWithFields() {
        val error = SharingException
            .AccessDenied("https://pod.example/secret", "https://owner.example/card#me")
            .toSharingError()

        assertTrue(error is SharingError.AccessDenied)
        error as SharingError.AccessDenied
        assertEquals("https://pod.example/secret", error.resourceUri)
        assertEquals("https://owner.example/card#me", error.ownerWebId)
    }

    @Test
    fun toSharingError_passesThroughExistingDomainError() {
        val original = SharingError.StaleAcl()
        assertSame(original, original.toSharingError())
    }

    @Test
    fun toSharingError_wrapsUnknownThrowable() {
        val error = IllegalStateException("boom").toSharingError()
        assertTrue(error is SharingError.Unknown)
        assertEquals("boom", error.message)
    }
}
