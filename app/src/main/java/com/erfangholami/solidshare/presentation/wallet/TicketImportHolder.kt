package com.erfangholami.solidshare.presentation.wallet

import com.erfangholami.solidshare.domain.model.TicketFile
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PendingImport {
    class File(val bytes: ByteArray, val fileName: String?) : PendingImport
    class Link(val url: String) : PendingImport
}

@Singleton
class TicketImportHolder @Inject constructor() {

    private var pendingArtifact: TicketFile? = null
    private var pendingImport: PendingImport? = null

    fun stash(artifact: TicketFile?) {
        pendingArtifact = artifact
    }

    fun consume(): TicketFile? = pendingArtifact.also { pendingArtifact = null }

    fun stashImport(bytes: ByteArray, fileName: String?) {
        pendingImport = PendingImport.File(bytes, fileName)
    }

    fun stashLink(url: String) {
        pendingImport = PendingImport.Link(url)
    }

    fun consumeImport(): PendingImport? = pendingImport.also { pendingImport = null }
}
