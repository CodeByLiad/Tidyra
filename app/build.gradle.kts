import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val signingPropertiesFile = rootProject.file(providers.environmentVariable("TIDYRA_SIGNING_PROPERTIES")
    .orElse(".signing/keystore.properties").get())
val releaseCredentials = Properties().apply {
    if (signingPropertiesFile.isFile) signingPropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "digital.jinatra.tidyra"
    compileSdk = 36

    defaultConfig {
        applicationId = "digital.jinatra.tidyra"
        minSdk = 30
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["tidyraLabel"] = "@string/app_name"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        // Physical-device QA stays separate from an install signed by another machine.
        create("usbTest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".usbtest"
            versionNameSuffix = "-usbtest"
            resValue("string", "usb_test_app_name", "Tidyra USB Test")
            resValue("string", "cache_accessibility_name", "Tidyra USB Test cache assistant")
            manifestPlaceholders["tidyraLabel"] = "@string/usb_test_app_name"
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // F-Droid builds this from source and signs it itself; no local signing config.
        }
    }
    if (releaseCredentials.isNotEmpty()) {
        val releaseKey = signingConfigs.create("localRelease") {
            storeFile = rootProject.file(releaseCredentials.getProperty("storeFile"))
            storePassword = releaseCredentials.getProperty("storePassword")
            keyAlias = releaseCredentials.getProperty("keyAlias")
            keyPassword = releaseCredentials.getProperty("keyPassword")
        }
        buildTypes.getByName("release").signingConfig = releaseKey
    }
    buildTypes.create("usbRelease") {
        initWith(buildTypes.getByName("release"))
        applicationIdSuffix = ".usbtest"
        versionNameSuffix = "-usbtest-release"
        signingConfig = signingConfigs.getByName("debug")
        resValue("string", "usb_test_app_name", "Tidyra USB Test")
        resValue("string", "cache_accessibility_name", "Tidyra USB Test cache assistant")
        manifestPlaceholders["tidyraLabel"] = "@string/usb_test_app_name"
        matchingFallbacks += listOf("release")
    }
    testBuildType = when {
        providers.gradleProperty("tidyra.usbReleaseTests").isPresent -> "usbRelease"
        providers.gradleProperty("tidyra.usbTests").isPresent -> "usbTest"
        else -> "debug"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }

    // Reproducible-build friendliness for F-Droid.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.okhttp)

    // Shizuku: optional at runtime. The app checks for it and degrades when absent.
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // On-device Gemma. Isolated behind LlmEngine so a from-source backend can replace it.
    implementation(libs.mediapipe.tasks.genai)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.test:runner:1.7.0")
}
