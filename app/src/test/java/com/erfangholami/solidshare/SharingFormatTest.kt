package com.erfangholami.solidshare

import com.erfangholami.solidshare.domain.model.ShareMode
import com.erfangholami.solidshare.domain.model.ShareReceiver
import com.erfangholami.solidshare.presentation.sharing.describeReceiver
import com.erfangholami.solidshare.presentation.sharing.displayNameForUri
import com.erfangholami.solidshare.presentation.sharing.isContainerUri
import com.erfangholami.solidshare.presentation.sharing.labelFor
import com.erfangholami.solidshare.presentation.sharing.shortenWebId
import com.erfangholami.solidshare.presentation.sharing.subjectKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharingFormatTest {

    @Test
    fun displayNameForUri_returnsFileName() {
        assertEquals("file.txt", displayNameForUri("https://pod.example/a/b/file.txt"))
    }

    @Test
    fun displayNameForUri_decodesPercentEncoding() {
        assertEquals("my file.txt", displayNameForUri("https://pod.example/a/my%20file.txt"))
    }

    @Test
    fun displayNameForUri_stripsContainerTrailingSlash() {
        assertEquals("folder", displayNameForUri("https://pod.example/a/folder/"))
    }

    @Test
    fun isContainerUri_detectsTrailingSlash() {
        assertTrue(isContainerUri("https://pod.example/a/"))
        assertFalse(isContainerUri("https://pod.example/a/file.txt"))
    }

    @Test
    fun shortenWebId_dropsSchemeAndFragment() {
        assertEquals(
            "alice.example/profile/card",
            shortenWebId("https://alice.example/profile/card#me"),
        )
    }

    @Test
    fun subjectKey_distinguishesReceivers() {
        assertEquals(
            "https://alice.example/card#me",
            ShareReceiver.WebIdReceiver("https://alice.example/card#me").subjectKey(),
        )
        assertEquals(
            "https://pod.example/team",
            ShareReceiver.GroupReceiver("https://pod.example/team").subjectKey(),
        )
        assertEquals("public", ShareReceiver.Public.subjectKey())
    }

    @Test
    fun labelFor_mapsShareModes() {
        assertEquals("Read", labelFor(ShareMode.READ))
        assertEquals("Append", labelFor(ShareMode.APPEND))
        assertEquals("Read & Write", labelFor(ShareMode.WRITE))
    }

    @Test
    fun describeReceiver_describesPublic() {
        assertEquals("Anyone with the link", describeReceiver(ShareReceiver.Public))
    }
}
