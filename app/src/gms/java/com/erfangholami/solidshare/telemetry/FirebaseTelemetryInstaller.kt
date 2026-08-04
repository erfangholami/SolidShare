package com.erfangholami.solidshare.telemetry

import android.util.Log
import com.erfangholami.androidsolidservices.shared.telemetry.Telemetry
import com.erfangholami.solidshare.BuildConfig
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Telemetry"

@Singleton
class FirebaseTelemetryInstaller @Inject constructor(
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics,
    private val sink: FirebaseTelemetrySink,
) : TelemetryInstaller {

    override fun install() {
        val enabled = BuildConfig.TELEMETRY_ENABLED
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
        analytics.setAnalyticsCollectionEnabled(enabled)
        runCatching {
            FirebasePerformance.getInstance().isPerformanceCollectionEnabled = enabled
        }

        if (!enabled) {
            Log.i(TAG, "Telemetry collection is disabled for this build type")
            return
        }

        crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME)
        Telemetry.install(sink)
        Log.i(TAG, "Firebase Analytics, Crashlytics and Performance Monitoring installed")
    }
}
