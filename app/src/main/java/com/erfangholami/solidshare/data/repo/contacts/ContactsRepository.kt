package com.erfangholami.solidshare.data.repo.contacts

import com.erfangholami.solidshare.domain.model.AddressBook
import com.erfangholami.solidshare.data.repo.datamodule.DataModuleLifecycle
import com.erfangholami.solidshare.domain.model.ContactDetail
import com.erfangholami.solidshare.domain.model.ContactDraft
import com.erfangholami.solidshare.domain.model.ContactGroup
import com.erfangholami.solidshare.domain.model.ContactListEntry
import com.erfangholami.solidshare.domain.model.ContactMatchResult
import com.erfangholami.solidshare.domain.model.ContactRef
import com.erfangholami.solidshare.domain.model.ContactsOverview
import com.erfangholami.solidshare.domain.model.MergeSuggestion
import kotlinx.coroutines.flow.Flow

interface ContactsRepository : DataModuleLifecycle {

    fun observeContacts(webId: String): Flow<List<ContactListEntry>>

    suspend fun refreshContacts(webId: String): ContactsOverview

    suspend fun getOverview(webId: String): ContactsOverview

    suspend fun ensureDefaultAddressBook(webId: String): String

    suspend fun getContact(webId: String, contactUri: String): ContactDetail

    fun contactShareTarget(contactUri: String): String

    suspend fun getSharedContact(webId: String, target: String): ContactDetail

    suspend fun getSharedContactPhoto(webId: String, photoUri: String): ByteArray?

    suspend fun addSharedContactToBook(webId: String, shared: ContactDetail): ContactDetail

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

    suspend fun deleteAllContacts(webId: String): Int

    suspend fun queueDelete(webId: String, ref: ContactRef)

    suspend fun queueMerge(webId: String, survivor: ContactRef, losers: List<ContactRef>)

    suspend fun queueDeleteAll(webId: String)



    suspend fun setContactPhoto(
        webId: String,
        contactUri: String,
        photo: ByteArray,
        contentType: String,
    ): ContactDetail

    suspend fun removeContactPhoto(webId: String, contactUri: String): ContactDetail

    suspend fun getContactPhoto(webId: String, photoUri: String): ByteArray

    suspend fun findContactByWebId(webId: String, targetWebId: String): ContactMatchResult

    suspend fun mergeContacts(
        webId: String,
        survivor: ContactRef,
        losers: List<ContactRef>,
    ): ContactDetail

    suspend fun findMergeSuggestions(webId: String): List<MergeSuggestion>

    suspend fun getGroups(webId: String, bookUri: String): List<ContactGroup>

    suspend fun addContactToGroup(webId: String, contactUri: String, groupUri: String)

    suspend fun removeContactFromGroup(webId: String, contactUri: String, groupUri: String)
}
