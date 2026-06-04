package com.erfangholami.solidshare.data.repo.file

import android.net.Uri
import com.erfangholami.solidshare.domain.model.ContainerItem
import com.erfangholami.solidshare.domain.model.DownloadedFile
import com.erfangholami.solidshare.domain.model.ResourceAccess
import java.io.InputStream

interface FileRepository {
    suspend fun getContainerContents(
        webId: String,
        containerUrl: String,
        includeItemAccess: Boolean = false,
    ): List<ContainerItem>

    suspend fun downloadFile(
        webId: String,
        fileUrl: String
    ): DownloadedFile

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
}
