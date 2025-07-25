plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("kotlinx-serialization")
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

    signingConfigs {
        create("release") {
            // These values should be stored in keystore.properties or environment variables
            storeFile = file("key.jks")
            storePassword = project.findProperty("KEYSTORE_PASSWORD") as String? ?: System.getenv("KEYSTORE_PASSWORD")
            keyAlias = project.findProperty("KEY_ALIAS") as String? ?: System.getenv("KEY_ALIAS")
            keyPassword = project.findProperty("KEY_PASSWORD") as String? ?: System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            // Disable shrinking in debug for faster builds
            isMinifyEnabled = false
        }
        release {
            // Enable all size optimizations
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false

            // Use R8 full mode for maximum optimization
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = signingConfigs.getByName("release")

            // Additional optimizations
            ndk {
                debugSymbolLevel = "NONE"
            }
        }
    }

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