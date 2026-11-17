# Shizuku's hidden process bridge is called reflectively.
-keepclassmembers class rikka.shizuku.Shizuku {
    public static *** newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}

# MediaPipe includes references to these code-generation annotations only.
# Generated model classes are already packaged; no AutoValue processor runs on-device.
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.auto.value.AutoValue$Builder

# Protobuf-lite schemas address generated fields by their original names. MediaPipe
# constructs these messages at model load; removing/renaming fields breaks release AI.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# Android's instrumentation runner references this shared dependency by its public name.
# Keep that API stable when the same optimized APK is exercised by the release tests.
-keep class androidx.tracing.Trace { *; }

# The AndroidJUnitRunner APK shares the target app's Kotlin runtime. Preserve that
# cross-APK API, including facade classes that the app itself can otherwise inline.
# Application code and resources are still shrunk and optimized normally.
-keep class kotlin.** { *; }

# Stable entry points exercised across the separate instrumentation APK. Keep the
# API boundary; allow R8 to optimize method bodies and all private implementation.
-keep,allowoptimization class digital.jinatra.tidyra.core.organize.Organizer { public *; }
-keep,allowoptimization class digital.jinatra.tidyra.core.organize.Organizer$* { public *; }
-keep,allowoptimization class digital.jinatra.tidyra.core.trash.TrashStore { public *; }
-keep,allowoptimization class digital.jinatra.tidyra.core.trash.TrashStore$* { public *; }
-keep,allowoptimization class digital.jinatra.tidyra.core.safety.SafeFiles { public *; }
-keep,allowoptimization class digital.jinatra.tidyra.core.model.Bytes { public *; }
-keep,allowoptimization class digital.jinatra.tidyra.core.shizuku.ShizukuBridge { public *; }
-keep,allowoptimization class digital.jinatra.tidyra.core.shizuku.ShizukuBridge$* { public *; }
-keep,allowoptimization class digital.jinatra.tidyra.core.shizuku.RestrictedFolder { public *; }
-keep,allowoptimization class digital.jinatra.tidyra.core.runtime.** { public *; }
-keep,allowoptimization class digital.jinatra.tidyra.core.cache.** { public *; }
-keep,allowoptimization class digital.jinatra.tidyra.core.preview.LocalImageDecoder { public *; }
-keep,allowoptimization class digital.jinatra.tidyra.ai.MediaPipeLlmEngine { public *; }
-keep,allowoptimization class digital.jinatra.tidyra.ai.PromptTemplate { public *; }
