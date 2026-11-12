# Tidyra build notes (toolchain facts, verified 2026-11-13)

Local toolchain on this PC:
- JDK 17 at `C:\Users\liad1\AppData\Local\Programs\jdk-17` (Temurin 17.0.20.1). `java` on PATH is 1.8 — always set `JAVA_HOME` / use the jdk-17 binary for Gradle.
- Android SDK at `C:\Users\liad1\AppData\Local\Android\Sdk`: platform **android-35** and build-tools **35.0.0** only. `cmdline-tools/latest` is present; `sdkmanager --list` returned no platform rows on first call — check licences (`sdkmanager --licenses`) before relying on it to install android-36.
- Gradle 8.14 already cached at `~/.gradle/wrapper/dists/gradle-8.14-bin/38aieal9i53h9rfe7vjup95b9/gradle-8.14` — the wrapper can point at 8.14 with no download.
- Network reachable: Google Maven, Maven Central, services.gradle.org, Hugging Face.
- ADB: `platform-tools/adb.exe`; device `5A241FDCH0001C` (Pixel 10 Pro, Android 17) attached over USB.

Latest **stable** versions resolved from the live metadata:

| Artifact | Latest stable |
|---|---|
| com.android.tools.build:gradle (AGP) | 9.5.1 (9.x needs Gradle 9 — prefer newest 8.x against cached Gradle 8.14) |
| org.jetbrains.kotlin | 2.4.21 |
| androidx.compose:compose-bom | 2026.09.00 |
| androidx.compose.material3:material3 | 1.4.0 (Expressive APIs live here) |
| androidx.activity:activity-compose | 1.13.0 |
| androidx.lifecycle:lifecycle-viewmodel-compose | 2.11.0 |
| androidx.core:core-ktx | 1.19.1 |
| androidx.documentfile:documentfile | 1.1.0 |
| androidx.navigation:navigation-compose | 2.10.2 |
| androidx.datastore:datastore-preferences | 1.3.0 |
| androidx.work:work-runtime-ktx | 2.12.0 |
| dev.rikka.shizuku:api / :provider | 13.1.5 (Maven Central) |
| com.squareup.okhttp3:okhttp | 5.5.1 |
| io.coil-kt.coil3:coil-compose | 3.6.3 |

**MediaPipe GenAI is on Google Maven, not Maven Central**: `com.google.mediapipe:tasks-genai:0.10.35`
(`https://dl.google.com/dl/android/maven2/com/google/mediapipe/tasks-genai/` — Maven Central returns 404 for it).

Open risk to settle at first build: newer AndroidX (core-ktx 1.19, activity 1.13) may demand compileSdk 36+.
Either install platform 36 via sdkmanager, or pin AndroidX to the last versions that build against compileSdk 35.
