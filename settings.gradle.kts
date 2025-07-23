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

        maven {
            url = uri("https://artifactory.appodeal.com/appodeal-public/")
            name = "Appodeal"
        }

        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }

    }
}

rootProject.name = "remote_mic"
include(":app")