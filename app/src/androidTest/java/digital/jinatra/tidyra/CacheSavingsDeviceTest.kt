package digital.jinatra.tidyra

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import digital.jinatra.tidyra.core.cache.CacheBatch
import digital.jinatra.tidyra.core.shizuku.ShizukuBridge
import digital.jinatra.tidyra.ui.MainActivity
import digital.jinatra.tidyra.ui.station.StationViewModel
import digital.jinatra.tidyra.ui.format.Format
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Exact fixture allowlist passed directly to the view model; no GUI selection or confirmation clicks. */
class CacheSavingsDeviceTest {
    @Test fun freshMeasurementsReachTheActualCompletedScreen() = deviceTest {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        assumeTrue(context.packageName.endsWith(".usbtest"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("tidyra.cacheSavingsFixture") == "true")
        val bridge = ShizukuBridge(context)
        assertEquals(ShizukuBridge.State.READY, bridge.state())
        val fixtures = linkedMapOf("digital.jinatra.usbfixture" to "Tidyra USB Fixture",
            "digital.jinatra.usbfixture.peer" to "Tidyra Cache Peer")
        fixtures.forEach { (pkg, label) ->
            assertEquals(label, context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString())
            assertTrue(bridge.exec("am", "force-stop", pkg).ok)
            assertTrue(bridge.exec("am", "start", "-W", "-n", "$pkg/digital.jinatra.usbfixture.MainActivity").ok)
            val deadline = System.currentTimeMillis() + 10_000
            while (bridge.exec("run-as", pkg, "stat", "-c", "%s", "cache/disposable-cache.dat").stdout.trim() != "16777216") {
                check(System.currentTimeMillis() < deadline) { "Fixture cache preparation failed" }
                Thread.sleep(100)
            }
        }
        val activity = inst.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        lateinit var model: StationViewModel
        inst.runOnMainSync {
            model = ViewModelProvider(activity)[StationViewModel::class.java]
            model.scan()
        }
        val scanDeadline = System.currentTimeMillis() + 90_000
        while (model.state.value.scanning || !fixtures.keys.all { pkg -> model.state.value.apps.any { it.packageName == pkg } }) {
            check(System.currentTimeMillis() < scanDeadline) { "Fixture inventory was not available" }
            Thread.sleep(100)
        }
        // The argument is the exact owned package set, independent of any UI selection.
        inst.runOnMainSync { model.clearCachesViaAccessibility(fixtures.keys.toList()) }
        val deadline = System.currentTimeMillis() + 90_000
        while (model.state.value.working) {
            check(System.currentTimeMillis() < deadline) { "Fixture batch exceeded its deadline" }
            Thread.sleep(100)
        }
        val state = model.state.value
        assertEquals(fixtures.keys, state.cacheResults.map { it.packageName }.toSet())
        assertTrue(state.cacheResults.all { it.status == CacheBatch.Status.REQUESTED })
        val measured = state.cacheSavings ?: error("Fresh fixture measurements were unavailable")
        assertEquals(2, measured.appCount)
        assertTrue("Expected the two real 16 MiB caches to be measured", measured.saved.value >= 32L * 1024 * 1024)
        fixtures.keys.forEach { pkg ->
            assertFalse(bridge.exec("run-as", pkg, "ls", "cache/disposable-cache.dat").ok)
            assertEquals("TIDYRA_USB_KEEP_V1", bridge.exec("run-as", pkg, "cat", "files/keep.txt").stdout.trim())
            assertEquals("TIDYRA_USB_EXTERNAL_V1", bridge.exec("cat", "/sdcard/Android/data/$pkg/files/keep-external.txt").stdout.trim())
        }
        // Connect only after the privileged helper has exited; never inspect any other window.
        val ui = inst.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun texts(node: android.view.accessibility.AccessibilityNodeInfo): List<String> =
            listOfNotNull(node.text?.toString()) + (0 until node.childCount).flatMap { node.getChild(it)?.let(::texts).orEmpty() }
        val until = System.currentTimeMillis() + 7000
        var shown = false
        while (!shown && System.currentTimeMillis() < until) {
            ui.clearCache()
            val root = ui.rootInActiveWindow
            if (root?.packageName?.toString() == context.packageName) {
                val content = texts(root)
                shown = Format.size(measured.saved) in content && "Space saved from app cache" in content
            }
            if (!shown) Thread.sleep(100)
        }
        assertTrue("The measured amount did not reach the completed screen", shown)
        val bmp = ui.takeScreenshot()!!
        File(context.cacheDir, "cache-measured-result.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        Unit
    }
}
