plugins { id("com.android.application") version "8.13.2" }
android {
    namespace = "digital.jinatra.usbfixture"
    compileSdk = 36
    defaultConfig {
        applicationId = "digital.jinatra.usbfixture"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
