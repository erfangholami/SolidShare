package com.erfangholami.solidshare.data.repo.file

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.http.HTTPAcceptType.OCTET_STREAM
import com.erfangholami.androidsolidservices.shared.result.SolidError
import com.erfangholami.androidsolidservices.shared.result.SolidErrorCode
import com.erfangholami.androidsolidservices.shared.result.SolidResult
import com.erfangholami.androidsolidservices.shared.model.access.WacAllow
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.util.getContentLength
import com.erfangholami.androidsolidservices.shared.util.getETag
import com.erfangholami.solidshare.data.local.cache.BlobDao
import com.erfangholami.solidshare.data.local.cache.BlobState
import com.erfangholami.solidshare.data.local.cache.CacheKeyManager
import com.erfangholami.solidshare.data.local.cache.CachedBlobEntity
import com.erfangholami.solidshare.data.local.cache.ResourceDao
import com.erfangholami.solidshare.data.local.cache.toCacheEntity
import com.erfangholami.solidshare.data.local.cache.toDomain
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.DownloadedFile
import com.erfangholami.solidshare.domain.model.ResourceAccess
import com.erfangholami.solidshare.domain.model.ResourceMeta
import com.erfangholami.solidshare.domain.model.getResourceType
import com.erfangholami.solidshare.util.NetworkMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject


