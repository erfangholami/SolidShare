package com.erfangholami.solidshare.data.repo.outbox

import android.net.Uri
import com.erfangholami.solidshare.domain.model.ContainerItem

interface OutboxRepository {

    suspend fun enqueueUpload(
        webId: String,
        containerUrl: String,
        sourceUri: Uri,
        fileName: String,
        mimeType: String,
    )

    suspend fun enqueueCreateFolder(
        webId: String,
        containerUrl: String,
        folderName: String,
    )

    suspend fun enqueueDelete(
        webId: String,
        resourceUri: String,
        isContainer: Boolean,
    )

    suspend fun enqueueDuplicate(
        webId: String,
        item: ContainerItem,
    )

    suspend fun drain()

    suspend fun hasUnfinishedWork(): Boolean

    suspend fun clearForWebId(webId: String)
}

class OutboxException(message: String, val terminal: Boolean) : Exception(message)
