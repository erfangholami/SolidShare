package com.erfangholami.solidshare.data.repo.file

import android.net.Uri
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.DownloadedFile
import com.erfangholami.solidshare.domain.model.ResourceMeta
import com.erfangholami.solidshare.domain.model.ResourceAccess
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

interface FileRepository {
    fun observeContainer(
        webId: String,
        containerUrl: String,
    ): Flow<List<ContainerItem>>

    suspend fun refreshContainer(
        webId: String,
        containerUrl: String,
        includeItemAccess: Boolean = false,
    )

    suspend fun getCachedContainer(
        webId: String,
        containerUrl: String,
    ): List<ContainerItem>

    suspend fun cacheContainer(
        webId: String,
        containerUrl: String,
        items: List<ContainerItem>,
    )

    suspend fun lastCachedAt(
        webId: String,
        containerUrl: String,
    ): Long?

    suspend fun getContainerContents(
        webId: String,
        containerUrl: String,
        includeItemAccess: Boolean = false,
    ): List<ContainerItem>

    suspend fun getContainerItemCount(
        webId: String,
        containerUrl: String,
    ): Int

    suspend fun getResourceMeta(
        webId: String,
        resourceUri: String,
    ): ResourceMeta

    suspend fun getResourceCreatedTime(
        webId: String,
        item: ContainerItem,
    ): Long?

    suspend fun downloadFile(
        webId: String,
        fileUrl: String
    ): DownloadedFile

    fun observeAvailableOffline(
        webId: String,
    ): Flow<List<String>>

    fun observePendingUris(
        webId: String,
    ): Flow<List<String>>

    fun observeErrorUris(
        webId: String,
    ): Flow<List<String>>

    suspend fun pinOffline(
        webId: String,
        fileUrl: String,
    )

    suspend fun unpinOffline(
        webId: String,
        fileUrl: String,
    )

    suspend fun probeAccess(
        webId: String,
        resourceUri: String,
    ): ResourceAccess

    suspend fun downloadToDevice(
        webId: String,
        fileUrl: String,
        fileName: String,
        mimeType: String,
        onProgress: (Int) -> Unit,
    ): Uri

    suspend fun uploadFile(
        webId: String,
        containerUrl: String,
        fileName: String,
        mimeType: String,
        inputStream: InputStream,
        onProgress: (Int) -> Unit,
    )

    suspend fun createFolder(
        webId: String,
        containerUrl: String,
        folderName: String
    )

    suspend fun deleteResource(
        webId: String,
        resourceUrl: String,
        isContainer: Boolean
    )

    suspend fun duplicateResource(
        webId: String,
        item: ContainerItem,
    ): List<String>

    suspend fun clearCacheForWebId(
        webId: String,
    )
}
