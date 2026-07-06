package com.erfangholami.solidshare.domain.model

data class AddressBook(
    val uri: String,
    val title: String,
    val isPrivate: Boolean,
    val contactCount: Int,
)

data class ContactGroup(
    val uri: String,
    val name: String,
    val memberUris: List<String> = emptyList(),
)

data class ContactListEntry(
    val uri: String,
    val bookUri: String,
    val name: String,
)

data class ContactsOverview(
    val books: List<AddressBook>,
    val entries: List<ContactListEntry>,
)

enum class ContactLinkType { HOME, WORK, HOMEPAGE, WEB_ID, PUBLIC_ID }

enum class ContactPhoneType { CELL, HOME, WORK, FAX, PAGER, VOICE, TEXT, VIDEO, TEXT_PHONE, OTHER }

enum class ContactEmailType { HOME, WORK, OTHER }

enum class ContactAddressType { HOME, WORK, OTHER }

data class ContactWebLink(
    val type: ContactLinkType,
    val value: String,
)

data class ContactPhone(
    val number: String,
    val type: ContactPhoneType = ContactPhoneType.OTHER,
)

data class ContactEmail(
    val address: String,
    val type: ContactEmailType = ContactEmailType.OTHER,
)

data class ContactAddress(
    val street: String? = null,
    val locality: String? = null,
    val region: String? = null,
    val postalCode: String? = null,
    val countryName: String? = null,
    val poBox: String? = null,
    val type: ContactAddressType = ContactAddressType.OTHER,
)

data class ContactDetail(
    val uri: String,
    val fullName: String,
    val givenName: String? = null,
    val familyName: String? = null,
    val middleName: String? = null,
    val namePrefix: String? = null,
    val nameSuffix: String? = null,
    val nickname: String? = null,
    val phones: List<ContactPhone> = emptyList(),
    val emails: List<ContactEmail> = emptyList(),
    val addresses: List<ContactAddress> = emptyList(),
    val birthday: String? = null,
    val anniversary: String? = null,
    val organization: String? = null,
    val organizationUnit: String? = null,
    val role: String? = null,
    val jobTitle: String? = null,
    val note: String? = null,
    val links: List<ContactWebLink> = emptyList(),
    val photoUri: String? = null,
    val modified: Long? = null,
) {
    val webId: String? get() = links.firstOrNull { it.type == ContactLinkType.WEB_ID }?.value
}

data class ContactDraft(
    val fullName: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val middleName: String? = null,
    val namePrefix: String? = null,
    val nameSuffix: String? = null,
    val nickname: String? = null,
    val phones: List<ContactPhone> = emptyList(),
    val emails: List<ContactEmail> = emptyList(),
    val addresses: List<ContactAddress> = emptyList(),
    val birthday: String? = null,
    val anniversary: String? = null,
    val organization: String? = null,
    val organizationUnit: String? = null,
    val role: String? = null,
    val jobTitle: String? = null,
    val note: String? = null,
    val links: List<ContactWebLink> = emptyList(),
)

data class ContactMatchResult(
    val contact: ContactDetail? = null,
    val bookUri: String? = null,
) {
    val exists: Boolean get() = contact != null
}
