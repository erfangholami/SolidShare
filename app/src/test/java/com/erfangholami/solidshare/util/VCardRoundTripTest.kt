package com.erfangholami.solidshare.util

import com.erfangholami.solidshare.domain.model.ContactDetail
import com.erfangholami.solidshare.domain.model.ContactGender
import com.erfangholami.solidshare.domain.model.ContactIm
import com.erfangholami.solidshare.domain.model.ContactImType
import org.junit.Assert.assertEquals
import org.junit.Test

class VCardRoundTripTest {

    private val contact = ContactDetail(
        uri = "https://alice.pod/contacts/book1/Person/p1/index.ttl#this",
        fullName = "Jane Doe",
        impps = listOf(ContactIm("xmpp:jane@jabber.example", ContactImType.HOME)),
        categories = listOf("Friends", "Sci-fi, fantasy"),
        gender = ContactGender.FEMALE,
        geos = listOf("geo:52.3676,4.9041"),
        languages = listOf("nl", "en-GB"),
        uid = "urn:uuid:2f1c0000-0000-0000-0000-000000000001",
    )

    @Test
    fun `geo language impp categories gender and uid survive a write-parse round trip`() {
        val vcf = VCardWriter.write(listOf(contact to null))
        val parsed = VCardReader.parse(vcf).single().draft

        assertEquals(listOf("geo:52.3676,4.9041"), parsed.geos)
        assertEquals(listOf("nl", "en-GB"), parsed.languages)
        assertEquals(contact.impps, parsed.impps)
        assertEquals(listOf("Friends", "Sci-fi, fantasy"), parsed.categories)
        assertEquals(ContactGender.FEMALE, parsed.gender)
        assertEquals(contact.uid, parsed.uid)
    }

    @Test
    fun `v3 style semicolon geo parses to a geo uri`() {
        val vcf = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Jane
            GEO:52.3676;4.9041
            END:VCARD
        """.trimIndent()

        assertEquals(listOf("geo:52.3676,4.9041"), VCardReader.parse(vcf).single().draft.geos)
    }

    @Test
    fun `lang pref ordering wins over file order`() {
        val vcf = """
            BEGIN:VCARD
            VERSION:4.0
            FN:Jane
            LANG;PREF=2:en
            LANG;PREF=1:nl
            END:VCARD
        """.trimIndent()

        assertEquals(listOf("nl", "en"), VCardReader.parse(vcf).single().draft.languages)
    }
}
