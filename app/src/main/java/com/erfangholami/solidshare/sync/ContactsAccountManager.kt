package com.erfangholami.solidshare.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsAccountManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val accountManager: AccountManager get() = AccountManager.get(context)

    fun reconcile(webIds: List<String>) {
        val existing = accountManager.getAccountsByType(SolidAccounts.ACCOUNT_TYPE)
        existing
            .filter { it.name !in webIds }
            .forEach { accountManager.removeAccountExplicitly(it) }
        webIds.forEach { webId ->
            val account = Account(webId, SolidAccounts.ACCOUNT_TYPE)
            val present = existing.any { it.name == webId } ||
                    accountManager.addAccountExplicitly(account, null, null)
            if (present) {
                ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1)
                ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true)
                ContentResolver.addPeriodicSync(
                    account,
                    ContactsContract.AUTHORITY,
                    Bundle.EMPTY,
                    TimeUnit.HOURS.toSeconds(4),
                )
            }
        }
    }

    fun requestSync(webId: String) {
        val account = Account(webId, SolidAccounts.ACCOUNT_TYPE)
        if (accountManager.getAccountsByType(SolidAccounts.ACCOUNT_TYPE).none { it == account }) {
            return
        }
        val extras = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }
        ContentResolver.requestSync(account, ContactsContract.AUTHORITY, extras)
    }
}
