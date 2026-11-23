package digital.jinatra.tidyra

import android.content.Intent
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import digital.jinatra.tidyra.core.cache.CacheBatch
import digital.jinatra.tidyra.core.cache.CacheSavings
import digital.jinatra.tidyra.core.model.Bytes
import digital.jinatra.tidyra.core.scan.AppInventory
import digital.jinatra.tidyra.ui.MainActivity
import digital.jinatra.tidyra.ui.detail.CacheDetailScreen
import digital.jinatra.tidyra.ui.station.StationState
import digital.jinatra.tidyra.ui.theme.TidyraTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Display-only sample results. This test never requests cache clearing or opens Settings. */
class CacheResultsUiTest {
    @Test fun summaryDetailsAndActionsRemainUsable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".usbtest"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("tidyra.cacheResultsUi") == "true")
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val ui = instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun nodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(node) +
            (0 until node.childCount).flatMap { node.getChild(it)?.let(::nodes).orEmpty() }
        fun visible(): List<AccessibilityNodeInfo> {
            ui.clearCache()
            val root = ui.rootInActiveWindow ?: return emptyList()
            if (root.packageName?.toString() != context.packageName || !root.refresh()) return emptyList()
            return nodes(root)
        }
        fun find(text: String): AccessibilityNodeInfo {
            val until = System.currentTimeMillis() + 7000
            while (System.currentTimeMillis() < until) {
                visible().firstOrNull { it.text?.toString() == text }?.let { return it }
                Thread.sleep(100)
            }
            error("Missing cache UI control: $text; visible=${visible().mapNotNull { it.text }}")
        }
        fun click(text: String) {
            var node = find(text)
            while (!node.isClickable) node = node.parent ?: error("No action for $text")
            assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
        }
        fun scrollTo(text: String) {
            repeat(12) {
                if (visible().any { it.text?.toString() == text && it.isVisibleToUser }) return
                val list = visible().firstOrNull { it.isScrollable } ?: error("No results list")
                check(list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
                Thread.sleep(150)
            }
            find(text)
        }
        fun capture(name: String) {
            ui.takeScreenshot()?.let { bitmap ->
                File(context.cacheDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            } ?: error("Screenshot unavailable")
        }
        val dismissed = AtomicBoolean(false)
        val back = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        val results = (1..17).map { CacheBatch.Item("display.fixture.$it", CacheBatch.Status.REQUESTED,
            "Clear cache requested; Settings updated") }
        val apps = results.mapIndexed { index, result -> AppInventory.AppEntry(result.packageName,
            "Sample app ${index + 1}", false, Bytes.ZERO, Bytes.ZERO, Bytes.ZERO, 0) }
        fun render(state: StationState, dark: Boolean = false, scale: Float = 1f) {
            instrumentation.runOnMainSync {
                activity.setContent {
                    key(state) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                        TidyraTheme(darkTheme = dark) {
                            Surface {
                                CacheDetailScreen(state, { _, _ -> }, {}, {}, { cancelled.set(true) }, {}, {}, {}, {}, {},
                                    onDismissResults = { dismissed.set(true) }, onBack = { back.set(true) },
                                    modifier = Modifier.statusBarsPadding())
                            }
                        }
                    }
                    }
                }
            }
            instrumentation.waitForIdleSync()
        }
        val mb = 1024L * 1024
        render(StationState(apps = apps, cacheResults = results,
            cacheSavings = CacheSavings.Measurement(Bytes(560 * mb), Bytes(80 * mb), 17)))
        find("480 MB")
        find("Space saved from app cache")
        find("17 requested")
        find("Done")
        assertFalse(visible().any { it.text?.toString() == "Clear selected cache" })
        find("Sample app 1")
        assertFalse(visible().any { it.text?.toString()?.startsWith("View app results") == true })
        capture("cache-summary-light")
        assertFalse(visible().any { it.text?.contains("Settings updated") == true })
        scrollTo("Sample app 17")
        capture("cache-details-light")
        click("Review remaining apps")
        assertTrue(dismissed.get())
        assertFalse(back.get())
        dismissed.set(false)
        click("Done")
        assertTrue(dismissed.get())
        assertTrue(back.get())

        // Mixed outcomes must never label an unconfirmed request as a confirmed clear.
        val mixed = listOf(results[0].copy(status = CacheBatch.Status.CONFIRMED), results[1],
            results[2].copy(status = CacheBatch.Status.FAILED, detail = "Settings did not respond. Try again."),
            results[3].copy(status = CacheBatch.Status.NOT_ATTEMPTED, detail = "Not confirmed after cancellation"))
        render(StationState(apps = apps, cacheResults = mixed,
            cacheSavings = CacheSavings.Measurement(Bytes(225_792 * mb), Bytes(1024 * mb), 2)), dark = true, scale = 1.5f)
        find("219.5 GB")
        find("1 confirmed · 1 requested · 2 need review")
        find("Done")
        capture("cache-summary-dark-large")
        scrollTo("Couldn’t clear")
        find("Couldn’t clear")
        scrollTo("Settings did not respond. Try again.")
        capture("cache-details-dark-large")

        render(StationState(apps = apps, cacheResults = listOf(results.first()),
            cacheSavings = CacheSavings.Measurement(Bytes(100), Bytes(100), 1)))
        find("0 B")
        find("No reduction measured yet. Android may update cache sizes later.")
        render(StationState(apps = apps, cacheResults = listOf(results.first())))
        find("Space saved unavailable")
        assertFalse(visible().any { it.text?.toString() == "0 B" })

        render(StationState(working = true, cacheProgress = CacheBatch.Progress(null, 2, emptyList())))
        find("0 of 2 · Preparing")
        click("Cancel")
        assertTrue(cancelled.get())
        assertFalse(visible().any { it.text?.toString() == "Done" })
    }
}
