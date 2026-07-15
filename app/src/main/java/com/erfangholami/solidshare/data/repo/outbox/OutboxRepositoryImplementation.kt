package com.erfangholami.solidshare.data.repo.outbox

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.http.HTTPAcceptType.OCTET_STREAM
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.solidshare.data.local.cache.BlobDao
import com.erfangholami.solidshare.data.local.cache.BlobState
import com.erfangholami.solidshare.data.local.cache.CacheKeyManager
import com.erfangholami.solidshare.data.local.cache.CachedBlobEntity
import com.erfangholami.solidshare.data.local.cache.CachedResourceEntity
import com.erfangholami.solidshare.data.local.cache.OpStatus
import com.erfangholami.solidshare.data.local.cache.OpType
import com.erfangholami.solidshare.data.local.cache.OutboxDao
import com.erfangholami.solidshare.data.local.cache.OutboxOpEntity
import com.erfangholami.solidshare.data.local.cache.ResourceDao
import com.erfangholami.solidshare.data.local.cache.SyncState
import com.erfangholami.solidshare.data.repo.file.FileRepository
import com.erfangholami.solidshare.data.repo.sharing.SharingRepository
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.ResourceAccess
import com.erfangholami.solidshare.domain.model.ResourceType
import com.erfangholami.solidshare.domain.model.getResourceType
import com.erfangholami.solidshare.util.NetworkMonitor
import com.erfangholami.solidshare.worker.OutboxWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutboxRepositoryImplementation @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val resourceManager: SolidResourceManager,
    private val resourceDao: ResourceDao,
    private val blobDao: BlobDao,
    private val outboxDao: OutboxDao,
    private val keyManager: CacheKeyManager,
    private val networkMonitor: NetworkMonitor,
    private val workManager: WorkManager,
    private val fileRepository: FileRepository,
    private val sharingRepository: SharingRepository,
) : OutboxRepository {

    override suspend fun enqueueUpload(
        webId: String,
        containerUrl: String,
        sourceUri: Uri,
        fileName: String,
        mimeType: String,
    ) {
        val now = System.currentTimeMillis()
        val fileUrl = containerUrl.trimEnd('/') + "/" + fileName
        val blobFile = blobFileFor(webId, fileUrl)
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            keyManager.encryptStream(input, blobFile)
        } ?: throw OutboxException("Cannot read file", terminal = true)
        val size = blobFile.length()
        val extension = fileName.substringAfterLast('.', "").lowercase().ifBlank { null }

        blobDao.upsert(
            CachedBlobEntity(
                webId = webId,
                uri = fileUrl,
                localPath = blobFile.absolutePath,
                etag = null,
                mimeType = mimeType,
                sizeBytes = size,
                pinned = true,
                lastAccessedAt = now,
                state = BlobState.PENDING_UPLOAD,
            ),
        )
        resourceDao.upsert(
            CachedResourceEntity(
                webId = webId,
                identifier = fileUrl,
                parentContainerUri = containerUrl,
                isContainer = false,
                name = fileName,
                extension = extension,
                mimeType = mimeType,
                resourceType = getResourceType(false, mimeType, extension),
                resourceTypes = emptyList(),
                sizeBytes = size,
                lastModified = now,
                etag = null,
                access = ResourceAccess.FULL,
                createdTime = now,
                itemCount = null,
                syncState = SyncState.PENDING_CREATE,
                cachedAt = now,
            ),
        )
        outboxDao.insert(
            OutboxOpEntity(
                webId = webId, type = OpType.UPLOAD, targetUri = fileUrl,
                parentContainerUri = containerUrl, name = fileName, mimeType = mimeType,
                blobPath = blobFile.absolutePath, isContainer = false, status = OpStatus.PENDING,
                attempts = 0, nextRetryAt = 0, lastError = null, createdAt = now, updatedAt = now,
            ),
        )
        triggerDrain()
    }

    override suspend fun enqueueCreateFolder(
        webId: String,
        containerUrl: String,
        folderName: String,
    ) {
        val now = System.currentTimeMillis()
        val name = folderName.trim()
        val folderUri = containerUrl.trimEnd('/') + "/" + name + "/"
        resourceDao.upsert(
            CachedResourceEntity(
                webId = webId,
                identifier = folderUri,
                parentContainerUri = containerUrl,
                isContainer = true,
                name = name,
                extension = null,
                mimeType = null,
                resourceType = ResourceType.FOLDER,
                resourceTypes = emptyList(),
                sizeBytes = null,
                lastModified = now,
                etag = null,
                access = ResourceAccess.FULL,
                createdTime = now,
                itemCount = 0,
                syncState = SyncState.PENDING_CREATE,
                cachedAt = now,
            ),
        )
        outboxDao.insert(
            OutboxOpEntity(
                webId = webId, type = OpType.CREATE_FOLDER, targetUri = folderUri,
                parentContainerUri = containerUrl, name = name, mimeType = null, blobPath = null,
                isContainer = true, status = OpStatus.PENDING, attempts = 0, nextRetryAt = 0,
                lastError = null, createdAt = now, updatedAt = now,
            ),
        )
        triggerDrain()
    }

    override suspend fun enqueueDelete(
        webId: String,
        resourceUri: String,
        isContainer: Boolean,
    ) {
        val now = System.currentTimeMillis()
        val existing = resourceDao.findByIdentifier(webId, resourceUri)
        if (existing?.syncState == SyncState.PENDING_CREATE) {
            outboxDao.deleteByTarget(webId, resourceUri)
            blobDao.find(webId, resourceUri)?.let { File(it.localPath).delete(); blobDao.delete(it) }
            resourceDao.deleteByIdentifier(webId, resourceUri)
            return
        }
        resourceDao.updateSyncState(webId, resourceUri, SyncState.PENDING_DELETE)
        outboxDao.insert(
            OutboxOpEntity(
                webId = webId, type = OpType.DELETE, targetUri = resourceUri,
                parentContainerUri = parentOf(resourceUri), name = nameOf(resourceUri),
                mimeType = null, blobPath = null, isContainer = isContainer,
                status = OpStatus.PENDING, attempts = 0, nextRetryAt = 0, lastError = null,
                createdAt = now, updatedAt = now,
            ),
        )
        triggerDrain()
    }

    override suspend fun enqueueDuplicate(webId: String, item: ContainerItem) {
        val now = System.currentTimeMillis()
        val parent = parentOf(item.identifier)
        val copyName = copyNameFor(item.name, item.extension, item.isContainer)
        val destUri = if (item.isContainer) {
            parent.trimEnd('/') + "/" + copyName + "/"
        } else {
            parent.trimEnd('/') + "/" + copyName
        }
        resourceDao.upsert(
            CachedResourceEntity(
                webId = webId,
                identifier = destUri,
                parentContainerUri = parent,
                isContainer = item.isContainer,
                name = copyName,
                extension = item.extension,
                mimeType = item.mimeType,
                resourceType = item.resourceType,
                resourceTypes = emptyList(),
                sizeBytes = item.sizeBytes,
                lastModified = now,
                etag = null,
                access = ResourceAccess.FULL,
                createdTime = now,
                itemCount = if (item.isContainer) item.itemCount else null,
                syncState = SyncState.PENDING_CREATE,
                cachedAt = now,
            ),
        )
        outboxDao.insert(
            OutboxOpEntity(
                webId = webId, type = OpType.COPY, targetUri = destUri, parentContainerUri = parent,
                name = copyName, mimeType = item.mimeType, blobPath = null,
                sourceUri = item.identifier, isContainer = item.isContainer, status = OpStatus.PENDING,
                attempts = 0, nextRetryAt = 0, lastError = null, createdAt = now, updatedAt = now,
            ),
        )
        triggerDrain()
    }

    override suspend fun drain() {
        outboxDao.resetInFlight()
        while (true) {
            val op = outboxDao.nextActionable(System.currentTimeMillis()) ?: break
            outboxDao.setStatus(op.id, OpStatus.IN_FLIGHT, System.currentTimeMillis())
            try {
                execute(op)
                outboxDao.delete(op)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                handleFailure(op, error)
            }
        }
    }

    override suspend fun hasUnfinishedWork(): Boolean = outboxDao.countUnfinished() > 0

    override suspend fun clearForWebId(webId: String) {
        outboxDao.purgeForWebId(webId)
    }

    private suspend fun execute(op: OutboxOpEntity) {
        when (op.type) {
            OpType.UPLOAD -> executeUpload(op)
            OpType.CREATE_FOLDER -> executeCreateFolder(op)
            OpType.DELETE -> executeDelete(op)
            OpType.COPY -> executeCopy(op)
        }
    }

    private suspend fun executeUpload(op: OutboxOpEntity) {
        val blobPath = op.blobPath ?: throw OutboxException("Missing upload data", terminal = true)
        val blobFile = File(blobPath)
        if (!blobFile.exists()) throw OutboxException("Upload data unavailable", terminal = true)
        val temp = File.createTempFile("outbox", null, openDir())
        try {
            temp.outputStream().use { keyManager.decryptStream(blobFile, it) }
            temp.inputStream().use { stream ->
                requireCreated(
                    resourceManager.createInContainer(
                        op.webId,
                        op.parentContainerUri,
                        SolidNonRDFResource(op.targetUri, op.mimeType ?: OCTET_STREAM, stream),
                    ),
                )
            }
            resourceDao.updateSyncState(op.webId, op.targetUri, SyncState.SYNCED)
            blobDao.find(op.webId, op.targetUri)?.let { blobDao.upsert(it.copy(state = BlobState.COMPLETE)) }
        } finally {
            temp.delete()
        }
    }

    private suspend fun executeCreateFolder(op: OutboxOpEntity) {
        requireCreated(
            resourceManager.createInContainer(
                op.webId,
                op.parentContainerUri,
                SolidContainer(op.targetUri),
            ),
        )
        resourceDao.updateSyncState(op.webId, op.targetUri, SyncState.SYNCED)
    }

    private suspend fun executeDelete(op: OutboxOpEntity) {
        when (val response = resourceManager.delete(op.webId, op.targetUri)) {
            is SolidResult.Success -> removeResource(op.webId, op.targetUri)
            is SolidResult.Failure -> {
                val error = response.error
                when {
                    error.code == SolidErrorCode.NOT_FOUND ->
                        removeResource(op.webId, op.targetUri)

                    error.httpStatus != null -> throw OutboxException(
                        "HTTP ${error.httpStatus}",
                        terminal = isTerminalCode(error),
                    )

                    else -> throw error.asException()
                }
            }
        }
    }

    private suspend fun executeCopy(op: OutboxOpEntity) {
        val sourceUri = op.sourceUri ?: throw OutboxException("Missing copy source", terminal = true)
        val sourceName = Uri.decode(nameOf(sourceUri))
        val extension = if (!op.isContainer && '.' in sourceName) {
            sourceName.substringAfterLast('.').lowercase().ifBlank { null }
        } else {
            null
        }
        val sourceItem = ContainerItem(
            identifier = sourceUri,
            isContainer = op.isContainer,
            name = sourceName,
            extension = extension,
            mimeType = op.mimeType,
            resourceType = getResourceType(op.isContainer, op.mimeType, extension),
            resourceTypes = emptyList(),
            sizeBytes = null,
            lastModified = null,
            etag = null,
        )
        val created = fileRepository.duplicateResource(op.webId, sourceItem)
        created.forEach { runCatching { sharingRepository.makePrivate(op.webId, it) } }
        resourceDao.updateSyncState(op.webId, op.targetUri, SyncState.SYNCED)
    }

    private fun requireCreated(response: SolidResult<String?>) {
        when (response) {
            is SolidResult.Success -> Unit
            is SolidResult.Failure -> {
                val error = response.error
                if (error.httpStatus != null) {
                    throw OutboxException("HTTP ${error.httpStatus}", terminal = isTerminalCode(error))
                } else {
                    throw error.asException()
                }
            }
        }
    }

    private suspend fun removeResource(webId: String, uri: String) {
        resourceDao.deleteByIdentifier(webId, uri)
        blobDao.find(webId, uri)?.let { File(it.localPath).delete(); blobDao.delete(it) }
    }

    private suspend fun handleFailure(op: OutboxOpEntity, error: Throwable) {
        val now = System.currentTimeMillis()
        if (error is OutboxException && error.terminal) {
            outboxDao.setRetry(op.id, OpStatus.ERROR, op.attempts, 0, error.message, now)
            resourceDao.updateSyncState(op.webId, op.targetUri, SyncState.ERROR)
        } else {
            val attempts = op.attempts + 1
            val backoff = (BASE_BACKOFF_MS * (1L shl attempts.coerceAtMost(MAX_BACKOFF_SHIFT)))
                .coerceAtMost(MAX_BACKOFF_MS)
            outboxDao.setRetry(op.id, OpStatus.FAILED, attempts, now + backoff, error.message, now)
        }
    }

    private fun triggerDrain() {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        workManager.enqueueUniqueWork(OutboxWorker.WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private fun blobDir(): File = File(context.filesDir, BLOB_DIR).apply { mkdirs() }

    private fun openDir(): File = File(context.cacheDir, OPEN_DIR).apply { mkdirs() }

    private fun blobFileFor(webId: String, uri: String): File =
        File(blobDir(), blobKeyFor(webId, uri))

    private fun blobKeyFor(webId: String, uri: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$webId\n$uri".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun parentOf(uri: String): String = uri.trimEnd('/').substringBeforeLast('/') + "/"

    private fun nameOf(uri: String): String = uri.trimEnd('/').substringAfterLast('/')

    private fun copyNameFor(name: String, extension: String?, isContainer: Boolean): String {
        val base = name.trimEnd('/')
        return when {
            isContainer -> "${base}_copy"
            extension != null && base.endsWith(".$extension") ->
                "${base.dropLast(extension.length + 1)}_copy.$extension"

            else -> "${base}_copy"
        }
    }

    private fun isTerminalCode(error: SolidError): Boolean =
        error.httpStatus?.let { it in TERMINAL_CODES } ?: false

    private companion object {
        const val BLOB_DIR = "blob_cache"
        const val OPEN_DIR = "open"
        const val BASE_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_SHIFT = 10
        const val MAX_BACKOFF_MS = 30 * 60 * 1_000L
        val TERMINAL_CODES = setOf(400, 401, 403, 405, 409, 410, 412, 415, 422)
    }
}
