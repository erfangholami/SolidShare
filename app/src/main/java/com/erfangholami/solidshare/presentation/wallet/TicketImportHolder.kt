package com.erfangholami.solidshare.presentation.wallet

import com.erfangholami.solidshare.domain.model.TicketFile
import javax.inject.Inject
import javax.inject.Singleton

class PendingImport(val bytes: ByteArray, val fileName: String?)

@Singleton
class TicketImportHolder @Inject constructor() {

    private var pendingArtifact: TicketFile? = null
    private var pendingImport: PendingImport? = null

    fun stash(artifact: TicketFile?) {
        pendingArtifact = artifact
    }

    fun consume(): TicketFile? = pendingArtifact.also { pendingArtifact = null }

    fun stashImport(bytes: ByteArray, fileName: String?) {
        pendingImport = PendingImport(bytes, fileName)
    }

    fun consumeImport(): PendingImport? = pendingImport.also { pendingImport = null }
}
