package com.erfangholami.solidshare.data.repo.file

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.collection.LruCache
import com.erfangholami.androidsolidservices.api.resource.SolidResourceManager
import com.erfangholami.androidsolidservices.shared.http.HTTPAcceptType.OCTET_STREAM
import com.erfangholami.androidsolidservices.shared.http.SolidNetworkResponse
import com.erfangholami.androidsolidservices.shared.model.access.WacAllow
import com.erfangholami.androidsolidservices.shared.model.resource.SolidContainer
import com.erfangholami.androidsolidservices.shared.model.resource.SolidMetadata
import com.erfangholami.androidsolidservices.shared.model.resource.SolidNonRDFResource
import com.erfangholami.androidsolidservices.shared.model.resource.SolidRDFResource
import com.erfangholami.androidsolidservices.shared.util.encodeUriString
import com.erfangholami.androidsolidservices.shared.util.getContentLength
import com.erfangholami.androidsolidservices.shared.util.getETag
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.DownloadedFile
import com.erfangholami.solidshare.domain.model.ResourceAccess
import com.erfangholami.solidshare.domain.model.ResourceMeta
import com.erfangholami.solidshare.domain.model.getResourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject


class FileRepositoryImplementation @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val resourceManager: SolidResourceManager,
) : FileRepository {

    private val localCache = LruCache<String, DownloadedFile>(20)

    override suspend fun getContainerContents(
        webId: String,
        containerUrl: String,
        includeItemAccess: Boolean,
    ): List<ContainerItem> {
        val response =
            resourceManager.read(webId, encodeUriString(containerUrl), SolidContainer::class.java)
        val refs = when (response) {
            is SolidNetworkResponse.Success -> response.data.getContained()
            is SolidNetworkResponse.Error ->
                throw accessAwareError(response.errorCode, response.errorMessage, containerUrl)

            is SolidNetworkResponse.Exception -> throw response.exception
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
                            runCatching { resourceManager.head(webId, encodeUriString(identifier)) }
                                .getOrNull()
                                ?.let { res -> if (res is SolidNetworkResponse.Success) res.data else null }
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
            encodeUriString(containerUrl),
            SolidContainer::class.java,
        ) as? SolidNetworkResponse.Success)?.data ?: return 0
        return container.getContained().size
    }

    override suspend fun getResourceMeta(
        webId: String,
        resourceUri: String,
    ): ResourceMeta {
        val uri = encodeUriString(resourceUri)
        if (resourceUri.endsWith("/")) {
            val container = (resourceManager.read(webId, uri, SolidContainer::class.java)
                    as? SolidNetworkResponse.Success)?.data
            return ResourceMeta(
                sizeBytes = null,
                lastModified = container?.getLastModified(),
                itemCount = container?.getContained()?.size,
            )
        }
        val metadata = (resourceManager.head(webId, uri)
                as? SolidNetworkResponse.Success)?.data
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
            encodeUriString(item.identifier),
            SolidRDFResource::class.java,
        ) as? SolidNetworkResponse.Success)?.data?.getCreatedTime()
    }

    private fun isRdfMimeType(mimeType: String?): Boolean {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase() ?: return false
        return mime == "text/turtle" || mime == "application/ld+json" ||
                mime == "application/rdf+xml" || mime == "application/n-triples" ||
                mime == "application/n-quads" || mime == "text/n3" || mime == "application/trig"
    }

    override suspend fun downloadFile(webId: String, fileUrl: String): DownloadedFile {
        localCache[fileUrl]?.let { cached ->
            if (isCachedFileStillFresh(webId, fileUrl, cached)) return cached
            localCache.remove(fileUrl)
        }
        val response =
            resourceManager.read(webId, encodeUriString(fileUrl), SolidNonRDFResource::class.java)
        return when (response) {
            is SolidNetworkResponse.Success -> {
                val resource = response.data
                val rawContentType = resource.getContentType().substringBefore(';').trim()
                val filename = fileUrl.trimEnd('/').substringAfterLast('/')
                val mimeType = rawContentType.ifBlank {
                    val ext = filename.substringAfterLast('.', "").lowercase()
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: OCTET_STREAM
                }
                val etag = resource.getHeaders().getETag()
                val file = File(context.cacheDir, filename)
                resource.use { r ->
                    file.outputStream().use { output ->
                        r.getEntity().copyTo(output)
                    }
                }
                val downloadedFile = DownloadedFile(
                    path = file.absolutePath,
                    mimeType = mimeType,
                    etag = etag,
                )
                localCache.put(fileUrl, downloadedFile)
                downloadedFile
            }

            is SolidNetworkResponse.Error ->
                throw accessAwareError(response.errorCode, response.errorMessage, fileUrl)

            is SolidNetworkResponse.Exception -> throw response.exception
        }
    }

    override suspend fun probeAccess(webId: String, resourceUri: String): ResourceAccess {
        return when (val response = resourceManager.head(webId, encodeUriString(resourceUri))) {
            is SolidNetworkResponse.Success ->
                response.data.wacAllow?.toResourceAccess() ?: ResourceAccess.READ_ONLY

            is SolidNetworkResponse.Error ->
                throw accessAwareError(response.errorCode, response.errorMessage, resourceUri)

            is SolidNetworkResponse.Exception -> throw response.exception
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
            resourceManager.read(webId, encodeUriString(fileUrl), SolidNonRDFResource::class.java)

        return when (response) {
            is SolidNetworkResponse.Success -> {
                val resource = response.data
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

            is SolidNetworkResponse.Error ->
                throw Exception("HTTP ${response.errorCode}: ${response.errorMessage}")

            is SolidNetworkResponse.Exception -> throw response.exception
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
            encodeUriString(fileUrl),
            mimeType,
            inputStream,
        )
        onProgress(40)

        when (
            val response =
                resourceManager.createInContainer(webId, encodeUriString(containerUrl), resource)
        ) {
            is SolidNetworkResponse.Success -> {
                localCache.remove(fileUrl)
                onProgress(100)
            }
            is SolidNetworkResponse.Error ->
                throw Exception("HTTP ${response.errorCode}: ${response.errorMessage}")

            is SolidNetworkResponse.Exception -> throw response.exception
        }
    }

    override suspend fun createFolder(webId: String, containerUrl: String, folderName: String) {
        val folderUri = encodeUriString(containerUrl.trimEnd('/') + "/${folderName.trim()}/")
        val container = SolidContainer(folderUri)
        when (
            val response =
                resourceManager.createInContainer(webId, encodeUriString(containerUrl), container)
        ) {
            is SolidNetworkResponse.Success -> Unit
            is SolidNetworkResponse.Error ->
                throw Exception("HTTP ${response.errorCode}: ${response.errorMessage}")

            is SolidNetworkResponse.Exception -> throw response.exception
        }
    }

    override suspend fun deleteResource(webId: String, resourceUrl: String, isContainer: Boolean) {
        val resourceUri = encodeUriString(resourceUrl)
        when (val response = resourceManager.delete(webId, resourceUri)) {
            is SolidNetworkResponse.Success -> localCache.remove(resourceUrl)
            is SolidNetworkResponse.Error ->
                throw Exception("HTTP ${response.errorCode}: ${response.errorMessage}")

            is SolidNetworkResponse.Exception -> throw response.exception
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

    private suspend fun isCachedFileStillFresh(
        webId: String,
        fileUrl: String,
        cached: DownloadedFile,
    ): Boolean {
        if (!File(cached.path).exists()) return false
        val cachedEtag = cached.etag ?: return false
        val metadata = runCatching { resourceManager.head(webId, encodeUriString(fileUrl)) }
            .getOrNull() ?: return true
        val currentEtag = (metadata as? SolidNetworkResponse.Success<SolidMetadata>)?.data?.etag
            ?: return true
        return cachedEtag == currentEtag
    }

    private fun accessAwareError(code: Int, message: String, resourceUri: String): Throwable =
        if (code == 401 || code == 403) ResourceAccessException.AccessDenied(resourceUri)
        else Exception("HTTP $code: $message")

    private fun WacAllow.toResourceAccess(): ResourceAccess = ResourceAccess(
        canWrite = canWrite(),
        canControl = canControl(),
        publicCanRead = publicModes.any { it.equals("read", ignoreCase = true) },
        canAppend = canAppend(),
    )

    companion object {
        private const val MAX_PARALLEL_HEADS = 8

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
