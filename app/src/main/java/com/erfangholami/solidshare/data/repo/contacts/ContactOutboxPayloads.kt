package com.erfangholami.solidshare.data.repo.contacts

import com.erfangholami.solidshare.domain.model.ContactRef
import kotlinx.serialization.Serializable

enum class ContactOpType {
    DELETE,
    MERGE,
    DELETE_ALL,
}

@Serializable
data class ContactMergePayload(
    val survivor: ContactRef,
    val losers: List<ContactRef>,
)
