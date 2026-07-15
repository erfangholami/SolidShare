package com.erfangholami.solidshare.data.repo.tickets

import android.os.Parcelable
import com.erfangholami.androidsolidservices.api.datamodule.tickets.SolidTicketsDataModule
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.solidshare.data.passimport.GoogleWalletParser
import com.erfangholami.solidshare.data.passimport.PkpassParser
import com.erfangholami.solidshare.data.passimport.TicketFileSniffer
import com.erfangholami.solidshare.data.passimport.TicketFileType
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketCategory
import com.erfangholami.solidshare.domain.model.TicketDraft
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

    override fun parseTicketFile(
        bytes: ByteArray,
        fileName: String?,
    ): Pair<TicketDraft, TicketFile>? = when (TicketFileSniffer.detect(bytes)) {
        TicketFileType.PKPASS ->
            PkpassParser.parse(bytes)?.let { it to TicketFile(PkpassParser.MIME_TYPE, bytes) }

        TicketFileType.PKPASSES ->
            TicketFileSniffer.firstPassOfBundle(bytes)
                ?.let { PkpassParser.parse(it) }
                ?.let { it to TicketFile(PKPASSES_MIME_TYPE, bytes) }

        TicketFileType.PDF ->
            fileDraft(fileName, TicketSource.PDF) to TicketFile("application/pdf", bytes)

        TicketFileType.IMAGE ->
            fileDraft(fileName, TicketSource.IMAGE) to
                TicketFile(TicketFileSniffer.imageMimeType(bytes), bytes)

        TicketFileType.UNKNOWN -> null
    }

    /**
     * A best-effort draft for a file we can capture but not yet read (a PDF or image): the original
     * is kept as the artifact and the user completes the details in the edit form. Rich extraction
     * (barcode + OCR) fills these in a later pass.
     */
    private fun fileDraft(fileName: String?, source: TicketSource): TicketDraft = TicketDraft(
        title = fileName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotBlank() }.orEmpty(),
        category = TicketCategory.GENERIC,
        source = source,
    )

    private fun <T : Parcelable> SolidResult<T>.unwrap(): T = getOrThrow()

    private companion object {
        const val PKPASSES_MIME_TYPE = "application/vnd.apple.pkpasses"
    }
}
