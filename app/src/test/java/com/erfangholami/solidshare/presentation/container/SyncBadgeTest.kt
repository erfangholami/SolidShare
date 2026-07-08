package com.erfangholami.solidshare.presentation.container

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncBadgeTest {

    private val uri = "https://alice.example/files/x"

    @Test
    fun error_takesPrecedenceOverEverything() {
        assertEquals(
            ItemSyncBadge.ERROR,
            syncBadgeFor(uri, availableOffline = setOf(uri), pending = setOf(uri), errored = setOf(uri)),
        )
    }

    @Test
    fun pending_takesPrecedenceOverOffline() {
        assertEquals(
            ItemSyncBadge.PENDING,
            syncBadgeFor(uri, availableOffline = setOf(uri), pending = setOf(uri), errored = emptySet()),
        )
    }

    @Test
    fun offline_whenOnlyAvailableOffline() {
        assertEquals(
            ItemSyncBadge.OFFLINE,
            syncBadgeFor(uri, availableOffline = setOf(uri), pending = emptySet(), errored = emptySet()),
        )
    }

    @Test
    fun none_whenNotInAnySet() {
        assertEquals(
            ItemSyncBadge.NONE,
            syncBadgeFor(uri, availableOffline = emptySet(), pending = emptySet(), errored = emptySet()),
        )
    }
}
