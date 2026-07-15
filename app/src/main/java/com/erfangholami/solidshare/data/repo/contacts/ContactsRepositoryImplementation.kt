package com.erfangholami.solidshare.data.repo.contacts

import android.os.Parcelable
import androidx.work.WorkManager
import com.erfangholami.androidsolidservices.api.datamodule.contacts.SolidContactsDataModule
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.local.ContactsMergePrefs
import com.erfangholami.solidshare.data.local.cache.CachedContactEntity
import com.erfangholami.solidshare.data.local.cache.ContactDao
import com.erfangholami.solidshare.data.local.cache.ContactOpType
import com.erfangholami.solidshare.data.local.cache.ContactOutboxDao
import com.erfangholami.solidshare.data.local.cache.ContactOutboxOpEntity
import com.erfangholami.solidshare.data.local.cache.OpStatus
import com.erfangholami.solidshare.data.local.cache.SyncState
import com.erfangholami.solidshare.data.local.cache.toCacheEntity
import com.erfangholami.solidshare.data.local.cache.toDomain
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.domain.model.AddressBook
import com.erfangholami.solidshare.domain.model.ContactDetail
import com.erfangholami.solidshare.domain.model.ContactDraft
import com.erfangholami.solidshare.domain.model.ContactGroup
import com.erfangholami.solidshare.domain.model.ContactListEntry
import com.erfangholami.solidshare.domain.model.ContactMatchResult
import com.erfangholami.solidshare.domain.model.ContactRef
import com.erfangholami.solidshare.domain.model.ContactsOverview
import com.erfangholami.solidshare.domain.model.MergeMember
import com.erfangholami.solidshare.domain.model.MergeSuggestion
import com.erfangholami.solidshare.util.StringProvider
import com.erfangholami.solidshare.worker.ContactOutboxWorker
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class ContactsRepositoryImplementation @Inject constructor(
    private val contactsDataModule: SolidContactsDataModule,
    private val authRepository: AuthRepository,
    private val mergeEngine: ContactMergeEngine,
    private val mergePrefs: ContactsMergePrefs,
    private val contactDao: ContactDao,
    private val contactOutboxDao: ContactOutboxDao,
    private val workManager: WorkManager,
    private val stringProvider: StringProvider,
) : ContactsRepository {

    private val payloadJson = Json { ignoreUnknownKeys = true }

    override fun observeContacts(webId: String): Flow<List<ContactListEntry>> =
        contactDao.observeContacts(webId).map { rows ->
            rows.map { ContactListEntry(it.contactUri, it.bookUri, it.name) }
        }

    override suspend fun refreshContacts(webId: String): ContactsOverview {
        val overview = getOverview(webId)
        val now = System.currentTimeMillis()
        val results = coroutineScope {
            overview.books.map { book ->
                async {
                    runCatching { getAllContacts(webId, book.uri) }
                        .map { contacts -> contacts.map { it.toCacheEntity(webId, book.uri, now) } }
                }
            }.awaitAll()
        }
        val entities = results.mapNotNull { it.getOrNull() }.flatten()
        contactDao.upsertAll(entities)
        val failures = results.mapNotNull { it.exceptionOrNull() }
        if (overview.books.isNotEmpty() && failures.size == overview.books.size) {
            throw failures.first()
        }
        if (overview.complete && failures.isEmpty() && overview.books.isNotEmpty()) {
            contactDao.deleteSyncedNotIn(webId, entities.map { it.contactUri })
        }
        return overview
    }

    override suspend fun getOverview(webId: String): ContactsOverview {
        val storage = runCatching { authRepository.getStorages(webId).firstOrNull() }.getOrNull()
        val bookList = if (storage != null) {
            contactsDataModule.books.ensureContainer(webId, storage).getOrNull()
                ?: contactsDataModule.books.list(webId).unwrap()
        } else {
            contactsDataModule.books.list(webId).unwrap()
        }
        val books = bookList.privateAddressBookUris.map { it to true } +
                bookList.publicAddressBookUris.map { it to false }
        val hydrated = coroutineScope {
            books.map { (uri, isPrivate) ->
                async {
                    runCatching {
                        val book = contactsDataModule.books.get(webId, uri).unwrap()
                        Triple(book, isPrivate, uri)
                    }.getOrNull()
                }
            }.awaitAll()
        }.filterNotNull()
        if (books.isNotEmpty() && hydrated.isEmpty()) {
            throw IllegalStateException(
                stringProvider.getString(R.string.contacts_books_unreadable),
            )
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
            complete = hydrated.size == books.size,
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
        contactDao.findByUri(webId, contactUri)?.toDomain()
            ?: contactsDataModule.contacts.get(webId, contactUri).unwrap().toDomain()

    override suspend fun getAllContacts(webId: String, bookUri: String): List<ContactDetail> =
        contactsDataModule.contacts.list(webId, bookUri).unwrap()
            .contacts.map { it.toDomain() }

    override suspend fun createContact(
        webId: String,
        bookUri: String,
        draft: ContactDraft,
        groupUris: List<String>,
    ): ContactDetail {
        val created = contactsDataModule.contacts
            .create(webId, bookUri, draft.toLib(), groupUris)
            .unwrap()
            .toDomain()
        runCatching {
            contactDao.upsert(
                created.toCacheEntity(
                    webId,
                    bookUri,
                    System.currentTimeMillis(),
                    syncState = SyncState.PENDING_CREATE,
                ),
            )
        }
        return created
    }

    override suspend fun updateContact(
        webId: String,
        bookUri: String,
        contactUri: String,
        draft: ContactDraft,
    ): ContactDetail {
        val updated = contactsDataModule.contacts
            .update(webId, bookUri, contactUri, draft.toLib())
            .unwrap()
            .toDomain()
        runCatching {
            contactDao.upsert(updated.toCacheEntity(webId, bookUri, System.currentTimeMillis()))
        }
        return updated
    }

    override suspend fun deleteContact(webId: String, bookUri: String, contactUri: String) {
        contactsDataModule.contacts.delete(webId, bookUri, contactUri).unwrap()
        runCatching { contactDao.deleteByUri(webId, contactUri) }
    }

    override suspend fun deleteAllContacts(webId: String): Int {
        val bookList = contactsDataModule.books.list(webId).unwrap()
        val bookUris = (bookList.privateAddressBookUris + bookList.publicAddressBookUris).distinct()
        var deleted = 0
        bookUris.forEach { bookUri ->
            val contactCount = runCatching {
                contactsDataModule.books.get(webId, bookUri).unwrap().contacts.size
            }.getOrDefault(0)
            val removed = runCatching {
                contactsDataModule.books.delete(webId, bookUri).unwrap()
            }.isSuccess
            if (removed) deleted += contactCount
        }
        runCatching { contactDao.deleteAllForWebId(webId) }
        return deleted
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

    override suspend fun mergeContacts(
        webId: String,
        survivor: ContactRef,
        losers: List<ContactRef>,
    ): ContactDetail {
        val survivorDetail = getContact(webId, survivor.contactUri)
        val loserDetails = losers.map { getContact(webId, it.contactUri) }
        val merged = mergeEngine.mergeDrafts(survivorDetail, loserDetails)
        updateContact(webId, survivor.bookUri, survivor.contactUri, merged)
        if (survivorDetail.photoUri == null) {
            val donor = loserDetails.firstOrNull { it.photoUri != null }
            val photoUri = donor?.photoUri
            if (photoUri != null) {
                runCatching {
                    val bytes = getContactPhoto(webId, photoUri)
                    setContactPhoto(webId, survivor.contactUri, bytes, "image/jpeg")
                }
            }
        }
        losers.forEach { loser ->
            runCatching { deleteContact(webId, loser.bookUri, loser.contactUri) }
        }
        return getContact(webId, survivor.contactUri)
    }

    override suspend fun findMergeSuggestions(webId: String): List<MergeSuggestion> {
        val cached = contactDao.getContacts(webId)
        val bookByContact = cached.associate { it.contactUri to it.bookUri }
        val contacts = cached.map { it.toDomain() }

        val clusters = mergeEngine.cluster(contacts)
        val suggestions = clusters.map { cluster ->
            val signature = mergeEngine.signatureOf(cluster.map { it.uri })
            MergeSuggestion(
                signature = signature,
                members = cluster.map { contact ->
                    MergeMember(
                        bookUri = bookByContact[contact.uri].orEmpty(),
                        contact = contact,
                    )
                },
            )
        }
        val dismissed = mergePrefs.dismissed(webId)
        runCatching { mergePrefs.prune(webId, suggestions.map { it.signature }.toSet()) }
        return suggestions.filter { it.signature !in dismissed }
    }

    override suspend fun queueDelete(webId: String, ref: ContactRef) {
        runCatching { contactDao.updateSyncState(webId, ref.contactUri, SyncState.PENDING_DELETE) }
        enqueueContactOp(
            webId,
            ContactOpType.DELETE,
            payloadJson.encodeToString(ContactRef.serializer(), ref),
        )
    }

    override suspend fun queueMerge(webId: String, survivor: ContactRef, losers: List<ContactRef>) {
        losers.forEach {
            runCatching { contactDao.updateSyncState(webId, it.contactUri, SyncState.PENDING_DELETE) }
        }
        enqueueContactOp(
            webId,
            ContactOpType.MERGE,
            payloadJson.encodeToString(
                ContactMergePayload.serializer(),
                ContactMergePayload(survivor, losers),
            ),
        )
    }

    override suspend fun queueDeleteAll(webId: String) {
        runCatching {
            contactDao.getContacts(webId).forEach {
                contactDao.updateSyncState(webId, it.contactUri, SyncState.PENDING_DELETE)
            }
        }
        enqueueContactOp(webId, ContactOpType.DELETE_ALL, "{}")
    }

    override suspend fun drainContactOutbox(webId: String): Boolean {
        val now = System.currentTimeMillis()
        val ops = contactOutboxDao.dueOps(webId, now)
        var allOk = true
        for (op in ops) {
            val result = runCatching { executeContactOp(webId, op) }
            if (result.isSuccess) {
                contactOutboxDao.deleteById(op.id)
            } else {
                allOk = false
                val attempts = op.attempts + 1
                contactOutboxDao.update(
                    op.copy(
                        status = OpStatus.FAILED,
                        attempts = attempts,
                        nextRetryAt = now + backoffMillis(attempts),
                        lastError = result.exceptionOrNull()?.message,
                        updatedAt = now,
                    ),
                )
            }
        }
        return allOk
    }

    private suspend fun executeContactOp(webId: String, op: ContactOutboxOpEntity) {
        when (op.type) {
            ContactOpType.DELETE -> {
                val ref = payloadJson.decodeFromString(ContactRef.serializer(), op.payload)
                deleteContact(webId, ref.bookUri, ref.contactUri)
            }

            ContactOpType.MERGE -> {
                val p = payloadJson.decodeFromString(ContactMergePayload.serializer(), op.payload)
                mergeContacts(webId, p.survivor, p.losers)
            }

            ContactOpType.DELETE_ALL -> {
                deleteAllContacts(webId)
            }
        }
    }

    private suspend fun enqueueContactOp(webId: String, type: ContactOpType, payload: String) {
        val now = System.currentTimeMillis()
        contactOutboxDao.insert(
            ContactOutboxOpEntity(
                webId = webId,
                type = type,
                payload = payload,
                status = OpStatus.PENDING,
                attempts = 0,
                nextRetryAt = 0,
                lastError = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        ContactOutboxWorker.enqueue(workManager)
    }

    private fun backoffMillis(attempts: Int): Long =
        minOf(30_000L * (1L shl minOf(attempts, 6)), 3_600_000L)

    override suspend fun clearCacheForWebId(webId: String) {
        runCatching { contactDao.deleteAllForWebId(webId) }
        runCatching { contactOutboxDao.deleteAllForWebId(webId) }
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

    private fun <T : Parcelable> SolidResult<T>.unwrap(): T = getOrThrow()
}
