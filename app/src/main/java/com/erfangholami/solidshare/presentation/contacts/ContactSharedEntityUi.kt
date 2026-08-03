package com.erfangholami.solidshare.presentation.contacts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.contacts.ContactsRepository
import com.erfangholami.solidshare.data.repo.sharing.SharedEntityTypes
import com.erfangholami.solidshare.presentation.navigation.ContactSharingRoute
import com.erfangholami.solidshare.presentation.navigation.ContactsRoute
import com.erfangholami.solidshare.presentation.navigation.SharedContactRoute
import com.erfangholami.solidshare.presentation.sharing.SharedEntityUi
import javax.inject.Inject

class ContactSharedEntityUi @Inject constructor(
    private val contactsRepository: ContactsRepository,
) : SharedEntityUi {
    override val typeIri: String = SharedEntityTypes.CONTACT
    override val icon: ImageVector = Icons.Filled.Person
    override val kindLabelRes: Int = R.string.entity_kind_contact

    override fun receivedShareRoute(resourceUri: String, ownerWebId: String?): Any =
        SharedContactRoute(resourceUri = resourceUri, ownerWebId = ownerWebId)

    override fun manageShareRoute(resourceUri: String): Any =
        ContactSharingRoute(
            contactUri = if (resourceUri.endsWith("/")) {
                "${resourceUri}index.ttl#this"
            } else {
                resourceUri
            },
        )

    override suspend fun resolveName(webId: String, resourceUri: String): String? =
        runCatching { contactsRepository.getSharedContact(webId, resourceUri).fullName }.getOrNull()
}
