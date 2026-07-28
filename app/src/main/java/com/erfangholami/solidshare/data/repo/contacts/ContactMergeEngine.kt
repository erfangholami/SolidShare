package com.erfangholami.solidshare.data.repo.contacts

import com.erfangholami.solidshare.domain.model.ContactAddress
import com.erfangholami.solidshare.domain.model.ContactDetail
import com.erfangholami.solidshare.domain.model.ContactDraft
import com.erfangholami.solidshare.domain.model.ContactEmail
import com.erfangholami.solidshare.domain.model.ContactIm
import com.erfangholami.solidshare.domain.model.ContactLinkType
import com.erfangholami.solidshare.domain.model.ContactPhone
import com.erfangholami.solidshare.domain.model.ContactWebLink
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactMergeEngine @Inject constructor() {

    fun normalizeEmail(value: String): String? =
        value.trim().substringAfter("mailto:").lowercase().takeIf { it.isNotBlank() }

    fun normalizeWebId(value: String): String? =
        value.trim().lowercase().trimEnd('/').takeIf { it.isNotBlank() }

    fun normalizePhone(value: String): String? {
        val digits = value.filter { it.isDigit() }
        if (digits.length < 6) return null
        return if (digits.length > 9) digits.takeLast(9) else digits
    }

    fun normalizeName(value: String?): String =
        value?.trim()?.lowercase()?.replace(Regex("\\s+"), " ").orEmpty()

    fun matchKeys(contact: ContactDetail): Set<String> =
        keysFrom(
            webIds = contact.links.filter { it.type == ContactLinkType.WEB_ID }.map { it.value },
            phones = contact.phones.map { it.number },
            emails = contact.emails.map { it.address },
        )

    fun matchKeys(draft: ContactDraft): Set<String> =
        keysFrom(
            webIds = draft.links.filter { it.type == ContactLinkType.WEB_ID }.map { it.value },
            phones = draft.phones.map { it.number },
            emails = draft.emails.map { it.address },
        )

    private fun keysFrom(
        webIds: List<String>,
        phones: List<String>,
        emails: List<String>,
    ): Set<String> {
        val keys = mutableSetOf<String>()
        webIds.forEach { value -> normalizeWebId(value)?.let { keys.add("w:$it") } }
        phones.forEach { value -> normalizePhone(value)?.let { keys.add("p:$it") } }
        emails.forEach { value -> normalizeEmail(value)?.let { keys.add("e:$it") } }
        return keys
    }

    fun cluster(contacts: List<ContactDetail>): List<List<ContactDetail>> {
        if (contacts.isEmpty()) return emptyList()
        val parent = IntArray(contacts.size) { it }

        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var current = x
            while (parent[current] != current) {
                val next = parent[current]
                parent[current] = root
                current = next
            }
            return root
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        val keyOwner = hashMapOf<String, Int>()
        contacts.forEachIndexed { index, contact ->
            matchKeys(contact).forEach { key ->
                val owner = keyOwner[key]
                if (owner == null) keyOwner[key] = index else union(owner, index)
            }
        }

        val groups = linkedMapOf<Int, MutableList<ContactDetail>>()
        contacts.forEachIndexed { index, contact ->
            groups.getOrPut(find(index)) { mutableListOf() }.add(contact)
        }
        return groups.values.filter { it.size >= 2 }.map { it.toList() }
    }

    fun signatureOf(contactUris: Collection<String>): String =
        contactUris.toSortedSet().joinToString("\u001F")

    fun chooseSurvivor(cluster: List<ContactDetail>): ContactDetail =
        cluster.firstOrNull { it.webId != null }
            ?: cluster.maxByOrNull { it.modified ?: Long.MIN_VALUE }
            ?: cluster.first()

    fun mergeDrafts(survivor: ContactDetail, others: List<ContactDetail>): ContactDraft {
        val ordered = listOf(survivor) +
                others.sortedByDescending { it.modified ?: Long.MIN_VALUE }

        fun pick(select: (ContactDetail) -> String?): String? =
            ordered.firstNotNullOfOrNull { select(it)?.takeIf { value -> value.isNotBlank() } }

        return ContactDraft(
            fullName = pick { it.fullName },
            givenName = pick { it.givenName },
            familyName = pick { it.familyName },
            middleName = pick { it.middleName },
            namePrefix = pick { it.namePrefix },
            nameSuffix = pick { it.nameSuffix },
            nickname = pick { it.nickname },
            phones = mergePhones(ordered.flatMap { it.phones }),
            emails = mergeEmails(ordered.flatMap { it.emails }),
            impps = mergeImpps(ordered.flatMap { it.impps }),
            addresses = mergeAddresses(ordered.flatMap { it.addresses }),
            birthday = pick { it.birthday },
            anniversary = pick { it.anniversary },
            organization = pick { it.organization },
            organizationUnit = pick { it.organizationUnit },
            role = pick { it.role },
            jobTitle = pick { it.jobTitle },
            note = pick { it.note },
            categories = mergeStrings(ordered.flatMap { it.categories }),
            gender = ordered.firstNotNullOfOrNull { it.gender },
            geos = mergeStrings(ordered.flatMap { it.geos }),
            languages = mergeStrings(ordered.flatMap { it.languages }),
            links = mergeLinks(ordered.flatMap { it.links }),
            uid = pick { it.uid },
        )
    }

    private fun mergePhones(phones: List<ContactPhone>): List<ContactPhone> {
        val seen = mutableSetOf<String>()
        return phones.filter { seen.add(normalizePhone(it.number) ?: it.number.trim().lowercase()) }
    }

    private fun mergeEmails(emails: List<ContactEmail>): List<ContactEmail> {
        val seen = mutableSetOf<String>()
        return emails.filter { seen.add(normalizeEmail(it.address) ?: it.address.trim().lowercase()) }
    }

    private fun mergeImpps(impps: List<ContactIm>): List<ContactIm> {
        val seen = mutableSetOf<String>()
        return impps.filter { seen.add(it.handle.trim().lowercase()) }
    }

    private fun mergeStrings(values: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return values.map { it.trim() }.filter { it.isNotBlank() && seen.add(it.lowercase()) }
    }

    private fun mergeAddresses(addresses: List<ContactAddress>): List<ContactAddress> {
        val seen = mutableSetOf<String>()
        return addresses.filter { address ->
            val key = listOf(
                address.street, address.locality, address.region,
                address.postalCode, address.countryName, address.poBox,
            ).joinToString("|") { it.orEmpty().trim().lowercase() }
            seen.add(key)
        }
    }

    private fun mergeLinks(links: List<ContactWebLink>): List<ContactWebLink> {
        val seen = mutableSetOf<String>()
        return links.filter { seen.add("${it.type}:${it.value.trim().lowercase()}") }
    }
}
