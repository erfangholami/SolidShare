package com.erfangholami.solidshare.telemetry

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthAnalytics @Inject constructor(
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics,
) {

    fun loginSucceeded(issuerHost: String?, hasRefreshToken: Boolean) {
        analytics.logEvent("solid_login_success") {
            putString("issuer", issuerHost ?: "unknown")
            putString("refresh_token", if (hasRefreshToken) "present" else "absent")
        }
        crashlytics.setCustomKey("last_login_issuer", issuerHost ?: "unknown")
        crashlytics.log("login success issuer=$issuerHost refreshToken=$hasRefreshToken")
    }

    fun loginNoRefreshToken(issuerHost: String?) {
        analytics.logEvent("solid_login_no_refresh") {
            putString("issuer", issuerHost ?: "unknown")
        }
        crashlytics.log("login yielded no refresh token issuer=$issuerHost")
    }

    fun loginFailed() {
        analytics.logEvent("solid_login_failed") {}
        crashlytics.log("login failed or was abandoned")
    }

    fun sessionExpired(issuerHost: String?, sessionError: String?) {
        val errorCode = sessionError?.substringBefore(":")?.trim()?.take(40) ?: "unknown"
        analytics.logEvent("solid_session_expired") {
            putString("issuer", issuerHost ?: "unknown")
            putString("error_code", errorCode)
        }
        crashlytics.setCustomKey("last_session_error", errorCode)
        crashlytics.log("session expired issuer=$issuerHost error=$sessionError")
    }

    fun kickedToLogin(trigger: String) {
        analytics.logEvent("solid_kicked_to_login") {
            putString("trigger", trigger)
        }
        crashlytics.log("navigated to login trigger=$trigger")
    }

    fun startupRouted(destination: String) {
        analytics.logEvent("solid_startup_route") {
            putString("destination", destination)
        }
        crashlytics.log("startup routed to $destination")
    }

    fun accountsSnapshot(loggedInCount: Int, expiredCount: Int) {
        crashlytics.setCustomKey("accounts_logged_in", loggedInCount.toString())
        crashlytics.setCustomKey("accounts_expired", expiredCount.toString())
    }

    private inline fun FirebaseAnalytics.logEvent(name: String, params: Bundle.() -> Unit) {
        logEvent(name, Bundle().apply(params))
    }

    companion object {
        fun issuerHost(oidcIssuer: String?): String? =
            oidcIssuer?.let { runCatching { URI(it).host }.getOrNull() }
    }
}
