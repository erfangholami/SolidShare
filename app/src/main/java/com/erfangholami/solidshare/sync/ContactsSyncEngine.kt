package com.erfangholami.solidshare.sync

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.erfangholami.solidshare.data.device.DeviceContactsSource
import com.erfangholami.solidshare.data.repo.contacts.ContactsRepository
import com.erfangholami.solidshare.domain.model.ContactAddress
import com.erfangholami.solidshare.domain.model.ContactAddressType
import com.erfangholami.solidshare.domain.model.ContactDetail
import com.erfangholami.solidshare.domain.model.ContactEmailType
import com.erfangholami.solidshare.domain.model.ContactGroup
import com.erfangholami.solidshare.domain.model.ContactPhoneType
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class ContactsSyncEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val contactsRepository: ContactsRepository,
    private val deviceContactsSource: DeviceContactsSource,
) {

    suspend fun sync(webId: String) {
        if (!hasPermission(Manifest.permission.READ_CONTACTS) ||
            !hasPermission(Manifest.permission.WRITE_CONTACTS)
        ) {
            return
        }
        val account = Account(webId, SolidAccounts.ACCOUNT_TYPE)
        ensureAccountSettings(account)
        uploadPhase(webId, account)
        downloadPhase(webId, account)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED

    private suspend fun pushPhoto(
        webId: String,
        contactUri: String,
        photo: ByteArray?,
        photoMime: String?,
    ) {
        if (photo == null) return
        runCatching {
            contactsRepository.setContactPhoto(
                webId,
                contactUri,
                photo,
                photoMime ?: "image/jpeg",
            )
        }
    }

    private suspend fun uploadPhase(webId: String, account: Account) {
        data class PendingRow(
            val rawId: Long,
            val sourceId: String?,
            val bookUri: String?,
            val deleted: Boolean,
            val photoKey: String?,
        )

        val pending = mutableListOf<PendingRow>()
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.SOURCE_ID,
                ContactsContract.RawContacts.SYNC1,
                ContactsContract.RawContacts.SYNC3,
                ContactsContract.RawContacts.DELETED,
            ),
            "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND " +
                    "${ContactsContract.RawContacts.ACCOUNT_NAME} = ? AND " +
                    "(${ContactsContract.RawContacts.DIRTY} = 1 OR " +
                    "${ContactsContract.RawContacts.DELETED} = 1)",
            arrayOf(account.type, account.name),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                pending.add(
                    PendingRow(
                        rawId = cursor.getLong(0),
                        sourceId = cursor.getString(1),
                        bookUri = cursor.getString(2),
                        photoKey = cursor.getString(3),
                        deleted = cursor.getInt(4) == 1,
                    ),
                )
            }
        }
        if (pending.isEmpty()) return

        var ensuredDefaultBook: String? = null
        suspend fun defaultBook(): String =
            ensuredDefaultBook ?: contactsRepository.ensureDefaultAddressBook(webId)
                .also { ensuredDefaultBook = it }

        pending.forEach { row ->
            if (row.deleted) {
                if (row.sourceId != null) {
                    val bookUri = row.bookUri ?: deriveBookUri(row.sourceId)
                    runCatching {
                        if (bookUri != null) {
                            contactsRepository.deleteContact(webId, bookUri, row.sourceId)
                        }
                    }
                }
                finalizeRowDeletion(row.rawId)
                return@forEach
            }

            val data = deviceContactsSource.loadContact(row.rawId)
            val deviceGroupUris = deviceGroupUrisFor(row.rawId)
            val localPhotoKey = data.photo?.let { md5(it) }

            if (row.sourceId == null) {
                runCatching {
                    val bookUri = defaultBook()
                    val created = contactsRepository.createContact(webId, bookUri, data.draft)
                    pushPhoto(webId, created.uri, data.photo, data.photoMime)
                    syncGroupMemberships(webId, bookUri, created.uri, deviceGroupUris)
                    val fresh = contactsRepository.getContact(webId, created.uri)
                    markRowClean(
                        rawId = row.rawId,
                        sourceId = created.uri,
                        bookUri = bookUri,
                        fieldsHash = fieldsHash(fresh, deviceGroupUris),
                        photoKey = localPhotoKey,
                    )
                }
            } else {
                val bookUri = row.bookUri ?: deriveBookUri(row.sourceId) ?: return@forEach
                val updateResult = runCatching {
                    contactsRepository.updateContact(webId, bookUri, row.sourceId, data.draft)
                }
                if (updateResult.isFailure) {
                    val gone = runCatching {
                        contactsRepository.getContact(webId, row.sourceId)
                    }.isFailure
                    if (gone) finalizeRowDeletion(row.rawId)
                    return@forEach
                }
                if (localPhotoKey != row.photoKey) {
                    runCatching {
                        if (data.photo != null) {
                            contactsRepository.setContactPhoto(
                                webId,
                                row.sourceId,
                                data.photo,
                                data.photoMime ?: "image/jpeg",
                            )
                        } else {
                            contactsRepository.removeContactPhoto(webId, row.sourceId)
                        }
                    }
                }
                runCatching {
                    syncGroupMemberships(webId, bookUri, row.sourceId, deviceGroupUris)
                }
                val fresh = runCatching {
                    contactsRepository.getContact(webId, row.sourceId)
                }.getOrNull()
                markRowClean(
                    rawId = row.rawId,
                    sourceId = row.sourceId,
                    bookUri = bookUri,
                    fieldsHash = fresh?.let { fieldsHash(it, deviceGroupUris) } ?: "",
                    photoKey = localPhotoKey,
                )
            }
        }
    }

    private suspend fun downloadPhase(webId: String, account: Account) {
        val overview = runCatching { contactsRepository.getOverview(webId) }.getOrNull() ?: return
        val podContactUris = mutableSetOf<String>()
        val podGroupUris = mutableSetOf<String>()

        overview.books.forEach { book ->
            val contacts = runCatching {
                contactsRepository.getAllContacts(webId, book.uri)
            }.getOrElse { return@forEach }
            val groups = runCatching {
                contactsRepository.getGroups(webId, book.uri)
            }.getOrDefault(emptyList())

            val groupRowIds = ensureDeviceGroups(account, groups)
            podGroupUris.addAll(groups.map { it.uri })

            contacts.forEach { contact ->
                podContactUris.add(contact.uri)
                val memberships = groups.filter { contact.uri in it.memberUris }
                upsertDeviceContact(webId, account, book.uri, contact, memberships, groupRowIds)
            }
        }

        deleteVanishedRows(account, podContactUris)
        deleteVanishedGroups(account, podGroupUris)
    }

    private suspend fun upsertDeviceContact(
        webId: String,
        account: Account,
        bookUri: String,
        contact: ContactDetail,
        memberships: List<ContactGroup>,
        groupRowIds: Map<String, Long>,
    ) {
        val membershipUris = memberships.map { it.uri }.sorted()
        val hash = fieldsHash(contact, membershipUris)
        val photoKey = contact.photoUri

        var rawId: Long? = null
        var storedHash: String? = null
        var storedPhotoKey: String? = null
        var dirty = false
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.SYNC2,
                ContactsContract.RawContacts.SYNC3,
                ContactsContract.RawContacts.DIRTY,
            ),
            "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND " +
                    "${ContactsContract.RawContacts.ACCOUNT_NAME} = ? AND " +
                    "${ContactsContract.RawContacts.SOURCE_ID} = ? AND " +
                    "${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf(account.type, account.name, contact.uri),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                rawId = cursor.getLong(0)
                storedHash = cursor.getString(1)
                storedPhotoKey = cursor.getString(2)
                dirty = cursor.getInt(3) == 1
            }
        }

        if (rawId == null) {
            insertDeviceContact(webId, account, bookUri, contact, membershipUris, groupRowIds, hash)
            return
        }
        if (dirty) return
        val id = rawId ?: return

        if (storedHash != hash) {
            rewriteDeviceContact(id, contact, membershipUris, groupRowIds, hash, bookUri)
        }
        if (storedPhotoKey != photoKey) {
            rewriteDevicePhoto(webId, id, contact, photoKey)
        }
    }

    private suspend fun insertDeviceContact(
        webId: String,
        account: Account,
        bookUri: String,
        contact: ContactDetail,
        membershipUris: List<String>,
        groupRowIds: Map<String, Long>,
        hash: String,
    ) {
        val ops = arrayListOf<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newInsert(syncAdapterUri(ContactsContract.RawContacts.CONTENT_URI))
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
                .withValue(ContactsContract.RawContacts.SOURCE_ID, contact.uri)
                .withValue(ContactsContract.RawContacts.SYNC1, bookUri)
                .withValue(ContactsContract.RawContacts.SYNC2, hash)
                .withValue(ContactsContract.RawContacts.SYNC3, contact.photoUri)
                .build(),
        )
        dataOperations(contact, membershipUris, groupRowIds).forEach { builder ->
            ops.add(
                builder
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .build(),
            )
        }
        val photoBytes = contact.photoUri?.let { uri ->
            runCatching { contactsRepository.getContactPhoto(webId, uri) }.getOrNull()
        }
        if (photoBytes != null) {
            ops.add(
                ContentProviderOperation.newInsert(syncAdapterUri(ContactsContract.Data.CONTENT_URI))
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE,
                    )
                    .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                    .build(),
            )
        }
        applyBatch(ops)
    }

    private fun rewriteDeviceContact(
        rawId: Long,
        contact: ContactDetail,
        membershipUris: List<String>,
        groupRowIds: Map<String, Long>,
        hash: String,
        bookUri: String,
    ) {
        val ops = arrayListOf<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newDelete(syncAdapterUri(ContactsContract.Data.CONTENT_URI))
                .withSelection(
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND " +
                            "${ContactsContract.Data.MIMETYPE} != ?",
                    arrayOf(
                        rawId.toString(),
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE,
                    ),
                )
                .build(),
        )
        dataOperations(contact, membershipUris, groupRowIds).forEach { builder ->
            ops.add(
                builder
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                    .build(),
            )
        }
        ops.add(
            ContentProviderOperation.newUpdate(syncAdapterUri(ContactsContract.RawContacts.CONTENT_URI))
                .withSelection("${ContactsContract.RawContacts._ID} = ?", arrayOf(rawId.toString()))
                .withValue(ContactsContract.RawContacts.SYNC1, bookUri)
                .withValue(ContactsContract.RawContacts.SYNC2, hash)
                .withValue(ContactsContract.RawContacts.DIRTY, 0)
                .build(),
        )
        applyBatch(ops)
    }

    private suspend fun rewriteDevicePhoto(
        webId: String,
        rawId: Long,
        contact: ContactDetail,
        photoKey: String?,
    ) {
        val ops = arrayListOf<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newDelete(syncAdapterUri(ContactsContract.Data.CONTENT_URI))
                .withSelection(
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND " +
                            "${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(
                        rawId.toString(),
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE,
                    ),
                )
                .build(),
        )
        val photoBytes = contact.photoUri?.let { uri ->
            runCatching { contactsRepository.getContactPhoto(webId, uri) }.getOrNull()
        }
        if (photoBytes != null) {
            ops.add(
                ContentProviderOperation.newInsert(syncAdapterUri(ContactsContract.Data.CONTENT_URI))
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE,
                    )
                    .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                    .build(),
            )
        }
        ops.add(
            ContentProviderOperation.newUpdate(syncAdapterUri(ContactsContract.RawContacts.CONTENT_URI))
                .withSelection("${ContactsContract.RawContacts._ID} = ?", arrayOf(rawId.toString()))
                .withValue(ContactsContract.RawContacts.SYNC3, photoKey)
                .withValue(ContactsContract.RawContacts.DIRTY, 0)
                .build(),
        )
        applyBatch(ops)
    }

    private fun dataOperations(
        contact: ContactDetail,
        membershipUris: List<String>,
        groupRowIds: Map<String, Long>,
    ): List<ContentProviderOperation.Builder> {
        val builders = mutableListOf<ContentProviderOperation.Builder>()
        builders.add(
            newDataInsert(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(
                    ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                    contact.fullName,
                )
                .withValue(
                    ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                    contact.givenName,
                )
                .withValue(
                    ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
                    contact.familyName,
                )
                .withValue(
                    ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,
                    contact.middleName,
                )
                .withValue(
                    ContactsContract.CommonDataKinds.StructuredName.PREFIX,
                    contact.namePrefix,
                )
                .withValue(
                    ContactsContract.CommonDataKinds.StructuredName.SUFFIX,
                    contact.nameSuffix,
                ),
        )
        contact.nickname?.let { nickname ->
            builders.add(
                newDataInsert(ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Nickname.NAME, nickname)
                    .withValue(
                        ContactsContract.CommonDataKinds.Nickname.TYPE,
                        ContactsContract.CommonDataKinds.Nickname.TYPE_DEFAULT,
                    ),
            )
        }
        contact.phones.forEach { phone ->
            builders.add(
                newDataInsert(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone.number)
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        phoneTypeTo(phone.type),
                    ),
            )
        }
        contact.emails.forEach { email ->
            builders.add(
                newDataInsert(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.address)
                    .withValue(
                        ContactsContract.CommonDataKinds.Email.TYPE,
                        emailTypeTo(email.type),
                    ),
            )
        }
        contact.addresses.forEach { address ->
            builders.add(
                newDataInsert(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredPostal.STREET,
                        address.street,
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredPostal.CITY,
                        address.locality,
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredPostal.REGION,
                        address.region,
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE,
                        address.postalCode,
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY,
                        address.countryName,
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredPostal.POBOX,
                        address.poBox,
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
                        addressTypeTo(address.type),
                    ),
            )
        }
        if (contact.organization != null || contact.jobTitle != null ||
            contact.organizationUnit != null
        ) {
            builders.add(
                newDataInsert(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(
                        ContactsContract.CommonDataKinds.Organization.COMPANY,
                        contact.organization,
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.Organization.DEPARTMENT,
                        contact.organizationUnit,
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.Organization.TITLE,
                        contact.jobTitle,
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.Organization.TYPE,
                        ContactsContract.CommonDataKinds.Organization.TYPE_WORK,
                    ),
            )
        }
        contact.note?.let { note ->
            builders.add(
                newDataInsert(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, note),
            )
        }
        contact.birthday?.let { birthday ->
            builders.add(
                newDataInsert(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, birthday)
                    .withValue(
                        ContactsContract.CommonDataKinds.Event.TYPE,
                        ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY,
                    ),
            )
        }
        contact.anniversary?.let { anniversary ->
            builders.add(
                newDataInsert(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Event.START_DATE, anniversary)
                    .withValue(
                        ContactsContract.CommonDataKinds.Event.TYPE,
                        ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY,
                    ),
            )
        }
        contact.links.forEach { link ->
            builders.add(
                newDataInsert(ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Website.URL, link.value),
            )
        }
        membershipUris.forEach { groupUri ->
            groupRowIds[groupUri]?.let { groupRowId ->
                builders.add(
                    newDataInsert(
                        ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
                    )
                        .withValue(
                            ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID,
                            groupRowId,
                        ),
                )
            }
        }
        return builders
    }

    private fun phoneTypeTo(type: ContactPhoneType): Int = when (type) {
        ContactPhoneType.CELL -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
        ContactPhoneType.HOME -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
        ContactPhoneType.WORK -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
        ContactPhoneType.FAX -> ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK
        ContactPhoneType.PAGER -> ContactsContract.CommonDataKinds.Phone.TYPE_PAGER
        else -> ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
    }

    private fun emailTypeTo(type: ContactEmailType): Int = when (type) {
        ContactEmailType.HOME -> ContactsContract.CommonDataKinds.Email.TYPE_HOME
        ContactEmailType.WORK -> ContactsContract.CommonDataKinds.Email.TYPE_WORK
        ContactEmailType.OTHER -> ContactsContract.CommonDataKinds.Email.TYPE_OTHER
    }

    private fun addressTypeTo(type: ContactAddressType): Int = when (type) {
        ContactAddressType.HOME -> ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME
        ContactAddressType.WORK -> ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK
        ContactAddressType.OTHER -> ContactsContract.CommonDataKinds.StructuredPostal.TYPE_OTHER
    }

    private fun newDataInsert(mimeType: String): ContentProviderOperation.Builder =
        ContentProviderOperation.newInsert(syncAdapterUri(ContactsContract.Data.CONTENT_URI))
            .withValue(ContactsContract.Data.MIMETYPE, mimeType)

    private fun ensureDeviceGroups(
        account: Account,
        groups: List<ContactGroup>,
    ): Map<String, Long> {
        val existing = mutableMapOf<String, Pair<Long, String?>>()
        context.contentResolver.query(
            ContactsContract.Groups.CONTENT_URI,
            arrayOf(
                ContactsContract.Groups._ID,
                ContactsContract.Groups.SOURCE_ID,
                ContactsContract.Groups.TITLE,
            ),
            "${ContactsContract.Groups.ACCOUNT_TYPE} = ? AND " +
                    "${ContactsContract.Groups.ACCOUNT_NAME} = ? AND " +
                    "${ContactsContract.Groups.DELETED} = 0",
            arrayOf(account.type, account.name),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val sourceId = cursor.getString(1) ?: continue
                existing[sourceId] = cursor.getLong(0) to cursor.getString(2)
            }
        }

        val result = mutableMapOf<String, Long>()
        val ops = arrayListOf<ContentProviderOperation>()
        groups.forEach { group ->
            val known = existing[group.uri]
            if (known == null) {
                ops.add(
                    ContentProviderOperation.newInsert(
                        syncAdapterUri(ContactsContract.Groups.CONTENT_URI),
                    )
                        .withValue(ContactsContract.Groups.ACCOUNT_TYPE, account.type)
                        .withValue(ContactsContract.Groups.ACCOUNT_NAME, account.name)
                        .withValue(ContactsContract.Groups.SOURCE_ID, group.uri)
                        .withValue(ContactsContract.Groups.TITLE, group.name)
                        .withValue(ContactsContract.Groups.GROUP_VISIBLE, 1)
                        .build(),
                )
            } else {
                result[group.uri] = known.first
                if (known.second != group.name) {
                    ops.add(
                        ContentProviderOperation.newUpdate(
                            syncAdapterUri(ContactsContract.Groups.CONTENT_URI),
                        )
                            .withSelection(
                                "${ContactsContract.Groups._ID} = ?",
                                arrayOf(known.first.toString()),
                            )
                            .withValue(ContactsContract.Groups.TITLE, group.name)
                            .build(),
                    )
                }
            }
        }
        if (ops.isNotEmpty()) {
            applyBatch(ops)
            context.contentResolver.query(
                ContactsContract.Groups.CONTENT_URI,
                arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.SOURCE_ID),
                "${ContactsContract.Groups.ACCOUNT_TYPE} = ? AND " +
                        "${ContactsContract.Groups.ACCOUNT_NAME} = ? AND " +
                        "${ContactsContract.Groups.DELETED} = 0",
                arrayOf(account.type, account.name),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(1)?.let { result[it] = cursor.getLong(0) }
                }
            }
        }
        return result
    }

    private fun deleteVanishedRows(account: Account, podContactUris: Set<String>) {
        val vanished = mutableListOf<Long>()
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.SOURCE_ID,
                ContactsContract.RawContacts.DIRTY,
            ),
            "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND " +
                    "${ContactsContract.RawContacts.ACCOUNT_NAME} = ? AND " +
                    "${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf(account.type, account.name),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val sourceId = cursor.getString(1)
                val dirty = cursor.getInt(2) == 1
                if (sourceId != null && !dirty && sourceId !in podContactUris) {
                    vanished.add(cursor.getLong(0))
                }
            }
        }
        vanished.forEach { finalizeRowDeletion(it) }
    }

    private fun deleteVanishedGroups(account: Account, podGroupUris: Set<String>) {
        val vanished = mutableListOf<Long>()
        context.contentResolver.query(
            ContactsContract.Groups.CONTENT_URI,
            arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.SOURCE_ID),
            "${ContactsContract.Groups.ACCOUNT_TYPE} = ? AND " +
                    "${ContactsContract.Groups.ACCOUNT_NAME} = ? AND " +
                    "${ContactsContract.Groups.DELETED} = 0",
            arrayOf(account.type, account.name),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val sourceId = cursor.getString(1)
                if (sourceId != null && sourceId !in podGroupUris) {
                    vanished.add(cursor.getLong(0))
                }
            }
        }
        if (vanished.isEmpty()) return
        val ops = vanished.map { id ->
            ContentProviderOperation.newDelete(
                ContentUris.withAppendedId(
                    syncAdapterUri(ContactsContract.Groups.CONTENT_URI),
                    id,
                ),
            ).build()
        }
        applyBatch(ArrayList(ops))
    }

    private suspend fun syncGroupMemberships(
        webId: String,
        bookUri: String,
        contactUri: String,
        deviceGroupUris: List<String>,
    ) {
        val podGroups = runCatching {
            contactsRepository.getGroups(webId, bookUri)
        }.getOrDefault(emptyList())
        val podMembership = podGroups.filter { contactUri in it.memberUris }.map { it.uri }.toSet()
        val deviceMembership = deviceGroupUris.toSet()
        (deviceMembership - podMembership).forEach { groupUri ->
            if (podGroups.any { it.uri == groupUri }) {
                runCatching { contactsRepository.addContactToGroup(webId, contactUri, groupUri) }
            }
        }
        (podMembership - deviceMembership).forEach { groupUri ->
            runCatching { contactsRepository.removeContactFromGroup(webId, contactUri, groupUri) }
        }
    }

    private fun deviceGroupUrisFor(rawContactId: Long): List<String> {
        val groupRowIds = mutableListOf<Long>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID),
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND " +
                    "${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(
                rawContactId.toString(),
                ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
            ),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (!cursor.isNull(0)) groupRowIds.add(cursor.getLong(0))
            }
        }
        if (groupRowIds.isEmpty()) return emptyList()
        val uris = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.Groups.CONTENT_URI,
            arrayOf(ContactsContract.Groups.SOURCE_ID),
            "${ContactsContract.Groups._ID} IN (${groupRowIds.joinToString(",")})",
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let { uris.add(it) }
            }
        }
        return uris
    }

    private fun markRowClean(
        rawId: Long,
        sourceId: String,
        bookUri: String,
        fieldsHash: String,
        photoKey: String?,
    ) {
        val ops = arrayListOf<ContentProviderOperation>(
            ContentProviderOperation.newUpdate(
                syncAdapterUri(ContactsContract.RawContacts.CONTENT_URI),
            )
                .withSelection(
                    "${ContactsContract.RawContacts._ID} = ?",
                    arrayOf(rawId.toString()),
                )
                .withValue(ContactsContract.RawContacts.SOURCE_ID, sourceId)
                .withValue(ContactsContract.RawContacts.SYNC1, bookUri)
                .withValue(ContactsContract.RawContacts.SYNC2, fieldsHash)
                .withValue(ContactsContract.RawContacts.SYNC3, photoKey)
                .withValue(ContactsContract.RawContacts.DIRTY, 0)
                .build(),
        )
        applyBatch(ops)
    }

    private fun finalizeRowDeletion(rawId: Long) {
        context.contentResolver.delete(
            ContentUris.withAppendedId(
                syncAdapterUri(ContactsContract.RawContacts.CONTENT_URI),
                rawId,
            ),
            null,
            null,
        )
    }

    private fun ensureAccountSettings(account: Account) {
        var exists = false
        context.contentResolver.query(
            ContactsContract.Settings.CONTENT_URI,
            arrayOf(ContactsContract.Settings.UNGROUPED_VISIBLE),
            "${ContactsContract.Settings.ACCOUNT_TYPE} = ? AND " +
                    "${ContactsContract.Settings.ACCOUNT_NAME} = ?",
            arrayOf(account.type, account.name),
            null,
        )?.use { cursor -> exists = cursor.moveToFirst() }
        if (exists) return
        val uri = ContactsContract.Settings.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(ContactsContract.Settings.ACCOUNT_NAME, account.name)
            .appendQueryParameter(ContactsContract.Settings.ACCOUNT_TYPE, account.type)
            .build()
        runCatching {
            context.contentResolver.insert(
                uri,
                android.content.ContentValues().apply {
                    put(ContactsContract.Settings.ACCOUNT_NAME, account.name)
                    put(ContactsContract.Settings.ACCOUNT_TYPE, account.type)
                    put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1)
                },
            )
        }
    }

    private fun applyBatch(ops: ArrayList<ContentProviderOperation>) {
        if (ops.isEmpty()) return
        ops.chunked(400).forEach { chunk ->
            runCatching {
                context.contentResolver.applyBatch(
                    ContactsContract.AUTHORITY,
                    ArrayList(chunk),
                )
            }
        }
    }

    private fun syncAdapterUri(base: Uri): Uri = base.buildUpon()
        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
        .build()

    private fun deriveBookUri(contactUri: String): String? {
        val personIndex = contactUri.lastIndexOf("/Person/")
        if (personIndex < 0) return null
        return "${contactUri.substring(0, personIndex + 1)}index.ttl#this"
    }

    private fun fieldsHash(contact: ContactDetail, membershipUris: List<String>): String {
        val canonical = buildString {
            append(contact.fullName).append('|')
            append(contact.givenName.orEmpty()).append('|')
            append(contact.familyName.orEmpty()).append('|')
            append(contact.middleName.orEmpty()).append('|')
            append(contact.namePrefix.orEmpty()).append('|')
            append(contact.nameSuffix.orEmpty()).append('|')
            append(contact.nickname.orEmpty()).append('|')
            append(
                contact.phones.map { "${it.type}:${it.number}" }.sorted().joinToString(","),
            ).append('|')
            append(
                contact.emails.map { "${it.type}:${it.address}" }.sorted().joinToString(","),
            ).append('|')
            append(
                contact.addresses.map { addressKey(it) }.sorted().joinToString(","),
            ).append('|')
            append(contact.birthday.orEmpty()).append('|')
            append(contact.anniversary.orEmpty()).append('|')
            append(contact.organization.orEmpty()).append('|')
            append(contact.organizationUnit.orEmpty()).append('|')
            append(contact.jobTitle.orEmpty()).append('|')
            append(contact.note.orEmpty()).append('|')
            append(
                contact.links.map { "${it.type}:${it.value}" }.sorted().joinToString(","),
            ).append('|')
            append(membershipUris.sorted().joinToString(","))
        }
        return md5(canonical.toByteArray(Charsets.UTF_8))
    }

    private fun addressKey(address: ContactAddress): String =
        listOf(
            address.type.name,
            address.street.orEmpty(),
            address.locality.orEmpty(),
            address.region.orEmpty(),
            address.postalCode.orEmpty(),
            address.countryName.orEmpty(),
            address.poBox.orEmpty(),
        ).joinToString(";")

    private fun md5(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
