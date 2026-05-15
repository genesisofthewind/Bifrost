// settings.gradle.kts
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
    }
    versionCatalogs {
        create("libs") {
            val agp = "9.1.1"
            val kotlin = "2.0.21"
            val composeBom = "2025.02.00"

            plugin("android-application", "com.android.application").version(agp)
            plugin("kotlin-android", "org.jetbrains.kotlin.android").version(kotlin)
            plugin("kotlin-compose", "org.jetbrains.kotlin.plugin.compose").version(kotlin)

            library("androidx-core-ktx", "androidx.core:core-ktx:1.15.0")
            library("androidx-lifecycle-runtime-ktx", "androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
            library("androidx-activity-compose", "androidx.activity:activity-compose:1.10.0")
            library("androidx-compose-bom", "androidx.compose", "compose-bom").version(composeBom)
            library("androidx-ui", "androidx.compose.ui", "ui").withoutVersion()
            library("androidx-ui-graphics", "androidx.compose.ui", "ui-graphics").withoutVersion()
            library("androidx-ui-tooling-preview", "androidx.compose.ui", "ui-tooling-preview").withoutVersion()
            library("androidx-material3", "androidx.compose.material3", "material3").withoutVersion()
        }
    }
}

rootProject.name = "ThorDrawBridge"
include(":app")
