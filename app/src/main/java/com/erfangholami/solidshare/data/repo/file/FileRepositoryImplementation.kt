package com.erfangholami.solidshare.data.repo.file

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.collection.LruCache
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.DownloadedFile
import com.erfangholami.solidshare.domain.model.getResourceType
import com.pondersource.shared.domain.container.SolidContainer
import com.pondersource.shared.domain.network.HTTPAcceptType.OCTET_STREAM
import com.pondersource.shared.domain.network.SolidNetworkResponse
import com.pondersource.shared.domain.resource.SolidNonRDFResource
import com.pondersource.shared.domain.util.encodeUriString
import com.pondersource.shared.domain.util.getContentLength
import com.pondersource.solidandroidapi.Authenticator
import com.pondersource.solidandroidapi.SolidAccountResourceManager
import java.io.ByteArrayInputStream
import java.io.File


class FileRepositoryImplementation(
    private val context: Context,
    private val authenticator: Authenticator,
) : FileRepository {

    private val localCache = LruCache<String, DownloadedFile>(20)

    override suspend fun getContainerContents(
        webId: String,
        containerUrl: String
    ): List<ContainerItem> {
        val profile = authenticator.getProfile(webId)
        val resourceManager = SolidAccountResourceManager.getInstance(context, profile)
        val response =
            resourceManager.read(encodeUriString(containerUrl), SolidContainer::class.java)
        return when (response) {
            is SolidNetworkResponse.Success -> response.data.getContained().map { ref ->
                val identifier = ref.identifier
                val types = ref.types
                val isContainer = ref.isContainer() || ref.isContainerByUri()
                val rawName = identifier.trimEnd('/').substringAfterLast('/')
                val extension = if (!isContainer && '.' in rawName) {
                    rawName.substringAfterLast('.').lowercase().ifBlank { null }
                } else null
                val mimeType = extension?.let {
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
                    sizeBytes = null,
                    lastModified = null,
                )
            }

            is SolidNetworkResponse.Error ->
                throw Exception("HTTP ${response.errorCode}: ${response.errorMessage}")

            is SolidNetworkResponse.Exception -> throw response.exception
        }
    }

    override suspend fun downloadFile(webId: String, fileUrl: String): DownloadedFile {
        localCache[fileUrl]?.let {
            return it
        }
        val resourceManager = getResourceManager(webId)
        val response =
            resourceManager.read(encodeUriString(fileUrl), SolidNonRDFResource::class.java)
        return when (response) {
            is SolidNetworkResponse.Success -> {
                val resource = response.data
                val rawContentType = resource.getContentType().substringBefore(';').trim()
                val filename = fileUrl.trimEnd('/').substringAfterLast('/')
                val mimeType = rawContentType.ifBlank {
                    val ext = filename.substringAfterLast('.', "").lowercase()
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: OCTET_STREAM
                }
                val file = File(context.cacheDir, filename)
                resource.use { r ->
                    file.outputStream().use { output ->
                        r.getEntity().copyTo(output)
                    }
                }
                val downloadedFile = DownloadedFile(file = file, mimeType = mimeType)
                localCache.put(fileUrl, downloadedFile)
                downloadedFile
            }

            is SolidNetworkResponse.Error ->
                throw Exception("HTTP ${response.errorCode}: ${response.errorMessage}")

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
        val profile = authenticator.getProfile(webId)
        val resourceManager = SolidAccountResourceManager.getInstance(context, profile)
        val response =
            resourceManager.read(encodeUriString(fileUrl), SolidNonRDFResource::class.java)

        return when (response) {
            is SolidNetworkResponse.Success -> {
                val resource = response.data
                val contentLength = resource.getHeaders().getContentLength()

                onProgress(0)

                val destUri = insertIntoDownloads(fileName, mimeType)
                    ?: throw Exception("Could not create entry in Downloads")

                context.contentResolver.openOutputStream(destUri)?.use { output ->
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
                                    ((bytesWritten * 100) / contentLength).toInt().coerceIn(0, 99)
                                )
                            }
                        }
                    }
                }

                // Mark as complete in MediaStore (API 29+)
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
        content: ByteArray,
        onProgress: (Int) -> Unit,
    ) {
        onProgress(10)
        val profile = authenticator.getProfile(webId)
        val resourceManager = SolidAccountResourceManager.getInstance(context, profile)
        onProgress(20)

        val fileUrl = containerUrl.trimEnd('/') + "/$fileName"
        val resource = SolidNonRDFResource(
            encodeUriString(fileUrl),
            mimeType,
            ByteArrayInputStream(content),
        )
        onProgress(40)

        when (val response = resourceManager.create(resource)) {
            is SolidNetworkResponse.Success -> onProgress(100)
            is SolidNetworkResponse.Error ->
                throw Exception("HTTP ${response.errorCode}: ${response.errorMessage}")

            is SolidNetworkResponse.Exception -> throw response.exception
        }
    }

    override suspend fun createFolder(webId: String, containerUrl: String, folderName: String) {
        val profile = authenticator.getProfile(webId)
        val resourceManager = SolidAccountResourceManager.getInstance(context, profile)
        val folderUri = encodeUriString(containerUrl.trimEnd('/') + "/${folderName.trim()}/")
        val container = SolidContainer(folderUri)
        when (val response = resourceManager.create(container)) {
            is SolidNetworkResponse.Success -> Unit
            is SolidNetworkResponse.Error ->
                throw Exception("HTTP ${response.errorCode}: ${response.errorMessage}")

            is SolidNetworkResponse.Exception -> throw response.exception
        }
    }

    override suspend fun deleteResource(webId: String, resourceUrl: String, isContainer: Boolean) {
        val profile = authenticator.getProfile(webId)
        val resourceManager = SolidAccountResourceManager.getInstance(context, profile)
        val resourceUri = encodeUriString(resourceUrl)
        if (isContainer) {
            when (val response = resourceManager.deleteContainer(resourceUri)) {
                is SolidNetworkResponse.Success -> Unit
                is SolidNetworkResponse.Error ->
                    throw Exception("HTTP ${response.errorCode}: ${response.errorMessage}")

                is SolidNetworkResponse.Exception -> throw response.exception
            }
        } else {
            when (val response = resourceManager.delete(resourceUri)) {
                is SolidNetworkResponse.Success -> Unit
                is SolidNetworkResponse.Error ->
                    throw Exception("HTTP ${response.errorCode}: ${response.errorMessage}")

                is SolidNetworkResponse.Exception -> throw response.exception
            }
        }
    }
}
