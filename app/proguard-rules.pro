# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ================================================================================================
# AGGRESSIVE SIZE OPTIMIZATION RULES
# ================================================================================================

# Enable more aggressive optimizations
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Remove debug and verbose logging from Kotlin
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}

# ================================================================================================
# COMPOSE SPECIFIC OPTIMIZATIONS
# ================================================================================================

# Keep Compose classes that might be referenced dynamically
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }

# Allow R8 to optimize Compose
-keepclassmembers class androidx.compose.** {
    *;
}

# ================================================================================================
# KOTLIN SPECIFIC OPTIMIZATIONS
# ================================================================================================

# Keep Kotlin metadata for reflection
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotations

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }

# ================================================================================================
# ANDROID SPECIFIC OPTIMIZATIONS
# ================================================================================================

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom views and their constructors
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep Activity subclasses
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ================================================================================================
# MEDIA AND CAMERA OPTIMIZATIONS
# ================================================================================================

# Keep ExoPlayer classes
-keep class com.google.android.exoplayer2.** { *; }
-keep class androidx.media3.** { *; }

# Keep Camera2 classes
-keep class androidx.camera.** { *; }

# Keep FFmpeg classes
-keep class com.arthenica.ffmpegkit.** { *; }

# ================================================================================================
# NETWORKING AND SERIALIZATION
# ================================================================================================

# Keep Nearby Connections API
-keep class com.google.android.gms.nearby.** { *; }

# Keep serialization classes
-keep @kotlinx.serialization.Serializable class * {
    static **[] values();
    static ** valueOf(java.lang.String);
    *;
}

# ================================================================================================
# REFLECTION AND INTROSPECTION
# ================================================================================================

# Keep classes that use reflection
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ================================================================================================
# DEBUGGING (Remove in production)
# ================================================================================================

# Uncomment these for debugging ProGuard issues
# -printmapping mapping.txt
# -printseeds seeds.txt
# -printusage unused.txt

# Remove source file names and line numbers for smaller size
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable