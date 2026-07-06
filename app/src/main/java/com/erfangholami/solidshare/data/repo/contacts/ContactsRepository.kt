package com.erfangholami.solidshare.data.repo.contacts

import com.erfangholami.solidshare.domain.model.ContactDetail
import com.erfangholami.solidshare.domain.model.ContactDraft
import com.erfangholami.solidshare.domain.model.ContactGroup
import com.erfangholami.solidshare.domain.model.ContactMatchResult
import com.erfangholami.solidshare.domain.model.ContactsOverview

interface ContactsRepository {

    suspend fun getOverview(webId: String): ContactsOverview

    suspend fun ensureDefaultAddressBook(webId: String): String

    suspend fun getContact(webId: String, contactUri: String): ContactDetail

    suspend fun getAllContacts(webId: String, bookUri: String): List<ContactDetail>

    suspend fun createContact(
        webId: String,
        bookUri: String,
        draft: ContactDraft,
        groupUris: List<String> = emptyList(),
    ): ContactDetail

    suspend fun updateContact(
        webId: String,
        bookUri: String,
        contactUri: String,
        draft: ContactDraft,
    ): ContactDetail

    suspend fun deleteContact(webId: String, bookUri: String, contactUri: String)

    suspend fun setContactPhoto(
        webId: String,
        contactUri: String,
        photo: ByteArray,
        contentType: String,
    ): ContactDetail

    suspend fun removeContactPhoto(webId: String, contactUri: String): ContactDetail

    suspend fun getContactPhoto(webId: String, photoUri: String): ByteArray

    suspend fun findContactByWebId(webId: String, targetWebId: String): ContactMatchResult

    suspend fun getGroups(webId: String, bookUri: String): List<ContactGroup>

    suspend fun addContactToGroup(webId: String, contactUri: String, groupUri: String)

    suspend fun removeContactFromGroup(webId: String, contactUri: String, groupUri: String)
}
