// Top-level build file where you can add configuration options common to all sub-projects/modules.
// The Crashlytics Gradle plugin is Play-only tooling, so its Maven coordinate stays out of the
// Gradle files that F-Droid's scanner reads: Play invocations supply it through the two
// solidshare.play.* Gradle properties (see .github/workflows/release.yml), and a build without
// them adds nothing here.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        providers.gradleProperty("solidshare.play.crashReportingPlugin").orNull?.let {
            classpath(it)
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.devtools.ksp) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.google.hilt.android) apply false
    alias(libs.plugins.jetbrains.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
}