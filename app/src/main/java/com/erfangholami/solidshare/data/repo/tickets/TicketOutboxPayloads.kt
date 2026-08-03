package com.erfangholami.solidshare.data.repo.tickets

import com.erfangholami.solidshare.domain.model.TicketDraft
import kotlinx.serialization.Serializable

enum class TicketOpType {
    CREATE,
    UPDATE,
    DELETE,
}

@Serializable
data class TicketCreatePayload(
    val provisionalUri: String,
    val draft: TicketDraft,
    val hasArtifact: Boolean = false,
    val artifactContentType: String? = null,
    val hasImages: Boolean = false,
)

@Serializable
data class TicketUpdatePayload(
    val ticketUri: String,
    val draft: TicketDraft,
)

@Serializable
data class TicketDeletePayload(
    val ticketUri: String,
)
