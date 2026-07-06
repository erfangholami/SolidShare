package com.erfangholami.solidshare.sync

import android.accounts.Account
import android.app.Service
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

class ContactsSyncAdapter(
    context: Context,
    private val syncEngine: ContactsSyncEngine,
) : AbstractThreadedSyncAdapter(context, true) {

    override fun onPerformSync(
        account: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        syncResult: SyncResult,
    ) {
        runBlocking {
            runCatching { syncEngine.sync(account.name) }
                .onFailure { syncResult.stats.numIoExceptions++ }
        }
    }
}

@AndroidEntryPoint
class ContactsSyncService : Service() {

    @Inject
    lateinit var syncEngine: ContactsSyncEngine

    private var syncAdapter: ContactsSyncAdapter? = null

    override fun onCreate() {
        super.onCreate()
        syncAdapter = ContactsSyncAdapter(applicationContext, syncEngine)
    }

    override fun onBind(intent: Intent?): IBinder? = syncAdapter?.syncAdapterBinder
}
