package com.erfangholami.solidshare.util

import android.util.Base64
import com.erfangholami.solidshare.domain.model.ContactAddress
import com.erfangholami.solidshare.domain.model.ContactAddressType
import com.erfangholami.solidshare.domain.model.ContactDraft
import com.erfangholami.solidshare.domain.model.ContactEmail
import com.erfangholami.solidshare.domain.model.ContactEmailType
import com.erfangholami.solidshare.domain.model.ContactGender
import com.erfangholami.solidshare.domain.model.ContactIm
import com.erfangholami.solidshare.domain.model.ContactImType
import com.erfangholami.solidshare.domain.model.ContactLinkType
import com.erfangholami.solidshare.domain.model.ContactPhone
import com.erfangholami.solidshare.domain.model.ContactPhoneType
import com.erfangholami.solidshare.domain.model.ContactWebLink

data class ParsedVCard(
    val draft: ContactDraft,
    val photo: ByteArray? = null,
    val photoMime: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParsedVCard) return false
        return draft == other.draft &&
                photoMime == other.photoMime &&
                (photo?.contentEquals(other.photo ?: return false) ?: (other.photo == null))
    }

    override fun hashCode(): Int {
        var result = draft.hashCode()
        result = 31 * result + (photo?.contentHashCode() ?: 0)
        result = 31 * result + (photoMime?.hashCode() ?: 0)
        return result
    }
}

private data class VCardProperty(
    val name: String,
    val params: List<String>,
    val value: String,
)

object VCardReader {

    fun parse(text: String): List<ParsedVCard> {
        val unfolded = unfold(text)
        val cards = mutableListOf<ParsedVCard>()
        var current: MutableList<VCardProperty>? = null
        unfolded.forEach { line ->
            val property = parseProperty(line) ?: return@forEach
            when (property.name) {
                "BEGIN" -> if (property.value.equals("VCARD", true)) {
                    current = mutableListOf()
                }

                "END" -> if (property.value.equals("VCARD", true)) {
                    current?.let { properties ->
                        buildCard(properties)?.let { cards.add(it) }
                    }
                    current = null
                }

                else -> current?.add(property)
            }
        }
        return cards
    }

