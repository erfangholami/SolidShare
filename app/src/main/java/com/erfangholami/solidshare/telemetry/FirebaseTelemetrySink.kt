package com.erfangholami.solidshare.telemetry

import android.os.Bundle
import android.os.SystemClock
import com.erfangholami.androidsolidservices.shared.telemetry.TelemetryNetworkSpan
import com.erfangholami.androidsolidservices.shared.telemetry.TelemetrySink
import com.erfangholami.androidsolidservices.shared.telemetry.TelemetrySpan
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseTelemetrySink @Inject constructor(
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics,
) : TelemetrySink {

    override fun startSpan(name: String): TelemetrySpan = EventSpan(name)

    override fun startNetworkSpan(url: String, method: String): TelemetryNetworkSpan =
        NetworkBreadcrumbSpan(url, method)

    override fun recordException(throwable: Throwable, attributes: Map<String, String>) {
        attributes.forEach { (key, value) ->
            crashlytics.setCustomKey(firebaseName(key), value.take(MAX_VALUE_LENGTH))
        }
        crashlytics.recordException(throwable)
        val params = Bundle().apply {
            putString("error_type", throwable.javaClass.simpleName.take(MAX_VALUE_LENGTH))
            throwable.message?.let { putString("error_message", it.take(MAX_VALUE_LENGTH)) }
            attributes.forEach { (key, value) ->
                putString(firebaseName(key), value.take(MAX_VALUE_LENGTH))
            }
        }
        analytics.logEvent("solid_nonfatal", params)
    }

    override fun log(message: String) {
        crashlytics.log(message)
    }

    override fun setKey(key: String, value: String) {
        crashlytics.setCustomKey(firebaseName(key), value.take(MAX_VALUE_LENGTH))
    }

    private inner class EventSpan(name: String) : TelemetrySpan {
        private val eventName = firebaseName(name)
        private val startedAt = SystemClock.elapsedRealtime()
        private val attributes = ConcurrentHashMap<String, String>()
        private val metrics = ConcurrentHashMap<String, Long>()

        @Volatile
        private var stopped = false

        override fun putAttribute(name: String, value: String) {
            if (!stopped) attributes[firebaseName(name)] = value.take(MAX_VALUE_LENGTH)
        }

        override fun putMetric(name: String, value: Long) {
            if (!stopped) metrics[firebaseName(name)] = value
        }

        override fun stop() {
            if (stopped) return
            stopped = true
            val durationMs = SystemClock.elapsedRealtime() - startedAt
            val params = Bundle().apply {
                putLong("duration_ms", durationMs)
                attributes.forEach { (key, value) -> putString(key, value) }
                metrics.forEach { (key, value) -> putLong(key, value) }
            }
            analytics.logEvent(eventName, params)
            crashlytics.log(
                "$eventName " +
                    attributes.entries.joinToString(" ") { "${it.key}=${it.value}" } +
                    " ${durationMs}ms",
            )
        }
    }

    private inner class NetworkBreadcrumbSpan(
        private val origin: String,
        private val method: String,
    ) : TelemetryNetworkSpan {
        private val startedAt = SystemClock.elapsedRealtime()

        @Volatile
        private var responseCode = 0

        @Volatile
        private var stopped = false

        override fun setResponseCode(code: Int) {
            responseCode = code
        }

        override fun setRequestPayloadSize(bytes: Long) = Unit

        override fun setResponsePayloadSize(bytes: Long) = Unit

        override fun setResponseContentType(contentType: String?) = Unit

        override fun putAttribute(name: String, value: String) = Unit

        override fun stop() {
            if (stopped) return
            stopped = true
            if (responseCode in 200..399) return
            val durationMs = SystemClock.elapsedRealtime() - startedAt
            val code = if (responseCode == 0) "no-response" else responseCode.toString()
            crashlytics.log("http $method $origin -> $code ${durationMs}ms")
        }
    }
}

private const val MAX_NAME_LENGTH = 40
private const val MAX_VALUE_LENGTH = 100

private fun firebaseName(raw: String): String = raw
    .map { if (it.isLetterOrDigit() || it == '_') it else '_' }
    .joinToString("")
    .dropWhile { !it.isLetter() }
    .take(MAX_NAME_LENGTH)
    .ifEmpty { "solid_event" }
