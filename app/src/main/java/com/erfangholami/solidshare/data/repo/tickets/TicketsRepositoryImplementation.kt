package com.erfangholami.solidshare.data.repo.tickets

import android.os.Parcelable
import com.erfangholami.androidsolidservices.api.datamodule.tickets.SolidTicketsDataModule
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.solidshare.data.passimport.GoogleWalletParser
import com.erfangholami.solidshare.data.passimport.PkpassParser
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketFile
import com.erfangholami.solidshare.domain.model.TicketSummaryItem
import javax.inject.Inject

class TicketsRepositoryImplementation @Inject constructor(
    private val ticketsDataModule: SolidTicketsDataModule,
    private val authRepository: AuthRepository,
) : TicketsRepository {

    override suspend fun getTickets(webId: String): List<TicketSummaryItem> =
        ticketsDataModule.tickets.list(webId).unwrap().tickets.map { it.toDomain() }

    override suspend fun getTicket(webId: String, ticketUri: String): Ticket =
        ticketsDataModule.tickets.get(webId, ticketUri).unwrap().toDomain()

    override suspend fun createTicket(
        webId: String,
        draft: TicketDraft,
        artifact: TicketFile?,
    ): Ticket {
        val storage = authRepository.getStorages(webId).firstOrNull()
            ?: throw IllegalStateException("No storage found for $webId")
        return ticketsDataModule.tickets
            .create(
                ownerWebId = webId,
                newTicket = draft.toLib(),
                storage = storage,
                artifact = artifact?.bytes,
                artifactContentType = artifact?.contentType,
            )
            .unwrap()
            .toDomain()
    }

    override suspend fun updateTicket(
        webId: String,
        ticketUri: String,
        draft: TicketDraft,
    ): Ticket =
        ticketsDataModule.tickets.update(webId, ticketUri, draft.toLib()).unwrap().toDomain()

    override suspend fun deleteTicket(webId: String, ticketUri: String) {
        ticketsDataModule.tickets.delete(webId, ticketUri).unwrap()
    }

    override suspend fun getTicketArtifact(webId: String, artifactUri: String): TicketFile {
        val artifact = ticketsDataModule.tickets.getArtifact(webId, artifactUri).unwrap()
        return TicketFile(artifact.contentType, artifact.bytes)
    }

    override fun parseTicketQr(raw: String): TicketDraft? =
        TicketQrCodec.parse(raw) ?: GoogleWalletParser.parse(raw)

    override fun parsePassFile(bytes: ByteArray): Pair<TicketDraft, TicketFile>? =
        PkpassParser.parse(bytes)?.let { draft ->
            draft to TicketFile(PkpassParser.MIME_TYPE, bytes)
        }

    private fun <T : Parcelable> SolidResult<T>.unwrap(): T = getOrThrow()
}