    private fun unfold(text: String): List<String> {
        val rawLines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val result = mutableListOf<String>()
        rawLines.forEach { line ->
            when {
                line.isEmpty() -> Unit

                (line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty() ->
                    result[result.lastIndex] = result.last() + line.drop(1)

                result.isNotEmpty() && result.last().isQuotedPrintable() &&
                        result.last().endsWith("=") ->
                    result[result.lastIndex] = result.last().dropLast(1) + line

                else -> result.add(line)
            }
        }
        return result
    }

    private fun String.isQuotedPrintable(): Boolean =
        substringBefore(':').uppercase().contains("QUOTED-PRINTABLE")

    private fun parseProperty(line: String): VCardProperty? {
        val colonIndex = line.indexOf(':')
        if (colonIndex <= 0) return null
        val nameAndParams = line.substring(0, colonIndex).split(';')
        val name = nameAndParams.first().substringAfter('.').trim().uppercase()
        if (name.isEmpty()) return null
        val params = nameAndParams.drop(1).map { it.trim().uppercase() }
        var value = line.substring(colonIndex + 1)
        if (params.any { it.contains("QUOTED-PRINTABLE") }) {
            value = decodeQuotedPrintable(value)
        }
        return VCardProperty(name, params, value)
    }

    private fun decodeQuotedPrintable(value: String): String {
        val bytes = mutableListOf<Byte>()
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '=' && index + 3 <= value.length) {
                val hex = value.substring(index + 1, index + 3)
                val decoded = hex.toIntOrNull(16)
                if (decoded != null) {
                    bytes.add(decoded.toByte())
                    index += 3
                    continue
                }
            }
            bytes.addAll(char.toString().toByteArray(Charsets.UTF_8).toList())
            index++
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private fun unescape(value: String): String = value
        .replace("\\n", "\n")
        .replace("\\N", "\n")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")
        .trim()

    private fun splitStructured(value: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        value.forEach { char ->
            when {
                escaped -> {
                    current.append('\\').append(char)
                    escaped = false
                }

                char == '\\' -> escaped = true
                char == ';' -> {
                    parts.add(current.toString())
                    current.clear()
                }

                else -> current.append(char)
            }
        }
        parts.add(current.toString())
        return parts.map { unescape(it) }
    }

    private fun buildCard(properties: List<VCardProperty>): ParsedVCard? {
        var fullName: String? = null
        var given: String? = null
        var family: String? = null
        var middle: String? = null
        var prefix: String? = null
        var suffix: String? = null
        var nickname: String? = null
        val phones = mutableListOf<ContactPhone>()
        val emails = mutableListOf<ContactEmail>()
        val addresses = mutableListOf<ContactAddress>()
        var birthday: String? = null
        var anniversary: String? = null
        var organization: String? = null
        var organizationUnit: String? = null
        var role: String? = null
        var jobTitle: String? = null
        var note: String? = null
        val links = mutableListOf<ContactWebLink>()
        val impps = mutableListOf<ContactIm>()
        val categories = mutableListOf<String>()
        var gender: ContactGender? = null
        val geos = mutableListOf<String>()
        val languages = mutableListOf<Pair<Int, String>>()
        var uid: String? = null
        var photo: ByteArray? = null
        var photoMime: String? = null

        properties.forEach { property ->
            when (property.name) {
                "FN" -> fullName = unescape(property.value).takeIf { it.isNotBlank() }

                "N" -> {
                    val parts = splitStructured(property.value)
                    family = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
                    given = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                    middle = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
                    prefix = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
                    suffix = parts.getOrNull(4)?.takeIf { it.isNotBlank() }
                }

                "NICKNAME" -> nickname = unescape(property.value).takeIf { it.isNotBlank() }

                "TEL" -> {
                    val number = unescape(property.value)
                    if (number.isNotBlank()) {
                        phones.add(ContactPhone(number, phoneTypeFrom(property.params)))
                    }
                }

                "EMAIL" -> {
                    val address = unescape(property.value)
                    if (address.isNotBlank()) {
                        emails.add(ContactEmail(address, emailTypeFrom(property.params)))
                    }
                }

                "ADR" -> {
                    val parts = splitStructured(property.value)
                    val address = ContactAddress(
                        poBox = parts.getOrNull(0)?.takeIf { it.isNotBlank() },
                        street = listOfNotNull(
                            parts.getOrNull(1)?.takeIf { it.isNotBlank() },
                            parts.getOrNull(2)?.takeIf { it.isNotBlank() },
                        ).joinToString(", ").takeIf { it.isNotBlank() },
                        locality = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
                        region = parts.getOrNull(4)?.takeIf { it.isNotBlank() },
                        postalCode = parts.getOrNull(5)?.takeIf { it.isNotBlank() },
                        countryName = parts.getOrNull(6)?.takeIf { it.isNotBlank() },
                        type = addressTypeFrom(property.params),
                    )
                    val isEmpty = listOf(
                        address.street, address.locality, address.region,
                        address.postalCode, address.countryName, address.poBox,
                    ).all { it.isNullOrBlank() }
                    if (!isEmpty) addresses.add(address)
                }

                "BDAY" -> birthday = unescape(property.value).takeIf { it.isNotBlank() }
                "ANNIVERSARY" -> anniversary = unescape(property.value).takeIf { it.isNotBlank() }

                "ORG" -> {
                    val parts = splitStructured(property.value)
                    organization = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
                    organizationUnit = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                }

                "TITLE" -> jobTitle = unescape(property.value).takeIf { it.isNotBlank() }
                "ROLE" -> role = unescape(property.value).takeIf { it.isNotBlank() }
                "NOTE" -> note = unescape(property.value).takeIf { it.isNotBlank() }

                "URL" -> {
                    val url = unescape(property.value)
                    if (url.isNotBlank()) {
                        links.add(ContactWebLink(ContactLinkType.HOMEPAGE, url))
                    }
                }

                "IMPP" -> {
                    val handle = unescape(property.value)
                    if (handle.isNotBlank()) {
                        impps.add(ContactIm(handle, imTypeFrom(property.params)))
                    }
                }

                "CATEGORIES" -> categories.addAll(
                    splitOnComma(property.value).filter { it.isNotBlank() },
                )

                "GENDER" -> gender = when (property.value.substringBefore(';').trim().uppercase()) {
                    "M" -> ContactGender.MALE
                    "F" -> ContactGender.FEMALE
                    "O" -> ContactGender.OTHER
                    "N" -> ContactGender.NONE
                    "U" -> ContactGender.UNKNOWN
                    else -> gender
                }

                "GEO" -> {
                    val value = normalizeGeo(property.value)
                    if (value != null) geos.add(value)
                }

                "LANG" -> {
                    val tag = property.value.trim()
                    if (tag.isNotBlank()) {
                        val pref = property.params
                            .firstOrNull { it.startsWith("PREF=") }
                            ?.removePrefix("PREF=")?.toIntOrNull() ?: 100
                        languages.add(pref to tag)
                    }
                }

                "UID" -> uid = unescape(property.value).takeIf { it.isNotBlank() }

                "PHOTO" -> {
                    decodePhoto(property)?.let { (bytes, mime) ->
                        photo = bytes
                        photoMime = mime
                    }
                }
            }
        }

        val hasIdentity = fullName != null || given != null || family != null ||
                phones.isNotEmpty() || emails.isNotEmpty()
        if (!hasIdentity) return null

        return ParsedVCard(
            draft = ContactDraft(
                fullName = fullName,
                givenName = given,
                familyName = family,
                middleName = middle,
                namePrefix = prefix,
                nameSuffix = suffix,
                nickname = nickname,
                phones = phones.distinctBy { it.number },
                emails = emails.distinctBy { it.address },
                addresses = addresses,
                birthday = birthday,
                anniversary = anniversary,
                organization = organization,
                organizationUnit = organizationUnit,
                role = role,
                jobTitle = jobTitle,
                note = note,
                categories = categories.distinct(),
                gender = gender,
                geos = geos.distinct(),
                languages = languages.sortedBy { it.first }.map { it.second }
                    .distinctBy { it.lowercase() },
                links = links.distinctBy { it.value },
                impps = impps.distinctBy { it.handle },
                uid = uid,
            ),
            photo = photo,
            photoMime = photoMime,
        )
    }

    private fun splitOnComma(value: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        value.forEach { char ->
            when {
                escaped -> {
                    current.append('\\').append(char)
                    escaped = false
                }

                char == '\\' -> escaped = true
                char == ',' -> {
                    parts.add(current.toString())
                    current.clear()
                }

                else -> current.append(char)
            }
        }
        parts.add(current.toString())
        return parts.map { unescape(it) }
    }

    private fun normalizeGeo(raw: String): String? {
        val value = raw.trim().removeSurrounding("\"")
        if (value.isBlank()) return null
        if (value.contains(':')) return value
        val coordinates = value.replace(';', ',').replace(" ", "")
        return "geo:$coordinates"
    }

    private fun decodePhoto(property: VCardProperty): Pair<ByteArray, String>? {
        val value = property.value.trim()
        val isBase64 = property.params.any {
            it == "ENCODING=B" || it == "ENCODING=BASE64" || it == "BASE64"
        }
        return when {
            value.startsWith("data:", ignoreCase = true) -> {
                val mime = value.substringAfter("data:").substringBefore(';')
                    .ifBlank { "image/jpeg" }
                val payload = value.substringAfter("base64,", "")
                if (payload.isBlank()) return null
                runCatching {
                    Base64.decode(payload, Base64.DEFAULT) to mime
                }.getOrNull()
            }

            isBase64 -> {
                val mime = property.params
                    .firstOrNull { it.startsWith("TYPE=") }
                    ?.removePrefix("TYPE=")
                    ?.let { if (it.contains('/')) it.lowercase() else "image/${it.lowercase()}" }
                    ?: "image/jpeg"
                runCatching {
                    Base64.decode(value, Base64.DEFAULT) to mime
                }.getOrNull()
            }

            else -> null
        }
    }

    private fun phoneTypeFrom(params: List<String>): ContactPhoneType {
        val types = typeValues(params)
        return when {
            "CELL" in types || "MOBILE" in types -> ContactPhoneType.CELL
            "FAX" in types -> ContactPhoneType.FAX
            "PAGER" in types -> ContactPhoneType.PAGER
            "WORK" in types -> ContactPhoneType.WORK
            "HOME" in types -> ContactPhoneType.HOME
            else -> ContactPhoneType.OTHER
        }
    }

    private fun emailTypeFrom(params: List<String>): ContactEmailType {
        val types = typeValues(params)
        return when {
            "WORK" in types -> ContactEmailType.WORK
            "HOME" in types -> ContactEmailType.HOME
            else -> ContactEmailType.OTHER
        }
    }

    private fun addressTypeFrom(params: List<String>): ContactAddressType {
        val types = typeValues(params)
        return when {
            "WORK" in types -> ContactAddressType.WORK
            "HOME" in types -> ContactAddressType.HOME
            else -> ContactAddressType.OTHER
        }
    }

    private fun imTypeFrom(params: List<String>): ContactImType {
        val types = typeValues(params)
        return when {
            "WORK" in types -> ContactImType.WORK
            "HOME" in types -> ContactImType.HOME
            else -> ContactImType.OTHER
        }
    }

    private fun typeValues(params: List<String>): Set<String> =
        params.flatMap { param ->
            if (param.startsWith("TYPE=")) {
                param.removePrefix("TYPE=").split(',')
            } else {
                listOf(param)
            }
        }.map { it.trim().removeSurrounding("\"") }.toSet()
}
