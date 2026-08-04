package com.erfangholami.solidshare.telemetry

/**
 * Installs the process-wide telemetry sink the Solid library reports through.
 *
 * Called once from `SolidShareApplication.onCreate`. The `gms` distribution installs a Firebase
 * sink; the `foss` distribution installs nothing, leaving the library's no-op sink in place so no
 * dependency on a proprietary SDK reaches the F-Droid build.
 */
interface TelemetryInstaller {

    fun install()
}
