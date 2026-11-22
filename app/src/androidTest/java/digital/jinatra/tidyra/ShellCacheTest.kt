package digital.jinatra.tidyra

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import digital.jinatra.tidyra.core.cache.*
import digital.jinatra.tidyra.core.shizuku.ShizukuBridge
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** No Instrumentation UiAutomation connection: exercises the actual Shizuku backend. */
class ShellCacheTest {
    private suspend fun regenerate(bridge: ShizukuBridge, pkg: String) {
        assertTrue(bridge.exec("am", "force-stop", pkg).ok)
        assertTrue(bridge.exec("am", "start", "-W", "-n", "$pkg/digital.jinatra.usbfixture.MainActivity").ok)
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (bridge.exec("run-as", pkg, "stat", "-c", "%s", "cache/disposable-cache.dat").stdout.trim() == "16777216") return
            Thread.sleep(100)
        }
        fail("Disposable fixture did not finish preparing cache: $pkg")
    }
    @Test fun selectedCachesRemovedWhileSavedDataSurvives() = deviceTest {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".usbtest"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("tidyra.shellCacheFixture") == "true")
        val bridge = ShizukuBridge(context)
        assertEquals(ShizukuBridge.State.READY, bridge.state())
        val protectedEvents = mutableListOf<String>()
        val protected = bridge.automateCaches(listOf(context.packageName, "com.android.settings")) { protectedEvents += it }
        assertTrue(protected.stderr, protected.ok)
        assertEquals(protectedEvents.toString(), 2, protectedEvents.count { it.startsWith("TIDYRA_CACHE:") && org.json.JSONObject(it.substringAfter("TIDYRA_CACHE:")).optString("event") == "skipped" })
        val fixtures = listOf("digital.jinatra.usbfixture", "digital.jinatra.usbfixture.peer")
        fixtures.forEach {
            val label = context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(it, 0)).toString()
            assertTrue(label == "Tidyra USB Fixture" || label == "Tidyra Cache Peer")
            regenerate(bridge, it)
        }
        // Closing the confirmation/control pipe before START must never issue a click.
        val disconnected = bridge.execWithTimeout(10_000, "/system/bin/env", "CLASSPATH=${context.applicationInfo.sourceDir}",
            "/system/bin/app_process", "/system/bin", "digital.jinatra.tidyra.core.cache.ShellCacheMain", "--caller", context.packageName, fixtures.first())
        assertFalse(disconnected.stdout, disconnected.stdout.contains("\"event\":\"requested\""))
        assertTrue(bridge.exec("run-as", fixtures.first(), "ls", "cache/disposable-cache.dat").ok)
        var id = 0L
        instrumentation.runOnMainSync {
            context.startActivity(Intent(context, digital.jinatra.tidyra.ui.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
        Thread.sleep(1000)
        instrumentation.runOnMainSync { id = ShellCacheService.start(context, fixtures + fixtures.first()) }
        val deadline = System.currentTimeMillis() + 65_000
        while (System.currentTimeMillis() < deadline) {
            val state = CacheAutomation.state.value
            if (state.runId == id && !state.running) break
            Thread.sleep(100)
        }
        val state = CacheAutomation.state.value
        assertEquals(state.toString(), id, state.runId)
        assertFalse(state.toString(), state.running)
        assertEquals(state.toString(), 2, state.results.size)
        assertTrue(state.toString(), state.results.all { it.status == CacheBatch.Status.REQUESTED })
        fixtures.forEach {
            assertFalse("Cache remains for $it", bridge.exec("run-as", it, "ls", "cache/disposable-cache.dat").ok)
            assertEquals("TIDYRA_USB_KEEP_V1", bridge.exec("run-as", it, "cat", "files/keep.txt").stdout.trim())
            assertEquals("TIDYRA_USB_EXTERNAL_V1", bridge.exec("cat", "/sdcard/Android/data/$it/files/keep-external.txt").stdout.trim())
        }
        // Regenerate disposable caches and exercise the actual foreground-service cancel route.
        fixtures.forEach {
            regenerate(bridge, it)
        }
        instrumentation.runOnMainSync { context.startActivity(Intent(context, digital.jinatra.tidyra.ui.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) }
        Thread.sleep(1000)
        instrumentation.runOnMainSync {
            id = ShellCacheService.start(context, fixtures)
            ShellCacheService.cancel(context)
        }
        val cancelDeadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < cancelDeadline) {
            val cancelled = CacheAutomation.state.value
            if (cancelled.runId == id && !cancelled.running) break
            Thread.sleep(100)
        }
        assertEquals(id, CacheAutomation.state.value.runId)
        assertFalse(CacheAutomation.state.value.toString(), CacheAutomation.state.value.running)
        fixtures.forEach { assertTrue("Cancellation must preserve unrequested cache", bridge.exec("run-as", it, "ls", "cache/disposable-cache.dat").ok) }
    }
}
