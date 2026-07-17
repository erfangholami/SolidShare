package com.erfangholami.solidshare.data.repo.tickets

import android.os.Parcelable
import com.erfangholami.androidsolidservices.api.datamodule.tickets.SolidTicketsDataModule
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.solidshare.data.passimport.PassImages
import com.erfangholami.solidshare.data.passimport.PkpassImages
import com.erfangholami.solidshare.data.passimport.PkpassParser
import com.erfangholami.solidshare.data.passimport.TicketFileSniffer
import com.erfangholami.solidshare.data.passimport.TicketFileType
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketBarcodeFormat
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketEventInfo
import com.erfangholami.solidshare.domain.model.TicketFile
import com.erfangholami.solidshare.domain.model.TicketSource
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
                newTicket = draft.withDerivedEventStart().toLib(),
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
        ticketsDataModule.tickets
            .update(webId, ticketUri, draft.withDerivedEventStart().toLib())
            .unwrap()
            .toDomain()

    override suspend fun deleteTicket(webId: String, ticketUri: String) {
        ticketsDataModule.tickets.delete(webId, ticketUri).unwrap()
    }

    override suspend fun getTicketArtifact(webId: String, artifactUri: String): TicketFile {
        val artifact = ticketsDataModule.tickets.getArtifact(webId, artifactUri).unwrap()
        return TicketFile(artifact.contentType, artifact.bytes)
    }

    override fun parseTicketQr(raw: String): TicketDraft? = TicketQrCodec.parse(raw)

    override suspend fun parseTicketFile(
        bytes: ByteArray,
        fileName: String?,
    ): List<ParsedTicketFile> = when (TicketFileSniffer.detect(bytes)) {
        TicketFileType.PKPASS -> listOfNotNull(parsedPass(bytes))

        TicketFileType.PKPASSES ->
            TicketFileSniffer.allPassesOfBundle(bytes).mapNotNull { parsedPass(it) }

        else -> emptyList()
    }

    private fun parsedPass(passBytes: ByteArray): ParsedTicketFile? =
        PkpassParser.parse(passBytes)?.let { pass ->
            ParsedTicketFile(
                draft = pass.toDraft(),
                artifact = TicketFile(PkpassParser.MIME_TYPE, passBytes),
                visuals = runCatching { PkpassImages.extract(passBytes) }.getOrNull(),
            )
        }

    private fun <T : Parcelable> SolidResult<T>.unwrap(): T = getOrThrow()

    private companion object {
        const val PKPASSES_MIME_TYPE = "application/vnd.apple.pkpasses"
    }
}

internal fun TicketDraft.withDerivedEventStart(): TicketDraft {
    val departure = journey?.from?.time
    if (departure.isNullOrBlank() || !event?.start.isNullOrBlank()) return this
    return copy(event = (event ?: TicketEventInfo()).copy(start = departure))
}
