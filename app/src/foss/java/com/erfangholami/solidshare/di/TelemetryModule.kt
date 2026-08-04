package com.erfangholami.solidshare.di

import com.erfangholami.solidshare.telemetry.AuthAnalytics
import com.erfangholami.solidshare.telemetry.NoAuthAnalytics
import com.erfangholami.solidshare.telemetry.NoTelemetryInstaller
import com.erfangholami.solidshare.telemetry.TelemetryInstaller
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface TelemetryModule {

    @Binds
    fun bindAuthAnalytics(implementation: NoAuthAnalytics): AuthAnalytics

    @Binds
    fun bindTelemetryInstaller(implementation: NoTelemetryInstaller): TelemetryInstaller
}