class FileRepositoryImplementation @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val resourceManager: SolidResourceManager,
    private val resourceDao: ResourceDao,
    private val blobDao: BlobDao,
    private val keyManager: CacheKeyManager,
    private val networkMonitor: NetworkMonitor,
) : FileRepository {

    override fun observeContainer(
        webId: String,
        containerUrl: String,
    ): Flow<List<ContainerItem>> =
        resourceDao.observeContainer(webId, containerUrl).map { rows -> rows.map { it.toDomain() } }

    override suspend fun refreshContainer(
        webId: String,
        containerUrl: String,
        includeItemAccess: Boolean,
    ) {
        val items = getContainerContents(webId, containerUrl, includeItemAccess)
        val now = System.currentTimeMillis()
        resourceDao.replaceContainer(
            webId = webId,
            parentUri = containerUrl,
            items = items.map {
                it.toCacheEntity(
                    webId = webId,
                    parentContainerUri = containerUrl,
                    cachedAt = now,
                )
            },
        )
    }

    override suspend fun getCachedContainer(
        webId: String,
        containerUrl: String,
    ): List<ContainerItem> =
        resourceDao.getContainer(webId, containerUrl).map { it.toDomain() }

    override suspend fun cacheContainer(
        webId: String,
        containerUrl: String,
        items: List<ContainerItem>,
    ) {
        val now = System.currentTimeMillis()
        resourceDao.replaceContainer(
            webId = webId,
            parentUri = containerUrl,
            items = items.map {
                it.toCacheEntity(webId = webId, parentContainerUri = containerUrl, cachedAt = now)
            },
        )
    }

    override suspend fun lastCachedAt(webId: String, containerUrl: String): Long? =
        resourceDao.lastCachedAt(webId, containerUrl)

    override suspend fun getContainerContents(
        webId: String,
        containerUrl: String,
        includeItemAccess: Boolean,
    ): List<ContainerItem> {
        val response =
            resourceManager.read(webId, containerUrl, SolidContainer::class.java)
        val refs = when (response) {
            is SolidResult.Success -> response.value.getContained()
            is SolidResult.Failure -> throw accessAwareError(response.error, containerUrl)
        }

        return coroutineScope {
            val gate = Semaphore(MAX_PARALLEL_HEADS)
            refs.map { ref ->
                async {
                    val identifier = ref.identifier
                    val types = ref.types
                    val isContainer = ref.isContainer() || ref.isContainerByUri()
                    val rawName = identifier.trimEnd('/').substringAfterLast('/')
                    val extension = if (!isContainer && '.' in rawName) {
                        rawName.substringAfterLast('.').lowercase().ifBlank { null }
                    } else null

                    val metadata =
                        if (isContainer && !includeItemAccess) null else gate.withPermit {
                            runCatching { resourceManager.head(webId, identifier) }
                                .getOrNull()
                                ?.let { res -> if (res is SolidResult.Success) res.value else null }
                        }

                    val mimeType =
                        metadata?.contentType?.substringBefore(';')?.trim()?.ifBlank { null }
                            ?: extension?.let {
                                MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
                            }

                    ContainerItem(
                        identifier = Uri.decode(identifier),
                        isContainer = isContainer,
                        name = Uri.decode(rawName).ifBlank { identifier },
                        extension = extension,
                        mimeType = mimeType,
                        resourceType = getResourceType(isContainer, mimeType, extension),
                        resourceTypes = types,
                        sizeBytes = if (isContainer) null
                        else ref.size ?: metadata?.contentLength?.takeIf { it >= 0 },
                        lastModified = ref.modified?.let(::parseIsoDateMillis)
                            ?: ref.mtime?.let { it * 1000 }
                            ?: metadata?.lastModified?.let(::parseHttpDateMillis),
                        etag = metadata?.etag,
                        access = if (includeItemAccess) {
                            metadata?.wacAllow?.toResourceAccess() ?: ResourceAccess.READ_ONLY
                        } else {
                            ResourceAccess.FULL
                        },
                    )
                }
            }.awaitAll()
        }
    }

    override suspend fun getContainerItemCount(
        webId: String,
        containerUrl: String,
    ): Int {
        val container = (resourceManager.read(
            webId,
            containerUrl,
            SolidContainer::class.java,
        ) as? SolidResult.Success)?.value ?: return 0
        return container.getContained().size
    }

    override suspend fun getResourceMeta(
        webId: String,
        resourceUri: String,
    ): ResourceMeta {
        val uri = resourceUri
        if (resourceUri.endsWith("/")) {
            val container = (resourceManager.read(webId, uri, SolidContainer::class.java)
                    as? SolidResult.Success)?.value
            return ResourceMeta(
                sizeBytes = null,
                lastModified = container?.getLastModified(),
                itemCount = container?.getContained()?.size,
            )
        }
        val metadata = (resourceManager.head(webId, uri)
                as? SolidResult.Success)?.value
        return ResourceMeta(
            sizeBytes = metadata?.contentLength?.takeIf { it >= 0 },
            lastModified = metadata?.lastModified?.let(::parseHttpDateMillis),
            itemCount = null,
        )
    }

    override suspend fun getResourceCreatedTime(webId: String, item: ContainerItem): Long? {
        if (!item.isContainer && !isRdfMimeType(item.mimeType)) return null
        return (resourceManager.read(
            webId,
            item.identifier,
            SolidRDFResource::class.java,
        ) as? SolidResult.Success)?.value?.getCreatedTime()
    }

    private fun isRdfMimeType(mimeType: String?): Boolean {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase() ?: return false
        return mime == "text/turtle" || mime == "application/ld+json" ||
                mime == "application/rdf+xml" || mime == "application/n-triples" ||
                mime == "application/n-quads" || mime == "text/n3" || mime == "application/trig"
    }

    override suspend fun downloadFile(webId: String, fileUrl: String): DownloadedFile {
        blobDao.find(webId, fileUrl)?.let { cached ->
            val encrypted = File(cached.localPath)
            if (encrypted.exists() && isBlobStillFresh(webId, fileUrl, cached.etag)) {
                blobDao.touch(webId, fileUrl, System.currentTimeMillis())
                return DownloadedFile(
                    path = decryptToOpenTemp(webId, fileUrl, encrypted).absolutePath,
                    mimeType = cached.mimeType,
                    etag = cached.etag,
                )
            }
        }
        val response =
            resourceManager.read(webId, fileUrl, SolidNonRDFResource::class.java)
        return when (response) {
            is SolidResult.Success -> {
                val resource = response.value
                val rawContentType = resource.getContentType().substringBefore(';').trim()
                val filename = fileNameFor(fileUrl)
                val mimeType = rawContentType.ifBlank {
                    val ext = filename.substringAfterLast('.', "").lowercase()
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: OCTET_STREAM
                }
                val etag = resource.getHeaders().getETag()
                val openFile = openTempFile(webId, fileUrl, filename)
                resource.use { r ->
                    openFile.outputStream().use { output -> r.getEntity().copyTo(output) }
                }
                persistBlob(webId, fileUrl, openFile, mimeType, etag)
                DownloadedFile(path = openFile.absolutePath, mimeType = mimeType, etag = etag)
            }

            is SolidResult.Failure -> throw accessAwareError(response.error, fileUrl)
        }
    }

    override fun observeAvailableOffline(webId: String): Flow<List<String>> =
        blobDao.observeAvailableOfflineUris(webId, BlobState.COMPLETE)

    override fun observePendingUris(webId: String): Flow<List<String>> =
        resourceDao.observePendingUris(webId)

    override fun observeErrorUris(webId: String): Flow<List<String>> =
        resourceDao.observeErrorUris(webId)

    override suspend fun pinOffline(webId: String, fileUrl: String) {
        if (blobDao.find(webId, fileUrl) == null) downloadFile(webId, fileUrl)
        blobDao.setPinned(webId, fileUrl, true)
    }

    override suspend fun unpinOffline(webId: String, fileUrl: String) {
        blobDao.setPinned(webId, fileUrl, false)
    }

    override suspend fun clearCacheForWebId(webId: String) {
        blobDao.forWebId(webId).forEach { File(it.localPath).delete() }
        blobDao.purgeRowsForWebId(webId)
        resourceDao.purgeForWebId(webId)
    }

    private suspend fun persistBlob(
        webId: String,
        fileUrl: String,
        plaintext: File,
        mimeType: String,
        etag: String?,
    ) {
        val wasPinned = blobDao.find(webId, fileUrl)?.pinned == true
        val encrypted = blobFileFor(webId, fileUrl)
        plaintext.inputStream().use { keyManager.encryptStream(it, encrypted) }
        blobDao.upsert(
            CachedBlobEntity(
                webId = webId,
                uri = fileUrl,
                localPath = encrypted.absolutePath,
                etag = etag,
                mimeType = mimeType,
                sizeBytes = encrypted.length(),
                pinned = wasPinned,
                lastAccessedAt = System.currentTimeMillis(),
                state = BlobState.COMPLETE,
            ),
        )
        enforceBlobBudget()
    }

    private fun decryptToOpenTemp(webId: String, fileUrl: String, encrypted: File): File {
        val temp = openTempFile(webId, fileUrl, fileNameFor(fileUrl))
        temp.outputStream().use { keyManager.decryptStream(encrypted, it) }
        return temp
    }

    private suspend fun enforceBlobBudget() {
        var total = blobDao.unpinnedSize(BlobState.COMPLETE)
        if (total <= MAX_BLOB_CACHE_BYTES) return
        for (blob in blobDao.unpinnedByAge(BlobState.COMPLETE)) {
            if (total <= MAX_BLOB_CACHE_BYTES) break
            File(blob.localPath).delete()
            blobDao.delete(blob)
            total -= blob.sizeBytes
        }
    }

    private suspend fun removeBlob(webId: String, fileUrl: String) {
        blobDao.find(webId, fileUrl)?.let {
            File(it.localPath).delete()
            blobDao.delete(it)
        }
    }

    private fun fileNameFor(fileUrl: String): String =
        fileUrl.trimEnd('/').substringAfterLast('/')

    private fun blobDir(): File = File(context.filesDir, BLOB_DIR).apply { mkdirs() }

    private fun openDir(): File = File(context.cacheDir, OPEN_DIR).apply { mkdirs() }

    private fun blobFileFor(webId: String, fileUrl: String): File =
        File(blobDir(), blobKey(webId, fileUrl))

    private fun openTempFile(webId: String, fileUrl: String, filename: String): File =
        File(openDir(), blobKey(webId, fileUrl) + "-" + filename)

    private fun blobKey(webId: String, fileUrl: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$webId\n$fileUrl".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    override suspend fun probeAccess(webId: String, resourceUri: String): ResourceAccess {
        return when (val response = resourceManager.head(webId, resourceUri)) {
            is SolidResult.Success ->
                response.value.wacAllow?.toResourceAccess() ?: ResourceAccess.READ_ONLY

            is SolidResult.Failure -> throw accessAwareError(response.error, resourceUri)
        }
    }

    override suspend fun downloadToDevice(
        webId: String,
        fileUrl: String,
        fileName: String,
        mimeType: String,
        onProgress: (Int) -> Unit,
    ): Uri {
        onProgress(0)
        val response =
            resourceManager.read(webId, fileUrl, SolidNonRDFResource::class.java)

        return when (response) {
            is SolidResult.Success -> {
                val resource = response.value
                val contentLength = resource.getHeaders().getContentLength()

                onProgress(50)

                val destUri = insertIntoDownloads(fileName, mimeType)
                    ?: throw Exception("Could not create entry in Downloads")

                val outputStream = context.contentResolver.openOutputStream(destUri)
                    ?: throw IllegalStateException("Could not open output stream for the download")
                outputStream.use { output ->
                    var bytesWritten = 0L
                    val buffer = ByteArray(8 * 1024)
                    resource.use { r ->
                        val input = r.getEntity()
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesWritten += read
                            if (contentLength > 0) {
                                onProgress(
                                    50 + ((bytesWritten * 50) / contentLength).toInt()
                                        .coerceIn(0, 49)
                                )
                            }
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    context.contentResolver.update(destUri, values, null, null)
                }

                onProgress(100)
                destUri
            }

            is SolidResult.Failure -> throw response.error.asException()
        }
    }

    private fun insertIntoDownloads(fileName: String, mimeType: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val file = File(dir, fileName)
            Uri.fromFile(file)
        }
    }

    override suspend fun uploadFile(
        webId: String,
        containerUrl: String,
        fileName: String,
        mimeType: String,
        inputStream: InputStream,
        onProgress: (Int) -> Unit,
    ) {
        onProgress(10)

        val fileUrl = containerUrl.trimEnd('/') + "/$fileName"
        val resource = SolidNonRDFResource(
            fileUrl,
            mimeType,
            inputStream,
        )
        onProgress(40)

        when (
            val response =
                resourceManager.createInContainer(webId, containerUrl, resource)
        ) {
            is SolidResult.Success -> {
                removeBlob(webId, fileUrl)
                onProgress(100)
            }
            is SolidResult.Failure -> throw response.error.asException()
        }
    }

    override suspend fun createFolder(webId: String, containerUrl: String, folderName: String) {
        val folderUri = containerUrl.trimEnd('/') + "/${folderName.trim()}/"
        val container = SolidContainer(folderUri)
        when (
            val response =
                resourceManager.createInContainer(webId, containerUrl, container)
        ) {
            is SolidResult.Success -> Unit
            is SolidResult.Failure -> throw response.error.asException()
        }
    }

    override suspend fun deleteResource(webId: String, resourceUrl: String, isContainer: Boolean) {
        val resourceUri = resourceUrl
        when (val response = resourceManager.delete(webId, resourceUri)) {
            is SolidResult.Success -> removeBlob(webId, resourceUrl)
            is SolidResult.Failure -> throw response.error.asException()
        }
    }

    override suspend fun duplicateResource(webId: String, item: ContainerItem): List<String> {
        val created = mutableListOf<String>()
        copyInto(
            webId = webId,
            sourceUri = item.identifier,
            isContainer = item.isContainer,
            destParentContainer = parentContainerUrl(item.identifier),
            destName = copyNameFor(item.name, item.extension, item.isContainer),
            created = created,
        )
        return created
    }

    private suspend fun copyInto(
        webId: String,
        sourceUri: String,
        isContainer: Boolean,
        destParentContainer: String,
        destName: String,
        created: MutableList<String>,
    ) {
        if (isContainer) {
            createFolder(webId, destParentContainer, destName)
            val destContainer = destParentContainer.trimEnd('/') + "/" + destName.trim() + "/"
            created += destContainer
            getContainerContents(webId, sourceUri).forEach { child ->
                copyInto(
                    webId = webId,
                    sourceUri = child.identifier,
                    isContainer = child.isContainer,
                    destParentContainer = destContainer,
                    destName = child.name,
                    created = created,
                )
            }
        } else {
            val downloaded = downloadFile(webId, sourceUri)
            File(downloaded.path).inputStream().use { stream ->
                uploadFile(webId, destParentContainer, destName, downloaded.mimeType, stream) {}
            }
            created += destParentContainer.trimEnd('/') + "/" + destName
        }
    }

    private fun parentContainerUrl(uri: String): String =
        uri.trimEnd('/').substringBeforeLast('/') + "/"

    private fun copyNameFor(name: String, extension: String?, isContainer: Boolean): String {
        val base = name.trimEnd('/')
        return when {
            isContainer -> "${base}_copy"
            extension != null && base.endsWith(".$extension") ->
                "${base.dropLast(extension.length + 1)}_copy.$extension"

            else -> "${base}_copy"
        }
    }

    private suspend fun isBlobStillFresh(
        webId: String,
        fileUrl: String,
        cachedEtag: String?,
    ): Boolean {
        if (!networkMonitor.currentlyOnline()) return true
        if (cachedEtag == null) return false
        val metadata = runCatching { resourceManager.head(webId, fileUrl) }
            .getOrNull() ?: return true
        val currentEtag = (metadata as? SolidResult.Success<SolidMetadata>)?.value?.etag
            ?: return true
        return cachedEtag == currentEtag
    }

    private fun accessAwareError(error: SolidError, resourceUri: String): Throwable =
        if (error.code == SolidErrorCode.UNAUTHORIZED || error.code == SolidErrorCode.FORBIDDEN)
            ResourceAccessException.AccessDenied(resourceUri)
        else error.asException()

    private fun WacAllow.toResourceAccess(): ResourceAccess = ResourceAccess(
        canWrite = canWrite(),
        canControl = canControl(),
        publicCanRead = publicModes.any { it.equals("read", ignoreCase = true) },
        canAppend = canAppend(),
    )

    companion object {
        private const val MAX_PARALLEL_HEADS = 8
        private const val MAX_BLOB_CACHE_BYTES = 512L * 1024 * 1024
        private const val BLOB_DIR = "blob_cache"
        private const val OPEN_DIR = "open"

        private fun parseHttpDateMillis(raw: String): Long? = runCatching {
            Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC).parse(raw))
                .toEpochMilli()
        }.getOrNull()

        private fun parseIsoDateMillis(raw: String): Long? = runCatching {
            OffsetDateTime.parse(raw).toInstant().toEpochMilli()
        }.recoverCatching {
            Instant.parse(raw).toEpochMilli()
        }.getOrNull()
    }
}
