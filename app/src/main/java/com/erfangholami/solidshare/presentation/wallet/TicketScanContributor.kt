package com.erfangholami.solidshare.presentation.wallet

import com.erfangholami.solidshare.data.repo.tickets.TicketsRepository
import com.erfangholami.solidshare.presentation.navigation.TicketEditRoute
import com.erfangholami.solidshare.presentation.navigation.TicketImportRoute
import com.erfangholami.solidshare.presentation.sharing.ScanContributor
import javax.inject.Inject

class TicketScanContributor @Inject constructor(
    private val ticketsRepository: TicketsRepository,
    private val importHolder: TicketImportHolder,
) : ScanContributor {

    override fun classify(raw: String): Any? =
        ticketsRepository.parseTicketQr(raw)?.let { TicketEditRoute(draft = it) }

    override fun classifyContent(bytes: ByteArray, fileName: String?): Any {
        importHolder.stashImport(bytes, fileName)
        return TicketImportRoute
    }
}
