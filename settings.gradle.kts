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

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kixyubook"

include(
    ":app",
    ":core:core-common",
    ":core:core-ui",
    ":core:core-designsystem",
    ":core:core-navigation",
    ":core:core-database",
    ":core:core-datastore",
    ":core:core-reader-engine",
    ":feature:feature-home",
    ":feature:feature-library",
    ":feature:feature-reader",
    ":feature:feature-settings",
)
