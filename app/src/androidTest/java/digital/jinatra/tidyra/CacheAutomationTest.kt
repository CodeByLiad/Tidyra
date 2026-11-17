package digital.jinatra.tidyra

import android.app.UiAutomation
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import digital.jinatra.tidyra.core.cache.CacheAutomation
import digital.jinatra.tidyra.core.cache.CacheBatch
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.After
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** Explicit fixture opt-in: never operates on personal apps or grants production special access. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CacheAutomationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val fixtures = listOf("digital.jinatra.usbfixture", "digital.jinatra.usbfixture.peer")
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand(command))
        .bufferedReader().use { it.readText().trim() }

    @Before fun requireIsolatedOptIn() {
        assumeTrue(context.packageName.endsWith(".usbtest"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("tidyra.accessibilityFixture") == "true")
        fixtures.forEach { pkg ->
            val label = context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
            assertTrue(label, label == "Tidyra USB Fixture" || label == "Tidyra Cache Peer")
        }
        CacheAutomation.resetUnsupported(context)
    }

    @After fun removeTestGrant() {
        if (context.packageName.endsWith(".usbtest") && InstrumentationRegistry.getArguments().getString("tidyra.accessibilityFixture") == "true")
            setTestServiceEnabled(false)
    }

    private fun setTestServiceEnabled(enabled: Boolean) {
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        try {
            val component = "${context.packageName}/digital.jinatra.tidyra.core.cache.CacheAccessibilityService"
            val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val services = current.orEmpty().split(':').filter { it.isNotBlank() && it != component }.toMutableList()
            if (enabled) services += component
            Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, services.joinToString(":"))
            Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, if (services.isEmpty()) 0 else 1)
        } finally { automation.dropShellPermissionIdentity() }
    }

    private fun run(packages: List<String>, cancelImmediately: Boolean = false, reconnect: Boolean = true): CacheAutomation.State {
        // Match production: the person starts from Tidyra, never a fixture or a stale Settings task.
        // Shell probes use UI automation; bind our service only after those probes finish.
        if (reconnect) {
            setTestServiceEnabled(false)
            val disconnected = System.currentTimeMillis() + 5000
            while ((CacheAutomation.state.value.connected || CacheAutomation.state.value.running) && System.currentTimeMillis() < disconnected) Thread.sleep(100)
            setTestServiceEnabled(true)
            val connected = System.currentTimeMillis() + 10_000
            while (!CacheAutomation.state.value.connected && System.currentTimeMillis() < connected) Thread.sleep(100)
            assertTrue("Isolated service failed to bind", CacheAutomation.state.value.connected)
        }
        instrumentation.runOnMainSync { context.startActivity(Intent(context, digital.jinatra.tidyra.ui.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) }
        Thread.sleep(500)
        var id: Long? = null
        instrumentation.runOnMainSync {
            id = CacheAutomation.start(packages)
            if (cancelImmediately) id?.let(CacheAutomation::cancel)
        }
        assertNotNull("Batch could not start: ${CacheAutomation.state.value}", id)
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            val state = CacheAutomation.state.value
            if (state.runId == id && !state.running) return state
            Thread.sleep(100)
        }
        instrumentation.runOnMainSync { id?.let(CacheAutomation::cancel) }
        fail("Accessibility batch exceeded its bounded deadline")
        error("unreachable")
    }

    @Test fun a_protectedTargetsCannotBeClicked() {
        val state = run(listOf(context.packageName, "com.android.settings"))
        assertEquals(2, state.results.size)
        assertTrue(state.results.all { it.status == CacheBatch.Status.NOT_ATTEMPTED })
    }

    @Test fun b_twoSelectedFixtureCachesAreRemovedAndDataSurvives() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tidyra.expectCacheAutomation") != "manual")
        fixtures.forEach { assertTrue(shell("run-as $it ls cache/disposable-cache.dat").contains("disposable-cache.dat")) }
        val state = run(fixtures + fixtures.first())
        assertEquals("Duplicate selection must not add another request", 2, state.results.size)
        assertTrue(state.results.toString(), state.results.all { it.status == CacheBatch.Status.REQUESTED })
        fixtures.forEach { pkg ->
            assertEquals("TIDYRA_USB_KEEP_V1", shell("run-as $pkg cat files/keep.txt"))
            assertEquals("TIDYRA_USB_EXTERNAL_V1", shell("cat /sdcard/Android/data/$pkg/files/keep-external.txt"))
            assertEquals("Fixture cache must be absent after the Settings request", "", shell("run-as $pkg ls cache/disposable-cache.dat"))
        }
    }

    @Test fun c_immediateCancellationDoesNotClearEitherFixture() {
        fixtures.forEach { pkg ->
            shell("am force-stop $pkg")
            context.startActivity(Intent().setClassName(pkg, "digital.jinatra.usbfixture.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val deadline = System.currentTimeMillis() + 5000
            while (!shell("run-as $pkg ls cache/disposable-cache.dat").contains("disposable-cache.dat") && System.currentTimeMillis() < deadline) Thread.sleep(100)
        }
        val state = run(fixtures, cancelImmediately = true)
        assertTrue(state.results.none { it.status == CacheBatch.Status.REQUESTED })
        fixtures.forEach { pkg ->
            assertTrue(shell("run-as $pkg ls cache/disposable-cache.dat").contains("disposable-cache.dat"))
            assertEquals("TIDYRA_USB_KEEP_V1", shell("run-as $pkg cat files/keep.txt"))
        }
    }

    @Test fun d_hiddenControlsFallBackWithoutTouchingCacheOrData() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tidyra.expectCacheAutomation") == "manual")
        val first = run(fixtures)
        assertTrue(first.results.toString(), first.results.all { it.status == CacheBatch.Status.NOT_ATTEMPTED })
        assertNotNull("This test requires detected unavailability: $first", CacheAutomation.unavailableReason(context))
        val started = System.currentTimeMillis()
        val repeat = run(fixtures, reconnect = false)
        assertTrue("Known unsupported controls must fall back immediately", System.currentTimeMillis() - started < 2500)
        assertTrue(repeat.results.none { it.status == CacheBatch.Status.REQUESTED })
        fixtures.forEach { pkg ->
            assertTrue(shell("run-as $pkg ls cache/disposable-cache.dat").contains("disposable-cache.dat"))
            assertEquals("TIDYRA_USB_KEEP_V1", shell("run-as $pkg cat files/keep.txt"))
            assertEquals("TIDYRA_USB_EXTERNAL_V1", shell("cat /sdcard/Android/data/$pkg/files/keep-external.txt"))
        }
    }
}
