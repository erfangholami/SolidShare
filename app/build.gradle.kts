import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.hilt.android)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// The one place the app's version is declared. A release is cut by bumping this and pushing the
// matching `v` tag; CI refuses to publish if the two disagree.
//
// It has to live in the source rather than come from the tag, because F-Droid builds from the tag
// on its own servers and never runs our workflow — whatever it checks out must already know its
// own version.
val appVersionName = "0.3.0"

// major * 10000 + minor * 100 + patch, so 1.2.3 is 10203. Deriving it means it can never drift
// from the name, and it stays monotonic for as long as minor and patch stay below 100 — both
// stores permanently reject a build whose versionCode did not increase.
val appVersionCode = appVersionName.split(".").let { parts ->
    require(parts.size == 3) {
        "appVersionName must be MAJOR.MINOR.PATCH, was \"$appVersionName\""
    }
    val (major, minor, patch) = parts.map {
        it.toIntOrNull() ?: throw GradleException(
            "appVersionName has a non-numeric part: \"$appVersionName\"",
        )
    }
    require(minor in 0..99 && patch in 0..99) {
        "minor and patch must each stay below 100 to keep versionCode ordered, was \"$appVersionName\""
    }
    major * 10000 + minor * 100 + patch
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.erfangholami.solidshare"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.erfangholami.solidshare"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appAuthRedirectScheme"] = namespace.toString()
    }

    // `foss` carries no Firebase or Google Play Services dependency, because F-Droid rejects apps
    // containing proprietary analytics outright. `gms` is what ships to Google Play.
    flavorDimensions += "distribution"
    productFlavors {
        create("gms") { dimension = "distribution" }
        create("foss") { dimension = "distribution" }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties["KEYSTORE_PATH"] as String)
                storePassword = keystoreProperties["KEYSTORE_PASSWORD"] as String
                keyAlias = keystoreProperties["KEY_ALIAS"] as String
                keyPassword = keystoreProperties["KEY_PASSWORD"] as String
            }
        }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            manifestPlaceholders["crashlyticsEnabled"] = false
            manifestPlaceholders["performanceEnabled"] = false
            buildConfigField("boolean", "TELEMETRY_ENABLED", "false")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["crashlyticsEnabled"] = true
            manifestPlaceholders["performanceEnabled"] = true
            buildConfigField("boolean", "TELEMETRY_ENABLED", "true")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes += setOf("META-INF/NOTICE.md", "META-INF/LICENSE.md", "META-INF/DEPENDENCIES")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                // Robolectric loads classes in its own sandbox classloader; without this jacoco
                // reports 0% for anything a Robolectric test exercises.
                it.extensions.configure(JacocoTaskExtension::class.java) {
                    isIncludeNoLocationClasses = true
                    excludes = listOf("jdk.internal.*")
                }
            }
        }
    }
}
kotlin {
    jvmToolchain(17)
}

composeCompiler {

}

// The google-services and Crashlytics plugins register a task per variant and cannot be applied
// per flavour, so they also run for `foss` — where google-services.json deliberately does not exist
// and there is no Firebase SDK to read the resources or consume the mapping upload. Disabling their
// foss tasks keeps them working for `gms` without leaking a Firebase config into the F-Droid build.
//
// The firebase-perf *plugin* is deliberately NOT applied at all. It instruments bytecode through
// AGP's Instrumentation API rather than a discrete task, so a gate like this cannot stop it: it
// rewrote AppAuth's `url.openConnection()` into `FirebasePerfUrlConnection` in the foss build too,
// which then crashed with NoClassDefFoundError because foss carries no Firebase. The SDK is still
// on gms for custom traces; only automatic HTTP/screen instrumentation is given up.
tasks
    .matching { task ->
        task.name.contains("Foss") &&
            (
                task.name.endsWith("GoogleServices") ||
                    task.name.contains("Crashlytics")
            )
    }.configureEach { enabled = false }

dependencies {

    implementation(libs.androidx.core.ktx)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.erfangholami.ass.solidandroidapi)

    // Firebase reaches only the gms flavour; foss must stay free of it to be F-Droid-eligible.
    "gmsImplementation"(platform(libs.firebase.bom))
    "gmsImplementation"(libs.firebase.analytics)
    "gmsImplementation"(libs.firebase.crashlytics)
    "gmsImplementation"(libs.firebase.performance)

    //Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)


    implementation(libs.google.android.material)

    //Compose
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui.google.fonts)

    //Hilt
    implementation(libs.google.hilt.android)
    ksp(libs.google.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    //QR
    implementation(libs.zxing.core)

    //CameraX (preview + lifecycle binding for the QR scanner)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Barcode decoding, every distribution. zxing-cpp is free software; ML Kit was dropped because
    // it is proprietary, pulls Google Play Services in (which F-Droid rejects) and added ~20 MB of
    // native libraries.
    implementation(libs.zxing.cpp.android)

    //Navigation
    implementation(libs.androidx.navigation.compose)

    //Local DataBase - Datasource
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)

    //Room offline cache (encrypted at rest via SQLCipher)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.sqlite)
    implementation(libs.zetetic.sqlcipher.android)

    //Test
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.core)

}

// Lets CI read the version without re-implementing the versionCode rule in shell, so the two can
// never disagree. Values are captured at configuration time to stay configuration-cache safe.
tasks.register("printVersion") {
    description = "Prints the app versionName and versionCode."
    val name = appVersionName
    val code = appVersionCode
    doLast {
        println("versionName=$name")
        println("versionCode=$code")
    }
}
