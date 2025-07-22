pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

        // Add Appodeal repository for FFmpeg packages
        maven {
            url = uri("https://artifactory.appodeal.com/appodeal-public/")
            name = "Appodeal"
        }

        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
        flatDir {
            dirs("libs")
        }
    }
}

rootProject.name = "remote_mic"
include(":app")