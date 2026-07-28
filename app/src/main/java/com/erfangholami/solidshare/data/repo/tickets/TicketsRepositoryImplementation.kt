package com.erfangholami.solidshare.data.repo.tickets

import android.os.Parcelable
import androidx.work.WorkManager
import com.erfangholami.androidsolidservices.api.datamodule.tickets.SolidTicketsDataModule
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicketImages
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.solidshare.data.local.cache.OpStatus
import com.erfangholami.solidshare.data.local.cache.SyncState
import com.erfangholami.solidshare.data.local.cache.TicketBlobStore
import com.erfangholami.solidshare.data.local.cache.TicketDao
import com.erfangholami.solidshare.data.local.cache.TicketOutboxDao
import com.erfangholami.solidshare.data.local.cache.TicketOutboxOpEntity
import com.erfangholami.solidshare.data.local.cache.TicketOpType
import com.erfangholami.solidshare.data.local.cache.toCacheEntity
import com.erfangholami.solidshare.data.local.cache.toDomain
import com.erfangholami.solidshare.data.local.cache.toSummary
import com.erfangholami.solidshare.data.passimport.PassImages
import com.erfangholami.solidshare.data.passimport.PkpassImages
import com.erfangholami.solidshare.data.passimport.PkpassParser
import com.erfangholami.solidshare.data.passimport.TicketFileSniffer
import com.erfangholami.solidshare.data.passimport.TicketFileType
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketEventInfo
import com.erfangholami.solidshare.domain.model.TicketFile
import com.erfangholami.solidshare.domain.model.TicketImageUris
import com.erfangholami.solidshare.domain.model.TicketSummaryItem
import com.erfangholami.solidshare.worker.TicketOutboxWorker
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class TicketsRepositoryImplementation @Inject constructor(
    private val ticketsDataModule: SolidTicketsDataModule,
    private val authRepository: AuthRepository,
    private val ticketDao: TicketDao,
    private val ticketOutboxDao: TicketOutboxDao,
    private val blobStore: TicketBlobStore,
    private val workManager: WorkManager,
) : TicketsRepository {

    private val payloadJson = Json { ignoreUnknownKeys = true }

    override fun observeTickets(webId: String): Flow<List<TicketSummaryItem>> =
        ticketDao.observeTickets(webId).map { rows -> rows.map { it.toSummary() } }

    override suspend fun refreshTickets(webId: String) {
        val summaries = ticketsDataModule.tickets.list(webId).unwrap().tickets
        val fetched = coroutineScope {
            summaries.map { summary ->
                async { runCatching { fetchRemoteTicket(webId, summary.uri) } }
            }.awaitAll()
        }
        val tickets = fetched.mapNotNull { it.getOrNull() }
        val now = System.currentTimeMillis()
        val entities = tickets.map { it.toCacheEntity(webId, now) }
        val failures = fetched.mapNotNull { it.exceptionOrNull() }
        if (tickets.isEmpty() && failures.isNotEmpty()) throw failures.first()
        if (failures.isEmpty()) {
            runCatching { ticketDao.replaceSynced(webId, entities) }
        } else {
            runCatching { ticketDao.upsertAll(entities) }
        }
        tickets.forEach { runCatching { cacheTicketBinaries(webId, it) } }
    }

    override suspend fun getTicket(webId: String, ticketUri: String): Ticket =
        ticketDao.findByUri(webId, ticketUri)?.toDomain()
            ?: fetchRemoteTicket(webId, ticketUri).also { ticket ->
                runCatching {
                    ticketDao.upsert(ticket.toCacheEntity(webId, System.currentTimeMillis()))
                }
            }

    override suspend fun queueCreate(
        webId: String,
        draft: TicketDraft,
        artifact: TicketFile?,
    ): String {
        val provisionalUri = PROVISIONAL_PREFIX + UUID.randomUUID()
        val normalized = draft.withDerivedEventStart()
        runCatching {
            if (artifact != null) {
                blobStore.write(webId, provisionalUri, TicketBlobStore.ARTIFACT, artifact.bytes)
                blobStore.writeText(
                    webId,
                    provisionalUri,
                    TicketBlobStore.ARTIFACT_MIME,
                    artifact.contentType,
                )
                if (artifact.contentType == PkpassParser.MIME_TYPE) {
                    PkpassImages.extract(artifact.bytes)?.let {
                        writeVisuals(webId, provisionalUri, it)
                    }
                }
            }
        }
        runCatching {
            ticketDao.upsert(
                normalized.toProvisionalTicket(provisionalUri).toCacheEntity(
                    webId,
                    System.currentTimeMillis(),
                    syncState = SyncState.PENDING_CREATE,
                ),
            )
        }
        enqueueTicketOp(
            webId,
            TicketOpType.CREATE,
            payloadJson.encodeToString(
                TicketCreatePayload.serializer(),
                TicketCreatePayload(
                    provisionalUri = provisionalUri,
                    draft = normalized,
                    hasArtifact = artifact != null,
                    artifactContentType = artifact?.contentType,
                ),
            ),
        )
        return provisionalUri
    }

    override suspend fun queueUpdate(webId: String, ticketUri: String, draft: TicketDraft) {
        val normalized = draft.withDerivedEventStart()
        if (ticketUri.startsWith(PROVISIONAL_PREFIX)) {
            rewritePendingCreate(webId, ticketUri, normalized)
            runCatching {
                ticketDao.upsert(
                    normalized.toProvisionalTicket(ticketUri).toCacheEntity(
                        webId,
                        System.currentTimeMillis(),
                        syncState = SyncState.PENDING_CREATE,
                    ),
                )
            }
            return
        }
        runCatching {
            val cached = ticketDao.findByUri(webId, ticketUri)?.toDomain()
            if (cached != null) {
                ticketDao.upsert(
                    cached.applying(normalized).toCacheEntity(
                        webId,
                        System.currentTimeMillis(),
                        syncState = SyncState.PENDING_UPDATE,
                    ),
                )
            }
        }
        enqueueTicketOp(
            webId,
            TicketOpType.UPDATE,
            payloadJson.encodeToString(
                TicketUpdatePayload.serializer(),
                TicketUpdatePayload(ticketUri, normalized),
            ),
        )
    }

    override suspend fun queueDelete(webId: String, ticketUri: String) {
        if (ticketUri.startsWith(PROVISIONAL_PREFIX)) {
            dropPendingCreate(webId, ticketUri)
            runCatching { ticketDao.deleteByUri(webId, ticketUri) }
            runCatching { blobStore.deleteTicket(webId, ticketUri) }
            return
        }
        runCatching { ticketDao.updateSyncState(webId, ticketUri, SyncState.PENDING_DELETE) }
        enqueueTicketOp(
            webId,
            TicketOpType.DELETE,
            payloadJson.encodeToString(TicketDeletePayload.serializer(), TicketDeletePayload(ticketUri)),
        )
    }

    override suspend fun drainTicketOutbox(webId: String): Boolean {
        val now = System.currentTimeMillis()
        val ops = ticketOutboxDao.dueOps(webId, now)
        var allOk = true
        for (op in ops) {
            val result = runCatching { executeTicketOp(webId, op) }
            if (result.isSuccess) {
                ticketOutboxDao.deleteById(op.id)
            } else {
                allOk = false
                val attempts = op.attempts + 1
                ticketOutboxDao.update(
                    op.copy(
                        status = OpStatus.FAILED,
                        attempts = attempts,
                        nextRetryAt = now + backoffMillis(attempts),
                        lastError = result.exceptionOrNull()?.message,
                        updatedAt = now,
                    ),
                )
            }
        }
        return allOk
    }

    override suspend fun clearCacheForWebId(webId: String) {
        runCatching { ticketDao.deleteAllForWebId(webId) }
        runCatching { ticketOutboxDao.deleteAllForWebId(webId) }
        runCatching { blobStore.clearForWebId(webId) }
    }

    override suspend fun createTicket(
        webId: String,
        draft: TicketDraft,
        artifact: TicketFile?,
    ): Ticket {
        val storage = authRepository.getStorages(webId).firstOrNull()
            ?: throw IllegalStateException("No storage found for $webId")
        val visuals = artifact
            ?.takeIf { it.contentType == PkpassParser.MIME_TYPE }
            ?.let { runCatching { PkpassImages.extract(it.bytes) }.getOrNull() }
        return ticketsDataModule.tickets
            .create(
                ownerWebId = webId,
                newTicket = draft.withDerivedEventStart().toLib(),
                storage = storage,
                artifact = artifact?.bytes,
                artifactContentType = artifact?.contentType,
                images = visuals?.toLibImages(),
            )
            .unwrap()
            .toDomain()
    }

    override suspend fun updateTicket(
        webId: String,
        ticketUri: String,
        draft: TicketDraft,
    ): Ticket {
        val updated = ticketsDataModule.tickets
            .update(webId, ticketUri, draft.withDerivedEventStart().toLib())
            .unwrap()
            .toDomain()
        runCatching {
            ticketDao.upsert(updated.toCacheEntity(webId, System.currentTimeMillis()))
        }
        return updated
    }

    override suspend fun deleteTicket(webId: String, ticketUri: String) {
        ticketsDataModule.tickets.delete(webId, ticketUri).unwrap()
        runCatching { ticketDao.deleteByUri(webId, ticketUri) }
        runCatching { blobStore.deleteTicket(webId, ticketUri) }
    }

    override suspend fun getTicketArtifact(
        webId: String,
        ticketUri: String,
        artifactUri: String,
    ): TicketFile {
        blobStore.read(webId, ticketUri, TicketBlobStore.ARTIFACT)?.let { bytes ->
            val mime = blobStore.readText(webId, ticketUri, TicketBlobStore.ARTIFACT_MIME)
                ?: PkpassParser.MIME_TYPE
            return TicketFile(mime, bytes)
        }
        val artifact = ticketsDataModule.tickets.getArtifact(webId, artifactUri).unwrap()
        runCatching {
            blobStore.write(webId, ticketUri, TicketBlobStore.ARTIFACT, artifact.bytes)
            blobStore.writeText(
                webId,
                ticketUri,
                TicketBlobStore.ARTIFACT_MIME,
                artifact.contentType,
            )
        }
        return TicketFile(artifact.contentType, artifact.bytes)
    }

    override suspend fun getTicketImages(
        webId: String,
        ticketUri: String,
        images: TicketImageUris?,
    ): PassImages? = coroutineScope {
        suspend fun fetch(role: String, uri: String?): ByteArray? {
            blobStore.read(webId, ticketUri, role)?.let { return it }
            uri ?: return null
            return runCatching {
                ticketsDataModule.tickets.getArtifact(webId, uri).unwrap().bytes
            }.getOrNull()?.also { runCatching { blobStore.write(webId, ticketUri, role, it) } }
        }
        val logo = async { fetch(TicketBlobStore.LOGO, images?.logo) }
        val icon = async { fetch(TicketBlobStore.ICON, images?.icon) }
        val strip = async { fetch(TicketBlobStore.STRIP, images?.strip) }
        val thumbnail = async { fetch(TicketBlobStore.THUMBNAIL, images?.thumbnail) }
        val footer = async { fetch(TicketBlobStore.FOOTER, images?.footer) }
        val background = async { fetch(TicketBlobStore.BACKGROUND, images?.background) }
        PassImages(
            logo = logo.await(),
            icon = icon.await(),
            strip = strip.await(),
            thumbnail = thumbnail.await(),
            footer = footer.await(),
            background = background.await(),
        ).takeIf { !it.isEmpty }
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

    private suspend fun fetchRemoteTicket(webId: String, ticketUri: String): Ticket =
        ticketsDataModule.tickets.get(webId, ticketUri).unwrap().toDomain()

    private suspend fun cacheTicketBinaries(webId: String, ticket: Ticket) {
        val roles = listOf(
            TicketBlobStore.LOGO to ticket.images?.logo,
            TicketBlobStore.ICON to ticket.images?.icon,
            TicketBlobStore.STRIP to ticket.images?.strip,
            TicketBlobStore.THUMBNAIL to ticket.images?.thumbnail,
            TicketBlobStore.FOOTER to ticket.images?.footer,
            TicketBlobStore.BACKGROUND to ticket.images?.background,
        )
        roles.forEach { (role, uri) ->
            if (uri != null && !blobStore.has(webId, ticket.uri, role)) {
                runCatching {
                    val bytes = ticketsDataModule.tickets.getArtifact(webId, uri).unwrap().bytes
                    blobStore.write(webId, ticket.uri, role, bytes)
                }
            }
        }
        val artifactUri = ticket.artifactUri
        if (artifactUri != null && !blobStore.has(webId, ticket.uri, TicketBlobStore.ARTIFACT)) {
            runCatching {
                val artifact = ticketsDataModule.tickets.getArtifact(webId, artifactUri).unwrap()
                blobStore.write(webId, ticket.uri, TicketBlobStore.ARTIFACT, artifact.bytes)
                blobStore.writeText(
                    webId,
                    ticket.uri,
                    TicketBlobStore.ARTIFACT_MIME,
                    artifact.contentType,
                )
            }
        }
    }

    private fun writeVisuals(webId: String, ticketUri: String, visuals: PassImages) {
        listOf(
            TicketBlobStore.LOGO to visuals.logo,
            TicketBlobStore.ICON to visuals.icon,
            TicketBlobStore.STRIP to visuals.strip,
            TicketBlobStore.THUMBNAIL to visuals.thumbnail,
            TicketBlobStore.FOOTER to visuals.footer,
            TicketBlobStore.BACKGROUND to visuals.background,
        ).forEach { (role, bytes) ->
            if (bytes != null) blobStore.write(webId, ticketUri, role, bytes)
        }
    }

    private suspend fun executeTicketOp(webId: String, op: TicketOutboxOpEntity) {
        when (op.type) {
            TicketOpType.CREATE -> {
                val payload =
                    payloadJson.decodeFromString(TicketCreatePayload.serializer(), op.payload)
                val artifact = if (payload.hasArtifact) {
                    blobStore.read(webId, payload.provisionalUri, TicketBlobStore.ARTIFACT)?.let {
                        TicketFile(
                            payload.artifactContentType
                                ?: blobStore.readText(
                                    webId,
                                    payload.provisionalUri,
                                    TicketBlobStore.ARTIFACT_MIME,
                                )
                                ?: PkpassParser.MIME_TYPE,
                            it,
                        )
                    }
                } else {
                    null
                }
                val created = createTicket(webId, payload.draft, artifact)
                runCatching {
                    ticketDao.deleteByUri(webId, payload.provisionalUri)
                    blobStore.move(webId, payload.provisionalUri, created.uri)
                    ticketDao.upsert(created.toCacheEntity(webId, System.currentTimeMillis()))
                }
            }

            TicketOpType.UPDATE -> {
                val payload =
                    payloadJson.decodeFromString(TicketUpdatePayload.serializer(), op.payload)
                updateTicket(webId, payload.ticketUri, payload.draft)
            }

            TicketOpType.DELETE -> {
                val payload =
                    payloadJson.decodeFromString(TicketDeletePayload.serializer(), op.payload)
                deleteTicket(webId, payload.ticketUri)
            }
        }
    }

    private suspend fun rewritePendingCreate(
        webId: String,
        provisionalUri: String,
        draft: TicketDraft,
    ) {
        runCatching {
            pendingCreateOps(webId, provisionalUri).forEach { (op, payload) ->
                ticketOutboxDao.update(
                    op.copy(
                        payload = payloadJson.encodeToString(
                            TicketCreatePayload.serializer(),
                            payload.copy(draft = draft),
                        ),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private suspend fun dropPendingCreate(webId: String, provisionalUri: String) {
        runCatching {
            pendingCreateOps(webId, provisionalUri).forEach { (op, _) ->
                ticketOutboxDao.deleteById(op.id)
            }
        }
    }

    private suspend fun pendingCreateOps(
        webId: String,
        provisionalUri: String,
    ): List<Pair<TicketOutboxOpEntity, TicketCreatePayload>> =
        ticketOutboxDao.pendingOps(webId)
            .filter { it.type == TicketOpType.CREATE }
            .mapNotNull { op ->
                runCatching {
                    payloadJson.decodeFromString(TicketCreatePayload.serializer(), op.payload)
                }.getOrNull()?.takeIf { it.provisionalUri == provisionalUri }?.let { op to it }
            }

    private suspend fun enqueueTicketOp(webId: String, type: TicketOpType, payload: String) {
        val now = System.currentTimeMillis()
        ticketOutboxDao.insert(
            TicketOutboxOpEntity(
                webId = webId,
                type = type,
                payload = payload,
                status = OpStatus.PENDING,
                attempts = 0,
                nextRetryAt = 0,
                lastError = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        TicketOutboxWorker.enqueue(workManager)
    }

    private fun backoffMillis(attempts: Int): Long =
        minOf(30_000L * (1L shl minOf(attempts, 6)), 3_600_000L)

    private fun PassImages.toLibImages(): NewTicketImages = NewTicketImages(
        logo = logo,
        icon = icon,
        strip = strip,
        thumbnail = thumbnail,
        footer = footer,
        background = background,
    )

    private fun parsedPass(passBytes: ByteArray): ParsedTicketFile? =
        PkpassParser.parse(passBytes)?.let { pass ->
            ParsedTicketFile(
                draft = pass.toDraft(),
                artifact = TicketFile(PkpassParser.MIME_TYPE, passBytes),
                visuals = runCatching { PkpassImages.extract(passBytes) }.getOrNull(),
            )
        }

    private fun <T : Parcelable> SolidResult<T>.unwrap(): T = getOrThrow()

    companion object {
        const val PROVISIONAL_PREFIX = "urn:solidshare:pending:"
    }
}

internal fun TicketDraft.withDerivedEventStart(): TicketDraft {
    val departure = journey?.from?.time
    if (departure.isNullOrBlank() || !event?.start.isNullOrBlank()) return this
    return copy(event = (event ?: TicketEventInfo()).copy(start = departure))
}
