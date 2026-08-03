package com.erfangholami.solidshare.di

import com.erfangholami.solidshare.presentation.contacts.ContactSharedEntityUi
import com.erfangholami.solidshare.presentation.sharing.SharedEntityUi
import com.erfangholami.solidshare.presentation.wallet.TicketSharedEntityUi
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

}
