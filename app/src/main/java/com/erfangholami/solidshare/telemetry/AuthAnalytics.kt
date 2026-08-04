package com.erfangholami.solidshare.telemetry

import java.net.URI

/**
 * Records the auth-session events that explain why a session ended.
 *
 * The interface carries no monitoring dependency so that call sites compile in both distributions:
 * the `gms` build binds a Firebase-backed implementation, the `foss` build binds one that discards
 * everything. F-Droid rejects apps containing proprietary analytics, and its Tracking anti-feature
 * additionally covers any reporting that is not opt-in and off by default — so a future FOSS sink
 * has to be consent-gated rather than simply reinstated here.
 */
interface AuthAnalytics {

    fun loginSucceeded(issuerHost: String?, hasRefreshToken: Boolean)

    fun loginNoRefreshToken(issuerHost: String?)

    fun loginFailed()

    fun sessionExpired(issuerHost: String?, sessionError: String?)

    fun kickedToLogin(trigger: String)

    fun startupRouted(destination: String)

    fun accountsSnapshot(loggedInCount: Int, expiredCount: Int)

    companion object {
        fun issuerHost(oidcIssuer: String?): String? =
            oidcIssuer?.let { runCatching { URI(it).host }.getOrNull() }
    }
}
