package com.erfangholami.solidshare.data.repo.contacts

import com.erfangholami.solidshare.domain.model.ContactRef
import kotlinx.serialization.Serializable

@Serializable
data class ContactMergePayload(
    val survivor: ContactRef,
    val losers: List<ContactRef>,
)
