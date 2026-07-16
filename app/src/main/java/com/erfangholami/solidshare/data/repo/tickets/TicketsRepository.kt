package com.erfangholami.solidshare.data.repo.tickets

import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketFile
import com.erfangholami.solidshare.domain.model.TicketSummaryItem

interface TicketsRepository {

    suspend fun getTickets(webId: String): List<TicketSummaryItem>

    suspend fun getTicket(webId: String, ticketUri: String): Ticket

    suspend fun createTicket(
        webId: String,
        draft: TicketDraft,
        artifact: TicketFile? = null,
    ): Ticket

    suspend fun updateTicket(webId: String, ticketUri: String, draft: TicketDraft): Ticket

    suspend fun deleteTicket(webId: String, ticketUri: String)

    suspend fun getTicketArtifact(webId: String, artifactUri: String): TicketFile

    fun parseTicketQr(raw: String): TicketDraft?

    suspend fun parseTicketFile(bytes: ByteArray, fileName: String? = null): Pair<TicketDraft, TicketFile>?
}
