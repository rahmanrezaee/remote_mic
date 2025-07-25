# Add this to your proguard-rules.pro

# Optimize for size
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Keep Nearby Connections
-keep class com.google.android.gms.nearby.** { *; }
-dontwarn com.google.android.gms.nearby.**

# Keep Serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.remote_mic.**$$serializer { *; }
-keepclassmembers class com.example.remote_mic.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.remote_mic.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Camera X
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Keep MediaRecorder and Audio
-keep class android.media.MediaRecorder { *; }
-keep class android.media.AudioManager { *; }

# Keep Compose
-keep class androidx.compose.** { *; }
-keep class kotlin.coroutines.** { *; }

# Keep FFmpeg
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.arthenica.smartexception.** { *; }

# Keep ExoPlayer
-keep class com.google.android.exoplayer2.** { *; }
-keep class androidx.media3.** { *; }

# Remove Kotlin metadata to reduce size
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
}