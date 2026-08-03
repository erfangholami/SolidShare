package com.erfangholami.solidshare.data.repo.tickets

import com.erfangholami.solidshare.data.repo.datamodule.DataModuleLifecycle
import com.erfangholami.solidshare.data.passimport.PassImages
import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketFile
import com.erfangholami.solidshare.domain.model.TicketImageUris
import com.erfangholami.solidshare.domain.model.TicketSummaryItem
import kotlinx.coroutines.flow.Flow

interface TicketsRepository : DataModuleLifecycle {

    fun observeTickets(webId: String): Flow<List<TicketSummaryItem>>

    suspend fun refreshTickets(webId: String)

    suspend fun getTicket(webId: String, ticketUri: String): Ticket

    fun ticketShareTarget(ticketUri: String): String

    suspend fun getSharedTicket(webId: String, target: String): Ticket

    suspend fun getSharedTicketImages(webId: String, ticket: Ticket): PassImages?

    suspend fun getSharedTicketArtifact(webId: String, artifactUri: String): TicketFile

    suspend fun addSharedTicketToWallet(webId: String, shared: Ticket): String

    suspend fun findTicketCopiedFrom(webId: String, originalUri: String): String?

    suspend fun queueCreate(
        webId: String,
        draft: TicketDraft,
        artifact: TicketFile? = null,
        images: PassImages? = null,
    ): String

    suspend fun queueUpdate(webId: String, ticketUri: String, draft: TicketDraft)

    suspend fun queueDelete(webId: String, ticketUri: String)


    suspend fun refreshIssuerPasses(webId: String)


    suspend fun createTicket(
        webId: String,
        draft: TicketDraft,
        artifact: TicketFile? = null,
        images: PassImages? = null,
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
