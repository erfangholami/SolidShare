package com.erfangholami.solidshare.presentation.wallet

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.ui.graphics.vector.ImageVector
import com.erfangholami.solidshare.R
import com.erfangholami.solidshare.data.repo.sharing.SharedEntityTypes
import com.erfangholami.solidshare.data.repo.tickets.TicketsRepository
import com.erfangholami.solidshare.presentation.navigation.SharedTicketRoute
import com.erfangholami.solidshare.presentation.navigation.TicketSharingRoute
import com.erfangholami.solidshare.presentation.navigation.WalletRoute
import com.erfangholami.solidshare.presentation.sharing.HomeModuleCard
import com.erfangholami.solidshare.presentation.sharing.SharedEntityUi
import javax.inject.Inject

class TicketSharedEntityUi @Inject constructor(
    private val ticketsRepository: TicketsRepository,
) : SharedEntityUi {
    override val typeIri: String = SharedEntityTypes.TICKET
    override val icon: ImageVector = Icons.Filled.ConfirmationNumber
    override val kindLabelRes: Int = R.string.entity_kind_ticket

    override val homeCard: HomeModuleCard = HomeModuleCard(
        order = 2,
        icon = Icons.Filled.AccountBalanceWallet,
        titleRes = R.string.home_card_wallet_title,
        subtitleRes = R.string.home_card_wallet_subtitle,
        route = WalletRoute,
    )

    override fun receivedShareRoute(resourceUri: String, ownerWebId: String?): Any =
        SharedTicketRoute(resourceUri = resourceUri, ownerWebId = ownerWebId)

    override fun manageShareRoute(resourceUri: String): Any =
        TicketSharingRoute(target = resourceUri)

    override suspend fun resolveName(webId: String, resourceUri: String): String? =
        runCatching { ticketsRepository.getSharedTicket(webId, resourceUri).title }.getOrNull()
}
