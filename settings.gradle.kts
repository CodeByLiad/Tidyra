pluginManagement {
    // Kotlin 2.4 metadata needs R8 >= 9.1.29; keep AGP 8 and compileSdk 36.
    buildscript {
        repositories { google(); mavenCentral() }
        dependencies { classpath("com.android.tools:r8:9.1.57") }
    }
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
