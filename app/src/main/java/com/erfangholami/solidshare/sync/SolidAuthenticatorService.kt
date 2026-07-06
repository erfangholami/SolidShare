package com.erfangholami.solidshare.sync

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.accounts.NetworkErrorException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder

class SolidAccountAuthenticator(context: Context) : AbstractAccountAuthenticator(context) {

    override fun editProperties(
        response: AccountAuthenticatorResponse?,
        accountType: String?,
    ): Bundle? = null

    @Throws(NetworkErrorException::class)
    override fun addAccount(
        response: AccountAuthenticatorResponse?,
        accountType: String?,
        authTokenType: String?,
        requiredFeatures: Array<out String>?,
        options: Bundle?,
    ): Bundle = Bundle().apply {
        putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION)
        putString(AccountManager.KEY_ERROR_MESSAGE, "Accounts are managed by Solid Share")
    }

    @Throws(NetworkErrorException::class)
    override fun confirmCredentials(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        options: Bundle?,
    ): Bundle? = null

    @Throws(NetworkErrorException::class)
    override fun getAuthToken(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        authTokenType: String?,
        options: Bundle?,
    ): Bundle = Bundle().apply {
        putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION)
        putString(AccountManager.KEY_ERROR_MESSAGE, "Tokens are managed by Solid Share")
    }

    override fun getAuthTokenLabel(authTokenType: String?): String? = null

    @Throws(NetworkErrorException::class)
    override fun updateCredentials(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        authTokenType: String?,
        options: Bundle?,
    ): Bundle? = null

    @Throws(NetworkErrorException::class)
    override fun hasFeatures(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        features: Array<out String>?,
    ): Bundle = Bundle().apply {
        putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false)
    }
}

class SolidAuthenticatorService : Service() {

    private lateinit var authenticator: SolidAccountAuthenticator

    override fun onCreate() {
        super.onCreate()
        authenticator = SolidAccountAuthenticator(this)
    }

    override fun onBind(intent: Intent?): IBinder = authenticator.iBinder
}
