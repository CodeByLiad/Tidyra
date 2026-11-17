package digital.jinatra.tidyra.core.cache

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory, user-started sessions only. A process restart cannot resume unattended actions. */
object CacheAutomation {
    private fun backend(context: Context) = "${android.os.Build.FINGERPRINT}:${digital.jinatra.tidyra.BuildConfig.VERSION_CODE}:" +
        runCatching { context.packageManager.getPackageInfo(CacheUiPolicy.SETTINGS, 0).longVersionCode }.getOrDefault(-1) + ":${java.util.Locale.getDefault().toLanguageTag()}"
    private const val MANUAL_REASON = "Android did not expose its cache controls to automation. Use manual Settings."
    fun unavailableReason(context: Context): String? = if (context.getSharedPreferences("cache_automation", Context.MODE_PRIVATE)
        .getString("unsupported_backend", null) == backend(context)) MANUAL_REASON else null
    fun resetUnsupported(context: Context) { context.getSharedPreferences("cache_automation", Context.MODE_PRIVATE).edit().remove("unsupported_backend").apply() }
    internal fun markUnsupported(context: Context) { context.getSharedPreferences("cache_automation", Context.MODE_PRIVATE).edit().putString("unsupported_backend", backend(context)).apply() }
    data class State(val connected: Boolean = false, val runId: Long = 0, val running: Boolean = false,
        val current: String? = null, val total: Int = 0, val results: List<CacheBatch.Item> = emptyList(), val note: String? = null)
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    internal var service: CacheAccessibilityService? = null
    internal fun publish(value: State) { mutable.value = value }
    fun start(packages: List<String>): Long? = service?.startBatch(packages)
    fun cancel(runId: Long) { if (state.value.runId == runId) service?.cancelBatch() }

    fun storageIntent(context: Context, packageName: String): Intent {
        val direct = Intent("com.android.settings.APP_STORAGE_SETTINGS", Uri.parse("package:$packageName"))
            .setPackage(CacheUiPolicy.SETTINGS)
        return (if (direct.resolveActivity(context.packageManager) != null) direct
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    // AOSP exports this action, but the SDK does not expose its constant on every API.
    fun permissionIntent(context: Context): Intent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
        putExtra(Intent.EXTRA_COMPONENT_NAME, android.content.ComponentName(context, CacheAccessibilityService::class.java))
    }
}
