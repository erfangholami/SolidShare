package com.erfangholami.solidshare.data.repo.contacts

import com.erfangholami.solidshare.domain.model.ContactAddress
import com.erfangholami.solidshare.domain.model.ContactAddressType
import com.erfangholami.solidshare.domain.model.ContactDetail
import com.erfangholami.solidshare.domain.model.ContactDraft
import com.erfangholami.solidshare.domain.model.ContactEmail
import com.erfangholami.solidshare.domain.model.ContactEmailType
import com.erfangholami.solidshare.domain.model.ContactLinkType
import com.erfangholami.solidshare.domain.model.ContactPhone
import com.erfangholami.solidshare.domain.model.ContactPhoneType
import com.erfangholami.solidshare.domain.model.ContactWebLink
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressEntry as LibAddressEntry
import com.erfangholami.androidsolidservices.shared.model.contacts.AddressType as LibAddressType
import com.erfangholami.androidsolidservices.shared.model.contacts.ContactData as LibContactData
import com.erfangholami.androidsolidservices.shared.model.contacts.EmailEntry as LibEmailEntry
import com.erfangholami.androidsolidservices.shared.model.contacts.EmailType as LibEmailType
import com.erfangholami.androidsolidservices.shared.model.contacts.Name as LibName
import com.erfangholami.androidsolidservices.shared.model.contacts.PhoneEntry as LibPhoneEntry
import com.erfangholami.androidsolidservices.shared.model.contacts.PhoneType as LibPhoneType
import com.erfangholami.androidsolidservices.shared.model.contacts.SolidContact as LibSolidContact
import com.erfangholami.androidsolidservices.shared.model.contacts.URLType as LibUrlType
import com.erfangholami.androidsolidservices.shared.model.contacts.UrlEntry as LibUrlEntry

fun LibSolidContact.toDomain(): ContactDetail = ContactDetail(
    uri = uri,
    fullName = fullName,
    givenName = data.name?.givenName,
    familyName = data.name?.familyName,
    middleName = data.name?.additionalName,
    namePrefix = data.name?.honorificPrefix,
    nameSuffix = data.name?.honorificSuffix,
    nickname = data.nickname,
    phones = data.phones.map { ContactPhone(it.number, ContactPhoneType.valueOf(it.type.name)) },
    emails = data.emails.map { ContactEmail(it.address, ContactEmailType.valueOf(it.type.name)) },
    addresses = data.addresses.map {
        ContactAddress(
            street = it.street,
            locality = it.locality,
            region = it.region,
            postalCode = it.postalCode,
            countryName = it.countryName,
            poBox = it.poBox,
            type = ContactAddressType.valueOf(it.type.name),
        )
    },
    birthday = data.birthday,
    anniversary = data.anniversary,
    organization = data.organizationName,
    organizationUnit = data.organizationUnit,
    role = data.role,
    jobTitle = data.title,
    note = data.note,
    links = data.urls.map { ContactWebLink(it.type.toDomain(), it.value) },
    photoUri = photoUri,
    modified = modified,
)

fun ContactDraft.toLib(): LibContactData {
    val nameParts = LibName(
        familyName = familyName?.takeIf { it.isNotBlank() },
        givenName = givenName?.takeIf { it.isNotBlank() },
        additionalName = middleName?.takeIf { it.isNotBlank() },
        honorificPrefix = namePrefix?.takeIf { it.isNotBlank() },
        honorificSuffix = nameSuffix?.takeIf { it.isNotBlank() },
    )
    val hasNameParts = listOfNotNull(
        nameParts.familyName,
        nameParts.givenName,
        nameParts.additionalName,
        nameParts.honorificPrefix,
        nameParts.honorificSuffix,
    ).isNotEmpty()
    return LibContactData(
        fullName = fullName?.takeIf { it.isNotBlank() },
        name = nameParts.takeIf { hasNameParts },
        nickname = nickname?.takeIf { it.isNotBlank() },
        phones = phones
            .filter { it.number.isNotBlank() }
            .distinctBy { it.number }
            .map { LibPhoneEntry(it.number.trim(), LibPhoneType.valueOf(it.type.name)) },
        emails = emails
            .filter { it.address.isNotBlank() }
            .distinctBy { it.address }
            .map { LibEmailEntry(it.address.trim(), LibEmailType.valueOf(it.type.name)) },
        addresses = addresses.map {
            LibAddressEntry(
                street = it.street?.takeIf { s -> s.isNotBlank() },
                locality = it.locality?.takeIf { s -> s.isNotBlank() },
                region = it.region?.takeIf { s -> s.isNotBlank() },
                postalCode = it.postalCode?.takeIf { s -> s.isNotBlank() },
                countryName = it.countryName?.takeIf { s -> s.isNotBlank() },
                poBox = it.poBox?.takeIf { s -> s.isNotBlank() },
                type = LibAddressType.valueOf(it.type.name),
            )
        }.filter { !it.isEmpty() },
        birthday = birthday?.takeIf { it.isNotBlank() },
        anniversary = anniversary?.takeIf { it.isNotBlank() },
        organizationName = organization?.takeIf { it.isNotBlank() },
        organizationUnit = organizationUnit?.takeIf { it.isNotBlank() },
        role = role?.takeIf { it.isNotBlank() },
        title = jobTitle?.takeIf { it.isNotBlank() },
        note = note?.takeIf { it.isNotBlank() },
        urls = links
            .filter { it.value.isNotBlank() }
            .map { LibUrlEntry(it.type.toLib(), it.value.trim()) },
    )
}

fun LibUrlType.toDomain(): ContactLinkType = when (this) {
    LibUrlType.Home -> ContactLinkType.HOME
    LibUrlType.Work -> ContactLinkType.WORK
    LibUrlType.Homepage -> ContactLinkType.HOMEPAGE
    LibUrlType.WebId -> ContactLinkType.WEB_ID
    LibUrlType.PublicId -> ContactLinkType.PUBLIC_ID
}

fun ContactLinkType.toLib(): LibUrlType = when (this) {
    ContactLinkType.HOME -> LibUrlType.Home
    ContactLinkType.WORK -> LibUrlType.Work
    ContactLinkType.HOMEPAGE -> LibUrlType.Homepage
    ContactLinkType.WEB_ID -> LibUrlType.WebId
    ContactLinkType.PUBLIC_ID -> LibUrlType.PublicId
}
