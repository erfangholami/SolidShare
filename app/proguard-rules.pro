# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# The AndroidSolidServices AAR (api + shared) ships its own consumer ProGuard rules for
# everything it needs reflectively — the resource-model constructors, Parcelable CREATORs,
# the JSON-LD JSON-P provider, and the full JJWT io.jsonwebtoken.impl.** tree used for DPoP
# signing — so no app-side keeps for that library (or its transitive JJWT) are required here.

# ML Kit barcode scanning (com.google.mlkit:barcode-scanning, which pulls in
# play-services-mlkit-barcode-scanning) builds its scanner through a Firebase-components
# registry. The registrars are discovered reflectively from MlKitComponentDiscoveryService
# manifest meta-data, and the registered components are tagged @KeepForSdk — which GMS's
# consumer rules do NOT member-keep — so under R8 full mode they get optimized away and
# BarcodeScanning.getClient() NPEs on a null component. Keep the registrars and the ML Kit
# barcode surface.
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode_bundled.** { *; }

# kotlinx.serialization: the runtime bundles the core rules, but pin the generated
# serializers for the app's @Serializable models and type-safe Navigation routes so
# (de)serialization keeps working under R8 full mode. The classes themselves stay
# shrinkable and obfuscatable.
-keepclassmembers @kotlinx.serialization.Serializable class com.erfangholami.solidshare.** {
    static **$Companion Companion;
}
-keep class com.erfangholami.solidshare.**$$serializer { *; }

# Hilt instantiates these Workers reflectively through its WorkerFactory; keep the
# injected constructors.
-keep @androidx.hilt.work.HiltWorker class com.erfangholami.solidshare.** {
    public <init>(...);
}