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
    plugins {
        // Define the Google Services plugin with its version
        id("com.google.gms.google-services") version "4.4.0" apply false // Or latest version
        // Define other plugins like Android Application and Kotlin Android
        id("com.android.application") version "8.2.0" apply false // Replace with your AGP version
        id("org.jetbrains.kotlin.android") version "1.9.22" apply false // Replace with your Kotlin version
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SafeHands"
include(":app")
