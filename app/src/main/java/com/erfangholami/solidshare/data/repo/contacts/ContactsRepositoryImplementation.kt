package com.erfangholami.solidshare.data.repo.contacts

import android.os.Parcelable
import com.erfangholami.androidsolidservices.api.datamodule.contacts.SolidContactsDataModule
import com.erfangholami.androidsolidservices.shared.result.DataModuleResult
import com.erfangholami.androidsolidservices.shared.result.getOrThrow
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.domain.model.AddressBook
import com.erfangholami.solidshare.domain.model.ContactDetail
import com.erfangholami.solidshare.domain.model.ContactDraft
import com.erfangholami.solidshare.domain.model.ContactGroup
import com.erfangholami.solidshare.domain.model.ContactListEntry
import com.erfangholami.solidshare.domain.model.ContactMatchResult
import com.erfangholami.solidshare.domain.model.ContactsOverview
import com.erfangholami.solidshare.util.StringProvider
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class ContactsRepositoryImplementation @Inject constructor(
    private val contactsDataModule: SolidContactsDataModule,
    private val authRepository: AuthRepository,
    private val stringProvider: StringProvider,
) : ContactsRepository {

    override suspend fun getOverview(webId: String): ContactsOverview {
        val bookList = contactsDataModule.books.list(webId).unwrap()
        val books = bookList.privateAddressBookUris.map { it to true } +
                bookList.publicAddressBookUris.map { it to false }
        val hydrated = coroutineScope {
            books.map { (uri, isPrivate) ->
                async {
                    val book = contactsDataModule.books.get(webId, uri).unwrap()
                    Triple(book, isPrivate, uri)
                }
            }.awaitAll()
        }
        val domainBooks = hydrated.map { (book, isPrivate, uri) ->
            AddressBook(
                uri = uri,
                title = book.title,
                isPrivate = isPrivate,
                contactCount = book.contacts.size,
            )
        }
        val entries = hydrated.flatMap { (book, _, uri) ->
            book.contacts.map { ContactListEntry(it.uri, uri, it.name) }
        }
        return ContactsOverview(
            books = domainBooks.sortedBy { it.title.lowercase() },
            entries = entries.sortedBy { it.name.lowercase() },
        )
    }

    override suspend fun ensureDefaultAddressBook(webId: String): String {
        val storage = authRepository.getStorages(webId).firstOrNull()
            ?: throw IllegalStateException("No storage found for $webId")
        return contactsDataModule.books
            .ensureDefault(
                ownerWebId = webId,
                storage = storage,
                title = stringProvider.getString(R.string.contacts_default_book_title),
            )
            .unwrap()
            .uri
    }

    override suspend fun getContact(webId: String, contactUri: String): ContactDetail =
        contactsDataModule.contacts.get(webId, contactUri).unwrap().toDomain()

    override suspend fun getAllContacts(webId: String, bookUri: String): List<ContactDetail> =
        contactsDataModule.contacts.getAll(webId, bookUri).unwrap()
            .contacts.map { it.toDomain() }

    override suspend fun createContact(
        webId: String,
        bookUri: String,
        draft: ContactDraft,
        groupUris: List<String>,
    ): ContactDetail =
        contactsDataModule.contacts
            .create(webId, bookUri, draft.toLib(), groupUris)
            .unwrap()
            .toDomain()

    override suspend fun updateContact(
        webId: String,
        bookUri: String,
        contactUri: String,
        draft: ContactDraft,
    ): ContactDetail =
        contactsDataModule.contacts
            .update(webId, bookUri, contactUri, draft.toLib())
            .unwrap()
            .toDomain()

    override suspend fun deleteContact(webId: String, bookUri: String, contactUri: String) {
        contactsDataModule.contacts.delete(webId, bookUri, contactUri).unwrap()
    }

    override suspend fun setContactPhoto(
        webId: String,
        contactUri: String,
        photo: ByteArray,
        contentType: String,
    ): ContactDetail =
        contactsDataModule.contacts
            .setPhoto(webId, contactUri, photo, contentType)
            .unwrap()
            .toDomain()

    override suspend fun removeContactPhoto(webId: String, contactUri: String): ContactDetail =
        contactsDataModule.contacts.removePhoto(webId, contactUri).unwrap().toDomain()

    override suspend fun getContactPhoto(webId: String, photoUri: String): ByteArray =
        contactsDataModule.contacts.getPhoto(webId, photoUri).unwrap().bytes

    override suspend fun findContactByWebId(
        webId: String,
        targetWebId: String,
    ): ContactMatchResult {
        val match = contactsDataModule.contacts.findByWebId(webId, targetWebId).unwrap()
        return ContactMatchResult(
            contact = match.contact?.toDomain(),
            bookUri = match.addressBookUri,
        )
    }

    override suspend fun getGroups(webId: String, bookUri: String): List<ContactGroup> {
        val book = contactsDataModule.books.get(webId, bookUri).unwrap()
        return coroutineScope {
            book.groups.map { group ->
                async {
                    val full = contactsDataModule.groups.get(webId, group.uri).unwrap()
                    ContactGroup(
                        uri = full.uri,
                        name = full.name,
                        memberUris = full.contacts.map { it.uri },
                    )
                }
            }.awaitAll()
        }.sortedBy { it.name.lowercase() }
    }

    override suspend fun addContactToGroup(webId: String, contactUri: String, groupUri: String) {
        contactsDataModule.groups.addMember(webId, groupUri, contactUri).unwrap()
    }

    override suspend fun removeContactFromGroup(
        webId: String,
        contactUri: String,
        groupUri: String,
    ) {
        contactsDataModule.groups.removeMember(webId, groupUri, contactUri).unwrap()
    }

    private fun <T : Parcelable> DataModuleResult<T>.unwrap(): T = getOrThrow()
}
