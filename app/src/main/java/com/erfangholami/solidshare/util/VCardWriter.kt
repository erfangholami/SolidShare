package com.erfangholami.solidshare.util

import android.util.Base64
import com.erfangholami.solidshare.domain.model.ContactAddressType
import com.erfangholami.solidshare.domain.model.ContactDetail
import com.erfangholami.solidshare.domain.model.ContactEmailType
import com.erfangholami.solidshare.domain.model.ContactGender
import com.erfangholami.solidshare.domain.model.ContactImType
import com.erfangholami.solidshare.domain.model.ContactPhoneType

object VCardWriter {

    fun write(contacts: List<Pair<ContactDetail, ByteArray?>>): String = buildString {
        contacts.forEach { (contact, photo) ->
            append("BEGIN:VCARD\r\n")
            append("VERSION:3.0\r\n")
            append("FN:").append(escape(contact.fullName)).append("\r\n")
            val hasNameParts = contact.familyName != null || contact.givenName != null ||
                    contact.middleName != null || contact.namePrefix != null ||
                    contact.nameSuffix != null
            if (hasNameParts) {
                append("N:")
                    .append(escape(contact.familyName.orEmpty())).append(';')
                    .append(escape(contact.givenName.orEmpty())).append(';')
                    .append(escape(contact.middleName.orEmpty())).append(';')
                    .append(escape(contact.namePrefix.orEmpty())).append(';')
                    .append(escape(contact.nameSuffix.orEmpty()))
                    .append("\r\n")
            }
            contact.nickname?.let { append("NICKNAME:").append(escape(it)).append("\r\n") }
            contact.phones.forEach { phone ->
                append("TEL")
                phoneTypeParam(phone.type)?.let { append(";TYPE=").append(it) }
                append(':').append(escape(phone.number)).append("\r\n")
            }
            contact.emails.forEach { email ->
                append("EMAIL")
                emailTypeParam(email.type)?.let { append(";TYPE=").append(it) }
                append(':').append(escape(email.address)).append("\r\n")
            }
            contact.addresses.forEach { address ->
                append("ADR")
                addressTypeParam(address.type)?.let { append(";TYPE=").append(it) }
                append(':')
                    .append(escape(address.poBox.orEmpty())).append(';')
                    .append(';')
                    .append(escape(address.street.orEmpty())).append(';')
                    .append(escape(address.locality.orEmpty())).append(';')
                    .append(escape(address.region.orEmpty())).append(';')
                    .append(escape(address.postalCode.orEmpty())).append(';')
                    .append(escape(address.countryName.orEmpty()))
                    .append("\r\n")
            }
            contact.birthday?.let { append("BDAY:").append(escape(it)).append("\r\n") }
            contact.anniversary?.let { append("ANNIVERSARY:").append(escape(it)).append("\r\n") }
            if (contact.organization != null || contact.organizationUnit != null) {
                append("ORG:")
                    .append(escape(contact.organization.orEmpty()))
                contact.organizationUnit?.let { append(';').append(escape(it)) }
                append("\r\n")
            }
            contact.jobTitle?.let { append("TITLE:").append(escape(it)).append("\r\n") }
            contact.role?.let { append("ROLE:").append(escape(it)).append("\r\n") }
            contact.note?.let { append("NOTE:").append(escape(it)).append("\r\n") }
            contact.impps.forEach { im ->
                append("IMPP")
                imTypeParam(im.type)?.let { append(";TYPE=").append(it) }
                append(':').append(escape(im.handle)).append("\r\n")
            }
            if (contact.categories.isNotEmpty()) {
                append("CATEGORIES:")
                    .append(contact.categories.joinToString(",") { escape(it) })
                    .append("\r\n")
            }
            contact.gender?.let { append("GENDER:").append(genderValue(it)).append("\r\n") }
            contact.geos.forEach { geo ->
                geoV3Value(geo)?.let { append("GEO:").append(it).append("\r\n") }
            }
            contact.languages.forEachIndexed { index, tag ->
                append("LANG;PREF=").append(index + 1).append(':').append(tag).append("\r\n")
            }
            contact.uid?.let { append("UID:").append(escape(it)).append("\r\n") }
            contact.links.forEach { append("URL:").append(escape(it.value)).append("\r\n") }
            if (photo != null) {
                val encoded = Base64.encodeToString(photo, Base64.NO_WRAP)
                append("PHOTO;ENCODING=b;TYPE=JPEG:").append(encoded).append("\r\n")
            }
            append("END:VCARD\r\n")
        }
    }

    private fun phoneTypeParam(type: ContactPhoneType): String? = when (type) {
        ContactPhoneType.CELL -> "CELL"
        ContactPhoneType.HOME -> "HOME"
        ContactPhoneType.WORK -> "WORK"
        ContactPhoneType.FAX -> "FAX"
        ContactPhoneType.PAGER -> "PAGER"
        else -> null
    }

    private fun emailTypeParam(type: ContactEmailType): String? = when (type) {
        ContactEmailType.HOME -> "HOME"
        ContactEmailType.WORK -> "WORK"
        ContactEmailType.OTHER -> null
    }

    private fun addressTypeParam(type: ContactAddressType): String? = when (type) {
        ContactAddressType.HOME -> "HOME"
        ContactAddressType.WORK -> "WORK"
        ContactAddressType.OTHER -> null
    }

    private fun imTypeParam(type: ContactImType): String? = when (type) {
        ContactImType.HOME -> "HOME"
        ContactImType.WORK -> "WORK"
        ContactImType.OTHER -> null
    }

    private fun genderValue(gender: ContactGender): String = when (gender) {
        ContactGender.MALE -> "M"
        ContactGender.FEMALE -> "F"
        ContactGender.OTHER -> "O"
        ContactGender.NONE -> "N"
        ContactGender.UNKNOWN -> "U"
    }

    private fun geoV3Value(geo: String): String? {
        val coordinates = geo.trim().removePrefix("geo:").substringBefore(';')
        val parts = coordinates.split(',')
        if (parts.size < 2) return null
        val latitude = parts[0].toDoubleOrNull() ?: return null
        val longitude = parts[1].toDoubleOrNull() ?: return null
        return "$latitude;$longitude"
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace(",", "\\,")
        .replace(";", "\\;")
}
