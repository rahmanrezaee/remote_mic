plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.0" apply false
}

tasks.register("buildAllApks") {
    group = "build"
    description = "Build all architecture-specific APKs"
    dependsOn("assembleRelease")

    doLast {
        println("✅ All APKs built successfully:")
        println("📱 ARM64: app/build/outputs/apk/release/app-arm64-v8a-release.apk")
        println("📱 ARM32: app/build/outputs/apk/release/app-armeabi-v7a-release.apk")
        println("💻 x86: app/build/outputs/apk/release/app-x86-release.apk")
        println("💻 x86_64: app/build/outputs/apk/release/app-x86_64-release.apk")
        println("🌐 Universal: app/build/outputs/apk/release/app-universal-release.apk")
    }
}

tasks.register("printApkSizes") {
    group = "verification"
    description = "Print sizes of all generated APKs"

    doLast {
        val apkDir = File("${project.buildDir}/outputs/apk/release")
        if (apkDir.exists()) {
            apkDir.listFiles()?.filter { it.extension == "apk" }?.forEach { apk ->
                val sizeInMB = apk.length() / (1024 * 1024)
                println("📦 ${apk.name}: ${sizeInMB}MB")
            }
        }
    }
}

tasks.register("cleanBuildCache") {
    group = "build"
    description = "Clean all build caches for smaller builds"

    doLast {
        delete("${project.buildDir}/intermediates")
        delete("${project.buildDir}/tmp")
        println("🧹 Build cache cleaned")
    }
}