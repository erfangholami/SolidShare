package com.erfangholami.solidshare.telemetry

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs no telemetry sink.
 *
 * The Solid library keeps its default no-op sink, so spans, breadcrumbs and non-fatals are built
 * nowhere and sent nowhere. This is what keeps the FOSS build free of the proprietary SDKs that
 * F-Droid's inclusion policy rejects, and free of anything Exodus Privacy would classify as a
 * tracker.
 */
@Singleton
class NoTelemetryInstaller @Inject constructor() : TelemetryInstaller {

    override fun install() = Unit
}
