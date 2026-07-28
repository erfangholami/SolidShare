package com.erfangholami.solidshare.data.repo.tickets

import com.erfangholami.solidshare.data.passimport.PassImages
import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketFile
import com.erfangholami.solidshare.domain.model.TicketImageUris
import com.erfangholami.solidshare.domain.model.TicketSummaryItem
import kotlinx.coroutines.flow.Flow

interface TicketsRepository {

    fun observeTickets(webId: String): Flow<List<TicketSummaryItem>>

    suspend fun refreshTickets(webId: String)

    suspend fun getTicket(webId: String, ticketUri: String): Ticket

    suspend fun queueCreate(webId: String, draft: TicketDraft, artifact: TicketFile? = null): String

    suspend fun queueUpdate(webId: String, ticketUri: String, draft: TicketDraft)

    suspend fun queueDelete(webId: String, ticketUri: String)

    suspend fun drainTicketOutbox(webId: String): Boolean

    suspend fun clearCacheForWebId(webId: String)

    suspend fun createTicket(
        webId: String,
        draft: TicketDraft,
        artifact: TicketFile? = null,
    ): Ticket

    suspend fun updateTicket(webId: String, ticketUri: String, draft: TicketDraft): Ticket

    suspend fun deleteTicket(webId: String, ticketUri: String)

    suspend fun getTicketArtifact(webId: String, ticketUri: String, artifactUri: String): TicketFile

    suspend fun getTicketImages(
        webId: String,
        ticketUri: String,
        images: TicketImageUris?,
    ): PassImages?

    fun parseTicketQr(raw: String): TicketDraft?

    suspend fun parseTicketFile(bytes: ByteArray, fileName: String? = null): List<ParsedTicketFile>
}

data class ParsedTicketFile(
    val draft: TicketDraft,
    val artifact: TicketFile,
    val visuals: PassImages? = null,
)
