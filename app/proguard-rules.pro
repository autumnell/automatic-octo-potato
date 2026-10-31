# Release build rules. Room, WorkManager, ML Kit, CameraX, Coil, OkHttp/Okio and
# kotlinx.serialization all ship their own consumer rules inside their AARs/JARs; the rules
# below only cover this app's code and belt-and-braces cases where a wrong guess would lose data.

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# kotlinx.serialization: the rules from its README, verbatim (the library jar ships them too).
# Backups, sorting prefs and caches are JSON: field names come from the generated serializers,
# so they stay the same as in debug builds.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mtgscanner.**$$serializer { *; }

# WorkManager stores each queued job's worker class NAME in its own database. A periodic job
# queued by an older build (weekly backup, price alerts, offline scans) must still find its class
# after an update, so worker names are never renamed. (work-runtime keeps them too.)
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Room: the generated database is created by name (room-runtime ships this rule too).
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# MediaPipe Tasks (art model): its native code calls back into Java classes and reads protobuf messages
# by reflection, and the AAR ships no consumer rules for that.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn com.google.auto.value.**
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**

# OpenCV (card corners): its native code builds Java objects (Mat, CvException) by class name.
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Optional dependencies some libraries mention but don't need on Android.
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**

# ONNX Runtime (knowing cards by picture): its native code finds Java classes and fields by name.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
