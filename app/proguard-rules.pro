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
# manifest meta-data; firebase-components' consumer rule keeps only their class NAMES, and
# under R8 full mode the no-arg constructors that reflective instantiation needs get
# stripped, NPE-ing BarcodeScanning.getClient() on a null component — member-keeping the
# registrars is the fix. The rest of ML Kit's reflective surface ships in its own consumer
# rules (bundled-proto <fields>, native method names, @UsedBy*), and
# ThickBarcodeScannerCreator is a direct code reference, so no package blankets are needed.
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }

# The telemetry protos under gms.internal.mlkit_vision_barcode use protobuf-lite field
# reflection but, unlike the bundled package, ship no consumer <fields> rule anywhere in
# the graph. Keep just the fields; the classes stay shrinkable and renamable.
-keepclassmembers class com.google.android.gms.internal.mlkit_vision_barcode.** {
    <fields>;
}

# kotlinx.serialization ≥ 1.8 bundles the complete R8 full-mode ruleset (Companion field,
# Companion.serializer(), object INSTANCE.serializer(), the $$serializer descriptor
# workaround), which covers the app's @Serializable models and type-safe Navigation
# routes — the $$serializer classes themselves are reached through code, so no app-side
# serialization keeps are needed here.

# Hilt instantiates these Workers reflectively through its WorkerFactory; keep the
# injected constructors.
-keep @androidx.hilt.work.HiltWorker class com.erfangholami.solidshare.** {
    public <init>(...);
}