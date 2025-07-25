plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("kotlinx-serialization")
}


tasks.register("buildSplitApks") {
    group = "build"
    description = "Build APKs for different architectures"
    dependsOn("assembleRelease")

    doLast {
        println("✅ Built APKs for all architectures")
    }
}


android {
    namespace = "com.example.remote_mic"
    compileSdk = 35


    defaultConfig {
        applicationId = "com.example.remote_mic"
        minSdk = 24
        //noinspection OldTargetApi
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // APK Splits Configuration (works for debug builds too)
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true  // Generate universal APK for fallback
        }
        density {
            isEnable = true
            reset()
            include("ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            // Keep debug builds fast - no minification
            isMinifyEnabled = false
        }
        release {
            // Enable all size optimizations for release
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false

            // Use R8 full mode for maximum optimization
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // No signing config - will use debug key
        }
    }

    // AAB Configuration
    bundle {
        language {
            // Split by language to reduce size
            enableSplit = true
        }
        density {
            // Split by screen density
            enableSplit = true
        }
        abi {
            // Split by CPU architecture
            enableSplit = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Additional excludes for size optimization
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/kotlin/**"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "/META-INF/**/previous-compilation-data.bin"
        }
    }
}

dependencies {
    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM and UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Compose extensions for state management
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Material Icons Extended (contains all Material Design icons)
    implementation(libs.androidx.material.icons.extended)

    // Google Nearby Connections API (Core dependency for P2P)
    implementation(libs.play.services.nearby)

    // Kotlinx Serialization (for message exchange)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines for async operations
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Activity result APIs for permissions
    implementation(libs.androidx.activity.ktx)

    // OPTION 1: FFmpeg-kit from Appodeal (with smart-exception)
    implementation(libs.ffmpeg.kit.min.gpl)
    implementation(libs.smart.exception.java)

    implementation(libs.androidx.media3.exoplayer)

    // ExoPlayer UI components
    implementation(libs.androidx.media3.ui)

    // ExoPlayer common functionality
    implementation(libs.androidx.media3.common)

    // Optional: For specific media formats
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.smoothstreaming)

    // Optional: Camera and Audio dependencies (uncomment when implementing media features)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)

    implementation(libs.androidx.fragment.ktx)

    // Testing dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Debug dependencies
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Custom tasks for building DEBUG APKs
tasks.register("buildAllDebugApks") {
    group = "build"
    description = "Build all architecture-specific DEBUG APKs"
    dependsOn("assembleDebug")

    doLast {
        println("✅ All DEBUG APKs built successfully:")
        println("📱 ARM64: app/build/outputs/apk/debug/app-arm64-v8a-debug.apk")
        println("📱 ARM32: app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk")
        println("💻 x86: app/build/outputs/apk/debug/app-x86-debug.apk")
        println("💻 x86_64: app/build/outputs/apk/debug/app-x86_64-debug.apk")
        println("🌐 Universal: app/build/outputs/apk/debug/app-universal-debug.apk")
    }
}

tasks.register("printDebugApkSizes") {
    group = "verification"
    description = "Print sizes of all generated DEBUG APKs"

    doLast {
        val apkDir = File("app/build/outputs/apk/debug")
        if (apkDir.exists()) {
            apkDir.listFiles()?.filter { it.extension == "apk" }?.forEach { apk ->
                val sizeInMB = apk.length() / (1024 * 1024)
                println("📦 ${apk.name}: ${sizeInMB}MB")
            }
        } else {
            println("❌ Debug APK directory not found. Run 'assembleDebug' first.")
        }
    }
}

tasks.register("buildAllReleaseApks") {
    group = "build"
    description = "Build all architecture-specific RELEASE APKs (debug-signed)"
    dependsOn("assembleRelease")

    doLast {
        println("✅ All RELEASE APKs built successfully (debug-signed):")
        println("📱 ARM64: app/build/outputs/apk/release/app-arm64-v8a-release.apk")
        println("📱 ARM32: app/build/outputs/apk/release/app-armeabi-v7a-release.apk")
        println("💻 x86: app/build/outputs/apk/release/app-x86-release.apk")
        println("💻 x86_64: app/build/outputs/apk/release/app-x86_64-release.apk")
        println("🌐 Universal: app/build/outputs/apk/release/app-universal-release.apk")
    }
}