package com.erfangholami.solidshare.data.device

import android.content.Context
import android.provider.ContactsContract
import com.erfangholami.solidshare.domain.model.ContactAddress
import com.erfangholami.solidshare.domain.model.ContactAddressType
import com.erfangholami.solidshare.domain.model.ContactDraft
import com.erfangholami.solidshare.domain.model.ContactEmail
import com.erfangholami.solidshare.domain.model.ContactEmailType
import com.erfangholami.solidshare.domain.model.ContactLinkType
import com.erfangholami.solidshare.domain.model.ContactPhone
import com.erfangholami.solidshare.domain.model.ContactPhoneType
import com.erfangholami.solidshare.domain.model.ContactWebLink
import com.erfangholami.solidshare.domain.model.DeviceAccount
import com.erfangholami.solidshare.domain.model.DeviceContactData
import com.erfangholami.solidshare.domain.model.DeviceContactRef
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DeviceContactsSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    suspend fun listAccounts(excludeAccountType: String): List<DeviceAccount> =
        withContext(Dispatchers.IO) {
            val grouped = linkedMapOf<Pair<String?, String?>, MutableList<DeviceContactRef>>()
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.RawContacts._ID,
                    ContactsContract.RawContacts.ACCOUNT_TYPE,
                    ContactsContract.RawContacts.ACCOUNT_NAME,
                    ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY,
                ),
                "${ContactsContract.RawContacts.DELETED} = 0",
                null,
                ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts._ID)
                val typeIndex =
                    cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_TYPE)
                val nameIndex =
                    cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_NAME)
                val displayIndex = cursor.getColumnIndexOrThrow(
                    ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY,
                )
                while (cursor.moveToNext()) {
                    val accountType = cursor.getString(typeIndex)
                    if (accountType == excludeAccountType) continue
                    val display = cursor.getString(displayIndex) ?: continue
                    if (display.isBlank()) continue
                    val key = accountType to cursor.getString(nameIndex)
                    grouped.getOrPut(key) { mutableListOf() }
                        .add(DeviceContactRef(cursor.getLong(idIndex), display))
                }
            }
            grouped.map { (key, contacts) ->
                DeviceAccount(
                    accountType = key.first,
                    accountName = key.second,
                    contacts = contacts,
                )
            }.sortedByDescending { it.contacts.size }
        }

    suspend fun listImportableRawContactIds(excludeAccountType: String): List<Long> =
        withContext(Dispatchers.IO) {
            val ids = mutableListOf<Long>()
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                "${ContactsContract.RawContacts.DELETED} = 0 AND " +
                        "(${ContactsContract.RawContacts.ACCOUNT_TYPE} IS NULL OR " +
                        "${ContactsContract.RawContacts.ACCOUNT_TYPE} != ?)",
                arrayOf(excludeAccountType),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    ids.add(cursor.getLong(0))
                }
            }
            ids
        }

    suspend fun listRawContactIds(accountType: String?, accountName: String?): List<Long> =
        withContext(Dispatchers.IO) {
            val ids = mutableListOf<Long>()
            val conditions = mutableListOf<String>()
            val args = mutableListOf<String>()
            if (accountType == null) {
                conditions.add("${ContactsContract.RawContacts.ACCOUNT_TYPE} IS NULL")
            } else {
                conditions.add("${ContactsContract.RawContacts.ACCOUNT_TYPE} = ?")
                args.add(accountType)
            }
            if (accountName == null) {
                conditions.add("${ContactsContract.RawContacts.ACCOUNT_NAME} IS NULL")
            } else {
                conditions.add("${ContactsContract.RawContacts.ACCOUNT_NAME} = ?")
                args.add(accountName)
            }
            conditions.add("${ContactsContract.RawContacts.DELETED} = 0")
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                conditions.joinToString(" AND "),
                args.toTypedArray(),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    ids.add(cursor.getLong(0))
                }
            }
            ids
        }

    suspend fun loadContact(rawContactId: Long): DeviceContactData =
        withContext(Dispatchers.IO) {
            var given: String? = null
            var family: String? = null
            var middle: String? = null
            var prefix: String? = null
            var suffix: String? = null
            var display: String? = null
            var nickname: String? = null
            val phones = mutableListOf<ContactPhone>()
            val emails = mutableListOf<ContactEmail>()
            val addresses = mutableListOf<ContactAddress>()
            var organization: String? = null
            var organizationUnit: String? = null
            var jobTitle: String? = null
            var note: String? = null
            var birthday: String? = null
            var anniversary: String? = null
            val links = mutableListOf<ContactWebLink>()
            var photo: ByteArray? = null

            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                null,
                "${ContactsContract.Data.RAW_CONTACT_ID} = ?",
                arrayOf(rawContactId.toString()),
                null,
            )?.use { cursor ->
                val mimeIndex = cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
                while (cursor.moveToNext()) {
                    when (cursor.getString(mimeIndex)) {
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                            display = cursor.getStringByName(
                                ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                            )
                            given = cursor.getStringByName(
                                ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                            )
                            family = cursor.getStringByName(
                                ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
                            )
                            middle = cursor.getStringByName(
                                ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,
                            )
                            prefix = cursor.getStringByName(
                                ContactsContract.CommonDataKinds.StructuredName.PREFIX,
                            )
                            suffix = cursor.getStringByName(
                                ContactsContract.CommonDataKinds.StructuredName.SUFFIX,
                            )
                        }

                        ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE -> {
                            nickname = cursor.getStringByName(
                                ContactsContract.CommonDataKinds.Nickname.NAME,
                            )?.takeIf { it.isNotBlank() } ?: nickname
                        }

                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                            cursor.getStringByName(
                                ContactsContract.CommonDataKinds.Phone.NUMBER,
                            )?.let { number ->
                                phones.add(
                                    ContactPhone(
                                        number = number,
                                        type = phoneTypeFrom(
                                            cursor.getIntByName(
                                                ContactsContract.CommonDataKinds.Phone.TYPE,
                                            ),
                                        ),
                                    ),
                                )
                            }
                        }

                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
                            cursor.getStringByName(
                                ContactsContract.CommonDataKinds.Email.ADDRESS,
                            )?.let { address ->
                                emails.add(
                                    ContactEmail(
                                        address = address,
                                        type = emailTypeFrom(
                                            cursor.getIntByName(
                                                ContactsContract.CommonDataKinds.Email.TYPE,
                                            ),
                                        ),
                                    ),
                                )
                            }
                        }

                        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                            val address = ContactAddress(
                                street = cursor.getStringByName(
                                    ContactsContract.CommonDataKinds.StructuredPostal.STREET,
                                ),
                                locality = cursor.getStringByName(
                                    ContactsContract.CommonDataKinds.StructuredPostal.CITY,
                                ),
                                region = cursor.getStringByName(
                                    ContactsContract.CommonDataKinds.StructuredPostal.REGION,
                                ),
                                postalCode = cursor.getStringByName(
                                    ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE,
                                ),
                                countryName = cursor.getStringByName(
                                    ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY,
                                ),
                                poBox = cursor.getStringByName(
                                    ContactsContract.CommonDataKinds.StructuredPostal.POBOX,
                                ),
                                type = addressTypeFrom(
                                    cursor.getIntByName(
                                        ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
                                    ),
                                ),
                            )
                            val isEmpty = listOf(
                                address.street, address.locality, address.region,
                                address.postalCode, address.countryName, address.poBox,
                            ).all { it.isNullOrBlank() }
                            if (!isEmpty) addresses.add(address)
                        }

                        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                            organization = cursor.getStringByName(
                                ContactsContract.CommonDataKinds.Organization.COMPANY,
                            ) ?: organization
                            organizationUnit = cursor.getStringByName(
                                ContactsContract.CommonDataKinds.Organization.DEPARTMENT,
                            ) ?: organizationUnit
                            jobTitle = cursor.getStringByName(
                                ContactsContract.CommonDataKinds.Organization.TITLE,
                            ) ?: jobTitle
                        }

                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
                            note = cursor.getStringByName(
                                ContactsContract.CommonDataKinds.Note.NOTE,
                            )?.takeIf { it.isNotBlank() } ?: note
                        }

                        ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE -> {
                            val startDate = cursor.getStringByName(
                                ContactsContract.CommonDataKinds.Event.START_DATE,
                            )
                            when (cursor.getIntByName(ContactsContract.CommonDataKinds.Event.TYPE)) {
                                ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY ->
                                    birthday = startDate ?: birthday

                                ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY ->
                                    anniversary = startDate ?: anniversary
                            }
                        }

                        ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE -> {
                            cursor.getStringByName(
                                ContactsContract.CommonDataKinds.Website.URL,
                            )?.let { links.add(ContactWebLink(ContactLinkType.HOMEPAGE, it)) }
                        }

                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE -> {
                            val photoIndex = cursor.getColumnIndex(
                                ContactsContract.CommonDataKinds.Photo.PHOTO,
                            )
                            if (photoIndex >= 0 && !cursor.isNull(photoIndex)) {
                                photo = cursor.getBlob(photoIndex)
                            }
                        }
                    }
                }
            }

            DeviceContactData(
                draft = ContactDraft(
                    fullName = display?.takeIf { it.isNotBlank() },
                    givenName = given?.takeIf { it.isNotBlank() },
                    familyName = family?.takeIf { it.isNotBlank() },
                    middleName = middle?.takeIf { it.isNotBlank() },
                    namePrefix = prefix?.takeIf { it.isNotBlank() },
                    nameSuffix = suffix?.takeIf { it.isNotBlank() },
                    nickname = nickname?.takeIf { it.isNotBlank() },
                    phones = phones.distinctBy { it.number },
                    emails = emails.distinctBy { it.address },
                    addresses = addresses,
                    birthday = birthday?.takeIf { it.isNotBlank() },
                    anniversary = anniversary?.takeIf { it.isNotBlank() },
                    organization = organization?.takeIf { it.isNotBlank() },
                    organizationUnit = organizationUnit?.takeIf { it.isNotBlank() },
                    jobTitle = jobTitle?.takeIf { it.isNotBlank() },
                    note = note?.takeIf { it.isNotBlank() },
                    links = links.distinctBy { it.value },
                ),
                photo = photo,
                photoMime = photo?.let { sniffImageMime(it) },
            )
        }

    private fun phoneTypeFrom(type: Int?): ContactPhoneType = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE,
        -> ContactPhoneType.CELL

        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> ContactPhoneType.HOME
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> ContactPhoneType.WORK

        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME,
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK,
        ContactsContract.CommonDataKinds.Phone.TYPE_OTHER_FAX,
        -> ContactPhoneType.FAX

        ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> ContactPhoneType.PAGER
        else -> ContactPhoneType.OTHER
    }

    private fun emailTypeFrom(type: Int?): ContactEmailType = when (type) {
        ContactsContract.CommonDataKinds.Email.TYPE_HOME -> ContactEmailType.HOME
        ContactsContract.CommonDataKinds.Email.TYPE_WORK -> ContactEmailType.WORK
        else -> ContactEmailType.OTHER
    }

    private fun addressTypeFrom(type: Int?): ContactAddressType = when (type) {
        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME -> ContactAddressType.HOME
        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK -> ContactAddressType.WORK
        else -> ContactAddressType.OTHER
    }

    private fun android.database.Cursor.getStringByName(column: String): String? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun android.database.Cursor.getIntByName(column: String): Int? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getInt(index) else null
    }

    private fun sniffImageMime(bytes: ByteArray): String =
        if (bytes.size > 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()
        ) {
            "image/png"
        } else {
            "image/jpeg"
        }
}
