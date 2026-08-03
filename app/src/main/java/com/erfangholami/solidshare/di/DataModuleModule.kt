package com.erfangholami.solidshare.di

import com.erfangholami.solidshare.data.repo.contacts.ContactsRepository
import com.erfangholami.solidshare.data.repo.datamodule.DataModuleLifecycle
import com.erfangholami.solidshare.data.repo.outbox.OutboxTrigger
import com.erfangholami.solidshare.data.repo.tickets.TicketsRepository
import com.erfangholami.solidshare.worker.WorkManagerOutboxTrigger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface DataModuleModule {

    @Binds
    fun bindOutboxTrigger(implementation: WorkManagerOutboxTrigger): OutboxTrigger

    @Binds
    @IntoSet
    fun bindContactsLifecycle(repository: ContactsRepository): DataModuleLifecycle

    @Binds
    @IntoSet
    fun bindTicketsLifecycle(repository: TicketsRepository): DataModuleLifecycle
}
