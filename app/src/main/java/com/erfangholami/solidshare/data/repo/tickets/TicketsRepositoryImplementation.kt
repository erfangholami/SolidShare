package com.erfangholami.solidshare.data.repo.tickets

import android.os.Parcelable
import com.erfangholami.androidsolidservices.api.datamodule.tickets.SolidTicketsDataModule
import com.erfangholami.androidsolidservices.shared.model.tickets.NewTicketImages
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.solidshare.data.local.cache.OpStatus
import com.erfangholami.solidshare.data.local.cache.SyncState
import com.erfangholami.solidshare.data.local.cache.TicketBlobStore
import com.erfangholami.solidshare.data.local.cache.ModuleOutboxOpEntity
import com.erfangholami.solidshare.data.local.cache.CachedEntityDao
import com.erfangholami.solidshare.data.passimport.PassImages
import com.erfangholami.solidshare.data.passimport.PkpassImages
import com.erfangholami.solidshare.data.passimport.PkpassParser
import com.erfangholami.solidshare.data.passimport.TicketFileSniffer
import com.erfangholami.solidshare.data.passimport.TicketFileType
import com.erfangholami.solidshare.data.repo.auth.AuthRepository
import com.erfangholami.solidshare.data.repo.datamodule.DataModuleIds
import com.erfangholami.solidshare.data.repo.outbox.ModuleOutbox
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.model.Ticket
import com.erfangholami.solidshare.domain.model.TicketDraft
import com.erfangholami.solidshare.domain.model.TicketEventInfo
import com.erfangholami.solidshare.domain.model.TicketFile
import com.erfangholami.solidshare.domain.model.TicketImageUris
import com.erfangholami.solidshare.domain.model.TicketSource
import com.erfangholami.solidshare.domain.model.TicketSummaryItem
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class TicketsRepositoryImplementation @Inject constructor(
    private val ticketsDataModule: SolidTicketsDataModule,
    private val authRepository: AuthRepository,
    private val sharingRepository: SharingRepository,
    private val entityDao: CachedEntityDao,
    private val outbox: ModuleOutbox,
    private val blobStore: TicketBlobStore,
) : TicketsRepository {

    private val payloadJson = Json { ignoreUnknownKeys = true }

    override fun observeTickets(webId: String): Flow<List<TicketSummaryItem>> =
        entityDao.observeNewestFirst(moduleId, webId)
            .map { rows -> rows.map { it.toTicketSummary() } }

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
            runCatching { entityDao.replaceSynced(moduleId, webId, entities) }
        } else {
            runCatching { entityDao.upsertAll(entities) }
        }
        tickets.forEach { runCatching { cacheTicketBinaries(webId, it) } }
    }

    override suspend fun getTicket(webId: String, ticketUri: String): Ticket =
        entityDao.findByUri(moduleId, webId, ticketUri)?.toTicket()
            ?: fetchRemoteTicket(webId, ticketUri).also { ticket ->
                runCatching {
                    entityDao.upsert(ticket.toCacheEntity(webId, System.currentTimeMillis()))
                }
            }

    override fun ticketShareTarget(ticketUri: String): String =
        ticketsDataModule.tickets.shareTarget(ticketUri)

    override suspend fun getSharedTicket(webId: String, target: String): Ticket =
        if (target.endsWith("/")) {
            ticketsDataModule.tickets.findInContainer(webId, target).unwrap().toDomain()
        } else {
            fetchRemoteTicket(webId, target)
        }

    override suspend fun getSharedTicketImages(webId: String, ticket: Ticket): PassImages? {
        fetchSharedImageBytes(webId, ticket)?.let { return it }
        val artifactUri = ticket.artifactUri ?: return null
        val artifact = runCatching { getSharedTicketArtifact(webId, artifactUri) }.getOrNull()
            ?: return null
        return runCatching { parseTicketFile(artifact.bytes) }.getOrNull()
            ?.firstOrNull()?.visuals
    }

    private suspend fun fetchSharedImageBytes(webId: String, ticket: Ticket): PassImages? =
        coroutineScope {
            suspend fun fetch(uri: String?): ByteArray? {
                uri ?: return null
                return runCatching {
                    ticketsDataModule.tickets.getArtifact(webId, uri).unwrap().bytes
                }.getOrNull()
            }
            val logo = async { fetch(ticket.images?.logo) }
            val icon = async { fetch(ticket.images?.icon) }
            val strip = async { fetch(ticket.images?.strip) }
            val thumbnail = async { fetch(ticket.images?.thumbnail) }
            val footer = async { fetch(ticket.images?.footer) }
            val background = async { fetch(ticket.images?.background) }
            PassImages(
                logo = logo.await(),
                icon = icon.await(),
                strip = strip.await(),
                thumbnail = thumbnail.await(),
                footer = footer.await(),
                background = background.await(),
            ).takeIf { !it.isEmpty }
        }

    override suspend fun getSharedTicketArtifact(webId: String, artifactUri: String): TicketFile {
        val artifact = ticketsDataModule.tickets.getArtifact(webId, artifactUri).unwrap()
        return TicketFile(artifact.contentType, artifact.bytes)
    }

    override suspend fun addSharedTicketToWallet(webId: String, shared: Ticket): String {
        val artifact = shared.artifactUri?.let {
            runCatching { getSharedTicketArtifact(webId, it) }.getOrNull()
        }
        val images = if (artifact?.contentType == PkpassParser.MIME_TYPE) {
            null
        } else {
            runCatching { getSharedTicketImages(webId, shared) }.getOrNull()
        }
        val draft = shared.toDraft().copy(copiedFrom = shared.uri)
        return queueCreate(webId, draft, artifact, images)
    }

    override suspend fun findTicketCopiedFrom(webId: String, originalUri: String): String? =
        entityDao.get(moduleId, webId).firstNotNullOfOrNull { row ->
            runCatching { row.toTicket() }.getOrNull()
                ?.takeIf { it.copiedFrom == originalUri }
                ?.uri
        }

    override suspend fun queueCreate(
        webId: String,
        draft: TicketDraft,
        artifact: TicketFile?,
        images: PassImages?,
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
                    PkpassImages.extract(artifact.bytes)
                        ?.let { writeVisuals(webId, provisionalUri, it) }
                }
            }
            images?.let { writeVisuals(webId, provisionalUri, it) }
        }
        runCatching {
            entityDao.upsert(
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
                    hasImages = images != null,
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
                entityDao.upsert(
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
            val cached = entityDao.findByUri(moduleId, webId, ticketUri)?.toTicket()
            if (cached != null) {
                entityDao.upsert(
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
            runCatching { entityDao.deleteByUri(moduleId, webId, ticketUri) }
            runCatching { blobStore.deleteTicket(webId, ticketUri) }
            return
        }
        runCatching {
            entityDao.updateSyncState(moduleId, webId, ticketUri, SyncState.PENDING_DELETE)
        }
        enqueueTicketOp(
            webId,
            TicketOpType.DELETE,
            payloadJson.encodeToString(TicketDeletePayload.serializer(), TicketDeletePayload(ticketUri)),
        )
    }

    override val moduleId: String = DataModuleIds.TICKETS

    override suspend fun drain(webId: String): Boolean =
        outbox.drain(moduleId, webId) { executeTicketOp(webId, it) }

    override suspend fun clearCache(webId: String) {
        runCatching { entityDao.deleteAllForWebId(moduleId, webId) }
        runCatching { outbox.clear(moduleId, webId) }
        runCatching { blobStore.clearForWebId(webId) }
    }

    override suspend fun refreshIssuerPasses(webId: String) {
        val candidates = entityDao.get(moduleId, webId)
            .filter { it.syncState == SyncState.SYNCED }
            .mapNotNull { row -> runCatching { row.toTicket() }.getOrNull() }
            .filter { ticket ->
                ticket.source == TicketSource.PKPASS &&
                    ticket.voided != true &&
                    ticket.passInfo?.webServiceUrl != null &&
                    ticket.passInfo?.passTypeIdentifier != null &&
                    ticket.passInfo?.serialNumber != null &&
                    ticket.passInfo?.authenticationToken != null
            }
        candidates.forEach { ticket -> runCatching { refreshIssuerPass(webId, ticket) } }
    }

    private suspend fun refreshIssuerPass(webId: String, ticket: Ticket) {
        val pass = requireNotNull(ticket.passInfo)
        val url = pass.webServiceUrl!!.trimEnd('/') +
            "/v1/passes/${pass.passTypeIdentifier}/${pass.serialNumber}"
        val since = blobStore.readText(webId, ticket.uri, TicketBlobStore.REFRESH_SINCE)
        val response = fetchIssuerPass(url, pass.authenticationToken!!, since) ?: return
        val parsed = parsedPass(response.first) ?: return
        updateTicket(webId, ticket.uri, parsed.draft)
        val refreshed = ticketsDataModule.tickets
            .putArtifact(
                webId,
                ticket.uri,
                response.first,
                PkpassParser.MIME_TYPE,
                parsed.visuals?.toLibImages(),
            )
            .unwrap()
            .toDomain()
        runCatching {
            blobStore.write(webId, ticket.uri, TicketBlobStore.ARTIFACT, response.first)
            blobStore.writeText(
                webId,
                ticket.uri,
                TicketBlobStore.ARTIFACT_MIME,
                PkpassParser.MIME_TYPE,
            )
            parsed.visuals?.let { writeVisuals(webId, ticket.uri, it) }
            response.second?.let {
                blobStore.writeText(webId, ticket.uri, TicketBlobStore.REFRESH_SINCE, it)
            }
            entityDao.upsert(refreshed.toCacheEntity(webId, System.currentTimeMillis()))
        }
    }

    private suspend fun fetchIssuerPass(
        url: String,
        authenticationToken: String,
        since: String?,
    ): Pair<ByteArray, String?>? = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "ApplePass $authenticationToken")
            since?.let { connection.setRequestProperty("If-Modified-Since", it) }
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val bytes = connection.inputStream.use { it.readBytes() }
            bytes to connection.getHeaderField("Last-Modified")
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun createTicket(
        webId: String,
        draft: TicketDraft,
        artifact: TicketFile?,
        images: PassImages?,
    ): Ticket {
        val storage = authRepository.getStorages(webId).firstOrNull()
            ?: throw IllegalStateException("No storage found for $webId")
        val visuals = images
            ?: artifact
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
            entityDao.upsert(updated.toCacheEntity(webId, System.currentTimeMillis()))
        }
        return updated
    }

    override suspend fun deleteTicket(webId: String, ticketUri: String) {
        ticketsDataModule.tickets.delete(webId, ticketUri).unwrap()
        runCatching {
            sharingRepository.purgeGivenShares(webId, ticketShareTarget(ticketUri))
        }
        runCatching { entityDao.deleteByUri(moduleId, webId, ticketUri) }
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

    private suspend fun writeVisuals(webId: String, ticketUri: String, visuals: PassImages) {
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

    private suspend fun readVisuals(webId: String, ticketUri: String): PassImages? = PassImages(
        logo = blobStore.read(webId, ticketUri, TicketBlobStore.LOGO),
        icon = blobStore.read(webId, ticketUri, TicketBlobStore.ICON),
        strip = blobStore.read(webId, ticketUri, TicketBlobStore.STRIP),
        thumbnail = blobStore.read(webId, ticketUri, TicketBlobStore.THUMBNAIL),
        footer = blobStore.read(webId, ticketUri, TicketBlobStore.FOOTER),
        background = blobStore.read(webId, ticketUri, TicketBlobStore.BACKGROUND),
    ).takeIf { !it.isEmpty }

    private suspend fun executeTicketOp(webId: String, op: ModuleOutboxOpEntity) {
        when (TicketOpType.valueOf(op.type)) {
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
                val images = if (payload.hasImages) {
                    readVisuals(webId, payload.provisionalUri)
                } else {
                    null
                }
                val created = createTicket(webId, payload.draft, artifact, images)
                runCatching {
                    entityDao.deleteByUri(moduleId, webId, payload.provisionalUri)
                    blobStore.move(webId, payload.provisionalUri, created.uri)
                    entityDao.upsert(created.toCacheEntity(webId, System.currentTimeMillis()))
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
                outbox.rewrite(
                    op,
                    payloadJson.encodeToString(
                        TicketCreatePayload.serializer(),
                        payload.copy(draft = draft),
                    ),
                )
            }
        }
    }

    private suspend fun dropPendingCreate(webId: String, provisionalUri: String) {
        runCatching {
            pendingCreateOps(webId, provisionalUri).forEach { (op, _) -> outbox.drop(op) }
        }
    }

    private suspend fun pendingCreateOps(
        webId: String,
        provisionalUri: String,
    ): List<Pair<ModuleOutboxOpEntity, TicketCreatePayload>> =
        outbox.pendingOps(moduleId, webId)
            .filter { it.type == TicketOpType.CREATE.name }
            .mapNotNull { op ->
                runCatching {
                    payloadJson.decodeFromString(TicketCreatePayload.serializer(), op.payload)
                }.getOrNull()?.takeIf { it.provisionalUri == provisionalUri }?.let { op to it }
            }

    private suspend fun enqueueTicketOp(webId: String, type: TicketOpType, payload: String) {
        outbox.enqueue(moduleId, webId, type.name, payload)
    }

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
