package com.erfangholami.solidshare.data.repo.contacts

import com.erfangholami.solidshare.data.local.cache.testOutbox
import androidx.work.WorkManager
import com.erfangholami.androidsolidservices.api.datamodule.contacts.AddressBookStore
import com.erfangholami.androidsolidservices.api.datamodule.contacts.ContactStore
import com.erfangholami.androidsolidservices.api.datamodule.contacts.SolidContactsDataModule
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressBook
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactPhoto
import com.erfangholami.androidsolidservices.shared.model.contacts.SolidContact
import com.erfangholami.androidsolidservices.shared.model.contacts.contactData
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.solidshare.data.local.ContactsMergePrefs
import com.erfangholami.solidshare.data.local.cache.SolidCacheDatabase
import com.erfangholami.solidshare.data.local.cache.inMemoryCacheDb
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.model.ContactAddress
import com.erfangholami.solidshare.domain.model.ContactDetail
import com.erfangholami.solidshare.domain.model.ContactEmail
import com.erfangholami.solidshare.domain.model.ContactGender
import com.erfangholami.solidshare.domain.model.ContactIm
import com.erfangholami.solidshare.domain.model.ContactLinkType
import com.erfangholami.solidshare.domain.model.ContactPhone
import com.erfangholami.solidshare.domain.model.ContactWebLink
import com.erfangholami.solidshare.util.StringProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedContactRepositoryTest {

    private val webId = "https://bob.pod/profile/card#me"
    private val bookUri = "https://bob.pod/contacts/b1/index.ttl#this"
    private val sharedContainer = "https://alice.pod/contacts/b9/Person/7a2d/"
    private val sharedUri = "${sharedContainer}index.ttl#this"
    private val sharedPhoto = "${sharedContainer}photo.jpg"
    private val createdUri = "https://bob.pod/contacts/b1/Person/n1/index.ttl#this"

    private lateinit var db: SolidCacheDatabase
    private lateinit var repo: ContactsRepositoryImplementation

    private val contacts = mockk<ContactStore>(relaxed = true) {
        every { shareTarget(any()) } answers {
            firstArg<String>().substringBefore('#').removeSuffix("index.ttl")
        }
    }
    private val books = mockk<AddressBookStore>()
    private val sharing = mockk<SharingRepository>(relaxed = true)
    private val module = mockk<SolidContactsDataModule> {
        every { this@mockk.contacts } returns this@SharedContactRepositoryTest.contacts
        every { this@mockk.books } returns this@SharedContactRepositoryTest.books
    }
    private val auth = mockk<AuthRepository> {
        coEvery { getStorages(webId) } returns listOf("https://bob.pod/")
    }
    private val stringProvider = mockk<StringProvider> {
        every { getString(any()) } returns "Contacts"
    }

    @Before
    fun setUp() {
        db = inMemoryCacheDb()
        repo = ContactsRepositoryImplementation(
            module,
            auth,
            sharing,
            ContactMergeEngine(),
            mockk<ContactsMergePrefs>(relaxed = true),
            db.cachedEntityDao(),
            testOutbox(db),
            stringProvider,
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `getSharedContact resolves a container target through findInContainer`() = runTest {
        coEvery { contacts.findInContainer(webId, sharedContainer) } returns SolidResult.Success(
            SolidContact(uri = sharedUri, data = contactData { fullName = "Jane" }),
        )

        val contact = repo.getSharedContact(webId, sharedContainer)

        assertEquals(sharedUri, contact.uri)
        assertEquals("Jane", contact.fullName)
    }

    @Test
    fun `addSharedContactToBook creates the copy in the default book and carries the photo`() =
        runTest {
            coEvery { books.ensureDefault(webId, any(), any()) } returns SolidResult.Success(
                AddressBook(
                    uri = bookUri,
                    title = "Contacts",
                    contacts = emptyList(),
                    groups = emptyList(),
                ),
            )
            coEvery { contacts.create(webId, bookUri, any(), any()) } returns SolidResult.Success(
                SolidContact(uri = createdUri, data = contactData { fullName = "Jane" }),
            )
            coEvery { contacts.getPhoto(webId, sharedPhoto) } returns SolidResult.Success(
                ContactPhoto(sharedPhoto, "image/jpeg", byteArrayOf(4, 2)),
            )
            coEvery { contacts.setPhoto(webId, createdUri, any(), "image/jpeg") } returns
                SolidResult.Success(
                    SolidContact(uri = createdUri, data = contactData { fullName = "Jane" }),
                )

            val shared = ContactDetail(
                uri = sharedUri,
                fullName = "Jane",
                photoUri = sharedPhoto,
            )

            val created = repo.addSharedContactToBook(webId, shared)

            assertEquals(createdUri, created.uri)
            coVerify { contacts.create(webId, bookUri, any(), any()) }
            coVerify { contacts.setPhoto(webId, createdUri, any(), "image/jpeg") }
        }

    @Test
    fun `deleting a contact purges the share rows of its Person container`() = runTest {
        val ownUri = "https://bob.pod/contacts/b1/Person/n1/index.ttl#this"
        coEvery { contacts.delete(webId, bookUri, ownUri) } returns SolidResult.Success(
            SolidContact(uri = ownUri, data = contactData { fullName = "Jane" }),
        )

        repo.deleteContact(webId, bookUri, ownUri)

        coVerify {
            sharing.purgeGivenShares(
                webId,
                "https://bob.pod/contacts/b1/Person/n1/",
                any(),
                any(),
            )
        }
    }

    @Test
    fun `toDraft keeps every field so a copy never loses data`() {
        val detail = ContactDetail(
            uri = sharedUri,
            fullName = "Jane Doe",
            givenName = "Jane",
            familyName = "Doe",
            middleName = "Q",
            namePrefix = "Dr",
            nameSuffix = "PhD",
            nickname = "JD",
            phones = listOf(ContactPhone("+31612345678")),
            emails = listOf(ContactEmail("jane@example.org")),
            impps = listOf(ContactIm("xmpp:jane@example.org")),
            addresses = listOf(ContactAddress(street = "Main 1")),
            birthday = "1990-01-01",
            anniversary = "2015-06-01",
            organization = "ACME",
            organizationUnit = "R&D",
            role = "Lead",
            jobTitle = "Engineer",
            note = "Met at FOSDEM",
            categories = listOf("friends"),
            gender = ContactGender.FEMALE,
            geos = listOf("geo:52.37,4.89"),
            languages = listOf("nl"),
            links = listOf(
                ContactWebLink(ContactLinkType.WEB_ID, "https://jane.pod/profile/card#me"),
            ),
            uid = "urn:uuid:1234",
            photoUri = sharedPhoto,
            modified = 42L,
        )

        val draft = detail.toDraft()

        assertEquals(detail.fullName, draft.fullName)
        assertEquals(detail.givenName, draft.givenName)
        assertEquals(detail.familyName, draft.familyName)
        assertEquals(detail.middleName, draft.middleName)
        assertEquals(detail.namePrefix, draft.namePrefix)
        assertEquals(detail.nameSuffix, draft.nameSuffix)
        assertEquals(detail.nickname, draft.nickname)
        assertEquals(detail.phones, draft.phones)
        assertEquals(detail.emails, draft.emails)
        assertEquals(detail.impps, draft.impps)
        assertEquals(detail.addresses, draft.addresses)
        assertEquals(detail.birthday, draft.birthday)
        assertEquals(detail.anniversary, draft.anniversary)
        assertEquals(detail.organization, draft.organization)
        assertEquals(detail.organizationUnit, draft.organizationUnit)
        assertEquals(detail.role, draft.role)
        assertEquals(detail.jobTitle, draft.jobTitle)
        assertEquals(detail.note, draft.note)
        assertEquals(detail.categories, draft.categories)
        assertEquals(detail.gender, draft.gender)
        assertEquals(detail.geos, draft.geos)
        assertEquals(detail.languages, draft.languages)
        assertEquals(detail.links, draft.links)
        assertEquals(detail.uid, draft.uid)
    }
}
