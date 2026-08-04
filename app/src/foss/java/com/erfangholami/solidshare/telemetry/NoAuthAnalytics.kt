package com.erfangholami.solidshare.telemetry

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discards every auth event.
 *
 * The FOSS distribution ships no analytics dependency at all, so the events are dropped at the
 * binding rather than being buffered or written anywhere. Nothing leaves the device.
 */
@Singleton
class NoAuthAnalytics @Inject constructor() : AuthAnalytics {

    override fun loginSucceeded(issuerHost: String?, hasRefreshToken: Boolean) = Unit

    override fun loginNoRefreshToken(issuerHost: String?) = Unit

    override fun loginFailed() = Unit

    override fun sessionExpired(issuerHost: String?, sessionError: String?) = Unit

    override fun kickedToLogin(trigger: String) = Unit

    override fun startupRouted(destination: String) = Unit

    override fun accountsSnapshot(loggedInCount: Int, expiredCount: Int) = Unit
}
