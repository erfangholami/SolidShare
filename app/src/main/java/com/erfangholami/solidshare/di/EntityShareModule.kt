package com.erfangholami.solidshare.di

import com.erfangholami.solidshare.presentation.contacts.ContactReceiverPickerContributor
import com.erfangholami.solidshare.presentation.contacts.ContactSharedEntityUi
import com.erfangholami.solidshare.presentation.contacts.ContactsNavGraph
import com.erfangholami.solidshare.presentation.navigation.NavGraphContributor
import com.erfangholami.solidshare.presentation.sharing.ReceiverPickerContributor
import com.erfangholami.solidshare.presentation.sharing.ScanContributor
import com.erfangholami.solidshare.presentation.sharing.SharedEntityUi
import com.erfangholami.solidshare.presentation.wallet.TicketScanContributor
import com.erfangholami.solidshare.presentation.wallet.TicketSharedEntityUi
import com.erfangholami.solidshare.presentation.wallet.WalletNavGraph
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface EntityShareModule {

    @Binds
    @IntoSet
    fun ticketSharedEntityUi(implementation: TicketSharedEntityUi): SharedEntityUi

    @Binds
    @IntoSet
    fun contactSharedEntityUi(implementation: ContactSharedEntityUi): SharedEntityUi

    @Binds
    @IntoSet
    fun contactReceiverPicker(
        implementation: ContactReceiverPickerContributor,
    ): ReceiverPickerContributor

    @Binds
    @IntoSet
    fun contactsNavGraph(implementation: ContactsNavGraph): NavGraphContributor

    @Binds
    @IntoSet
    fun walletNavGraph(implementation: WalletNavGraph): NavGraphContributor

    @Binds
    @IntoSet
    fun ticketScanContributor(implementation: TicketScanContributor): ScanContributor
}
