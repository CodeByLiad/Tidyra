package digital.jinatra.tidyra.core.cache

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import digital.jinatra.tidyra.ui.MainActivity
import kotlinx.coroutines.*
import java.util.Locale

/** Optional Settings-only automation. No gestures, global button matching, or dialog acceptance. */
class CacheAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var batch: Job? = null
    private var overlay: LinearLayout? = null
    private var overlayText: TextView? = null
    private var nextId = 0L

    override fun onServiceConnected() {
        CacheAutomation.service = this
        CacheAutomation.publish(CacheAutomation.state.value.copy(connected = true))
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit // Poll fresh nodes; never retain event trees.
    override fun onInterrupt() { cancelBatch() }
    override fun onUnbind(intent: Intent?): Boolean {
        cancelBatch()
        removeOverlay()
        if (CacheAutomation.service === this) {
            CacheAutomation.service = null
            CacheAutomation.publish(CacheAutomation.state.value.copy(connected = false))
        }
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        batch?.cancel()
        scope.cancel()
        removeOverlay()
        if (CacheAutomation.service === this) {
            CacheAutomation.service = null
            CacheAutomation.publish(CacheAutomation.state.value.copy(connected = false,
                note = "Accessibility disconnected. Remaining apps were not requested."))
        }
        super.onDestroy()
    }

    internal fun startBatch(packages: List<String>): Long? {
        if (batch?.isActive == true || packages.isEmpty()) return null
        val approved = packages.distinct()
        nextId = maxOf(nextId + 1, SystemClock.elapsedRealtime())
        val id = nextId
        CacheAutomation.unavailableReason(this)?.let { reason ->
            CacheAutomation.publish(CacheAutomation.State(true, id, false, total = approved.size,
                results = approved.map { CacheBatch.Item(it, CacheBatch.Status.NOT_ATTEMPTED, reason) }, note = reason))
            return id
        }
        CacheAutomation.publish(CacheAutomation.State(true, id, true, total = approved.size))
        batch = scope.launch {
            val results = mutableListOf<CacheBatch.Item>()
            var returnToApp = true
            try {
                showOverlay()
                for ((index, pkg) in approved.withIndex()) {
                    ensureActive()
                    CacheAutomation.publish(CacheAutomation.State(true, id, true, pkg, approved.size, results.toList()))
                    val identity = withContext(Dispatchers.IO) { verifiedLabel(pkg) }
                    if (identity == null) {
                        results += CacheBatch.Item(pkg, CacheBatch.Status.NOT_ATTEMPTED, "App identity is protected or ambiguous; use manual Settings")
                        continue
                    }
                    overlayText?.text = "${index + 1}/${approved.size} · $identity"
                    val intent = CacheAutomation.storageIntent(this@CacheAccessibilityService, pkg)
                    // Automation requires the direct native storage page. Other OEM layouts stay manual.
                    if (intent.action != "com.android.settings.APP_STORAGE_SETTINGS") {
                        results += CacheBatch.Item(pkg, CacheBatch.Status.NOT_ATTEMPTED, "Native cache page unavailable; use manual Settings")
                        continue
                    }
                    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    val outcome = requestCache(identity)
                    results += CacheBatch.Item(pkg, outcome.first, outcome.second)
                    CacheAutomation.publish(CacheAutomation.State(true, id, true, pkg, approved.size, results.toList()))
                    if (outcome.second.startsWith("Left Settings")) { returnToApp = false; break }
                    if (outcome.second.contains("unverified") ||
                        (outcome.first == CacheBatch.Status.NOT_ATTEMPTED && outcome.second != "Clear cache is disabled in Settings")) break
                }
            } catch (cancelled: CancellationException) {
                // Finish below before propagating cancellation, so the UI always unlocks.
            } catch (_: Exception) {
                // Preserve results; never broaden the action when an OEM screen differs.
            } finally {
                val done = results.map { it.packageName }.toSet()
                results += approved.filterNot { it in done }.map { CacheBatch.Item(it, CacheBatch.Status.NOT_ATTEMPTED,
                    "Not confirmed; review manually. A request already sent may finish.") }
                removeOverlay()
                if (returnToApp && CacheAutomation.service === this@CacheAccessibilityService) runCatching { startActivity(Intent(this@CacheAccessibilityService, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)) }
                // A disconnected service can finish after a replacement has bound.
                // Never overwrite a newer session or mark that replacement disconnected.
                if (CacheAutomation.state.value.runId == id) CacheAutomation.publish(CacheAutomation.State(CacheAutomation.service != null,
                    id, false, total = approved.size, results = results.toList(),
                    note = CacheAutomation.unavailableReason(this@CacheAccessibilityService) ?:
                        "${results.count { it.status == CacheBatch.Status.REQUESTED }} of ${approved.size} requested through Settings. Review refreshed sizes; requests are not proof of freed space."))
            }
        }
        return id
    }

    internal fun cancelBatch() { batch?.cancel() }

    private fun verifiedLabel(pkg: String): String? = runCatching {
        val pm = packageManager
        val info = pm.getApplicationInfo(pkg, 0)
        val owners = pm.getPackagesForUid(info.uid) ?: return@runCatching null
        if (CachePolicy.blockedReason(pkg, info.uid, info.flags and ApplicationInfo.FLAG_PERSISTENT != 0, owners.size != 1) != null) return@runCatching null
        val label = pm.getApplicationLabel(info).toString().trim()
        if (label.isBlank()) return@runCatching null
        // Settings exposes an app label, not a package ID. Refuse duplicate labels instead of guessing.
        if (pm.getInstalledApplications(0).count { pm.getApplicationLabel(it).toString().trim() == label } != 1) null else label
    }.getOrNull()

    private fun cacheText(): String? = runCatching {
        val resources = createPackageContext(CacheUiPolicy.SETTINGS, 0).resources
        val id = resources.getIdentifier("clear_cache_btn_text", "string", CacheUiPolicy.SETTINGS)
        if (id != 0) resources.getString(id).trim() else null
    }.getOrNull() ?: if (Locale.getDefault().language == "en") "Clear cache" else null

    private suspend fun requestCache(label: String): Pair<CacheBatch.Status, String> {
        val text = cacheText() ?: return CacheBatch.Status.NOT_ATTEMPTED to "Cache label unavailable in this language; use manual Settings"
        val start = SystemClock.elapsedRealtime()
        var clicked = false
        var diagnostic: String? = null
        while (SystemClock.elapsedRealtime() - start < 15_000) {
            currentCoroutineContext().ensureActive()
            delay(250)
            val root = rootInActiveWindow ?: continue
            // Look at the package only; never traverse unrelated app content.
            if (root.packageName?.toString() != CacheUiPolicy.SETTINGS) {
                // Pixel redirects through another Settings activity. During that short
                // transition only inspect the package; do not treat the old window as a user exit.
                if (!clicked && SystemClock.elapsedRealtime() - start < 2000) continue
                return (if (clicked) CacheBatch.Status.REQUESTED else CacheBatch.Status.NOT_ATTEMPTED) to "Left Settings; remaining automation stopped"
            }
            val titles = root.findAccessibilityNodeInfosByViewId(CacheUiPolicy.TITLE).filter { it.isVisibleToUser }.map { it.text?.toString().orEmpty().trim() }
            if (titles.isNotEmpty() && label !in titles && SystemClock.elapsedRealtime() - start > 2000)
                return CacheBatch.Status.NOT_ATTEMPTED to "Left Settings target; remaining automation stopped"
            // Expressive Settings attaches the click listener to action2, not its icon.
            val groups = root.findAccessibilityNodeInfosByViewId(CacheUiPolicy.ACTION)
            val buttons = if (groups.isNotEmpty()) groups else root.findAccessibilityNodeInfosByViewId(CacheUiPolicy.BUTTON)
            val controls = buttons.map {
                val nativeLabel = CacheUiPolicy.label(it.text?.toString(), it.contentDescription?.toString()).ifBlank {
                    if (it.viewIdResourceName == CacheUiPolicy.ACTION) it.findAccessibilityNodeInfosByViewId(CacheUiPolicy.ACTION_TEXT)
                        .filter { child -> child.isVisibleToUser }.singleOrNull()?.text?.toString().orEmpty().trim() else ""
                }
                CacheUiPolicy.Control(it.viewIdResourceName, nativeLabel,
                it.className?.toString(), it.isVisibleToUser, it.isClickable, it.isEnabled) }
            if (titles.singleOrNull() == label && buttons.isEmpty() && SystemClock.elapsedRealtime() - start > 3000) {
                CacheAutomation.markUnsupported(this)
                return CacheBatch.Status.NOT_ATTEMPTED to CacheAutomation.unavailableReason(this).orEmpty()
            }
            if (packageName.endsWith(".usbtest")) {
                // Isolated-fixture diagnostics contain only control metadata, never screen text.
                val summary = "headers=${titles.size}, identity=${titles.singleOrNull() == label}, controls=" + controls.map {
                    "${it.id}:${it.className}:label=${it.text == text}:visible=${it.visible}:clickable=${it.clickable}:enabled=${it.enabled}"
                }
                if (summary != diagnostic) android.util.Log.i("TidyraCacheFixture", summary)
                diagnostic = summary
            }
            val control = CacheUiPolicy.cacheControl(root.packageName?.toString(), titles, label, text, controls) ?: continue
            if (clicked) {
                if (!control.enabled) return CacheBatch.Status.REQUESTED to "Clear cache tapped; Settings updated"
            } else if (control.enabled) {
                // Revalidate the fresh tree immediately before the single allowed click.
                if (buttons.single().performAction(AccessibilityNodeInfo.ACTION_CLICK)) clicked = true
            } else if (SystemClock.elapsedRealtime() - start > 3000) {
                return CacheBatch.Status.NOT_ATTEMPTED to "Clear cache is disabled in Settings"
            }
        }
        return (if (clicked) CacheBatch.Status.REQUESTED else CacheBatch.Status.NOT_ATTEMPTED) to
            if (clicked) "Clear cache tapped; completion unverified" else "Settings layout or app identity could not be verified; use manual Settings"
    }

    private fun showOverlay() {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
            setBackgroundColor(getColor(android.R.color.background_light))
        }
        overlayText = TextView(this).apply { textSize = 16f; setTextColor(getColor(android.R.color.primary_text_light)); text = "Preparing selected apps…" }
        row.addView(overlayText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply { text = "Cancel"; setOnClickListener { cancelBatch() } })
        overlay = row
        getSystemService(WindowManager::class.java).addView(row, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.BOTTOM; y = (32 * density).toInt() })
    }
    private fun removeOverlay() {
        overlay?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        overlay = null; overlayText = null
    }
}
