package com.erfangholami.solidshare.data.passimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TicketLinkFetcherTest {

    @Test
    fun `extracts the first https url from shared prose`() {
        assertEquals(
            "https://vendor.example/pass/abc",
            TicketLinkFetcher.firstHttpsUrl("Your ticket: https://vendor.example/pass/abc enjoy!"),
        )
    }

    @Test
    fun `strips trailing punctuation from a url in a sentence`() {
        assertEquals(
            "https://vendor.example/pass",
            TicketLinkFetcher.firstHttpsUrl("Download it (https://vendor.example/pass)."),
        )
    }

    @Test
    fun `ignores text without an https link`() {
        assertNull(TicketLinkFetcher.firstHttpsUrl("no links here"))
        assertNull(TicketLinkFetcher.firstHttpsUrl("http://insecure.example/pass"))
        assertNull(TicketLinkFetcher.firstHttpsUrl(""))
    }

    @Test
    fun `takes the file name from a content disposition header`() {
        assertEquals(
            "boarding.pkpass",
            TicketLinkFetcher.fileNameFrom(
                "attachment; filename=\"boarding.pkpass\"",
                "https://vendor.example/download",
            ),
        )
        assertEquals(
            "ticket.pdf",
            TicketLinkFetcher.fileNameFrom(
                "attachment; filename*=UTF-8''ticket.pdf",
                "https://vendor.example/download",
            ),
        )
    }

    @Test
    fun `falls back to the url path segment with an extension`() {
        assertEquals(
            "pass.pkpass",
            TicketLinkFetcher.fileNameFrom(null, "https://vendor.example/files/pass.pkpass?sig=x"),
        )
        assertNull(TicketLinkFetcher.fileNameFrom(null, "https://vendor.example/download"))
    }
}
