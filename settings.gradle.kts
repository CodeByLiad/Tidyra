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
        // Shizuku (rikkax) artifacts live on JitPack.
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github.*") }
        }
    }
}

rootProject.name = "Tidyra"
include(":app")
