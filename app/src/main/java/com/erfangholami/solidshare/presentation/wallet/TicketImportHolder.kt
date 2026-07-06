package com.erfangholami.solidshare.presentation.wallet

import com.erfangholami.solidshare.domain.model.TicketFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TicketImportHolder @Inject constructor() {

    private var pendingArtifact: TicketFile? = null

    fun stash(artifact: TicketFile?) {
        pendingArtifact = artifact
    }

    fun consume(): TicketFile? = pendingArtifact.also { pendingArtifact = null }
}
