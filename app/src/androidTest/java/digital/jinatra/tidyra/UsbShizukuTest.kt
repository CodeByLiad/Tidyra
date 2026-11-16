package digital.jinatra.tidyra

import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import digital.jinatra.tidyra.core.shizuku.ShizukuBridge
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** Opt-in hardware checks. Mutations are confined to the disposable fixture package. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UsbShizukuTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val bridge = ShizukuBridge(context)

    @Before fun requireIsolatedOptIn() {
        assumeTrue("Use the isolated USB variant", context.packageName.endsWith(".usbtest"))
        assumeTrue("Explicit opt-in required", InstrumentationRegistry.getArguments().getString("tidyra.usbFixture") == "true")
        val deadline = System.currentTimeMillis() + 10_000
        while (bridge.state() != ShizukuBridge.State.READY && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertEquals("Grant Shizuku access to Tidyra USB Test first", ShizukuBridge.State.READY, bridge.state())
    }

    @Test fun a_protectedPackagesAreRefused() = deviceTest {
        assertFalse(bridge.uninstall(context.packageName).ok)
        assertFalse(bridge.uninstall("com.android.settings").ok)
        assertFalse(bridge.clearCache(context.packageName).ok)
    }

    @Test fun a_remoteCommandWaitsForCompletion() = deviceTest {
        val result = bridge.exec("sh", "-c", "sleep 0.2; printf USB_COMMAND_COMPLETED; exit 7")
        assertEquals("Remote process output was lost: ${result.stderr}", "USB_COMMAND_COMPLETED", result.stdout)
        assertEquals("Remote exit code must be returned after completion", 7, result.exitCode)
    }

    @Test fun b_restrictedFoldersAreMeasured() = deviceTest {
        requireFixture()
        val probe = bridge.exec("du", "-k", "-d", "1", "/storage/emulated/0/Android/data/$FIXTURE")
        assertTrue("Fixture-only du probe: exit=${probe.exitCode}, out=${probe.stdout}, err=${probe.stderr}", probe.ok)
        val result = bridge.restrictedFolders()
        assertTrue("Disposable fixture's restricted folder was not measured (count=${result.folders.size}): ${result.note}",
            result.folders.any { it.path.endsWith("/Android/data/$FIXTURE") && it.size.value > 0 })
    }

    @Test fun c_cacheClearNeverRemovesAppData() = deviceTest {
        requireFixture()
        val before = bridge.exec("run-as", FIXTURE, "cat", "files/keep.txt")
        assertTrue(before.stderr, before.ok)
        assertEquals(MARKER, before.stdout)
        assertTrue("Fixture cache must exist before clearing", bridge.exec("run-as", FIXTURE, "ls", "cache/disposable-cache.dat").ok)
        val clear = bridge.clearCache(FIXTURE)
        if (clear.ok) {
            assertFalse("Cache remained after clearing", bridge.exec("run-as", FIXTURE, "ls", "cache/disposable-cache.dat").ok)
        } else {
            assertTrue("A failure must explain the fallback", clear.stderr.isNotBlank())
            if (!bridge.canClearCache()) {
                val started = System.currentTimeMillis()
                assertFalse(bridge.clearCache(FIXTURE).ok)
                assertTrue("An unsupported device must not repeat the blocking command", System.currentTimeMillis() - started < 5000)
            }
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("cacheClearOutcome", if (clear.ok) "CLEARED" else "FAILED_SAFELY: ${clear.stderr}")
        })
        val after = bridge.exec("run-as", FIXTURE, "cat", "files/keep.txt")
        assertTrue(after.stderr, after.ok)
        assertEquals("App data was modified by cache clearing", MARKER, after.stdout)
        val external = bridge.exec("cat", "/storage/emulated/0/Android/data/$FIXTURE/files/keep-external.txt")
        assertTrue(external.stderr, external.ok)
        assertEquals("TIDYRA_USB_EXTERNAL_V1", external.stdout)
    }

    @Test fun d_fixtureUninstall() = deviceTest {
        requireFixture()
        assertEquals(MARKER, bridge.exec("run-as", FIXTURE, "cat", "files/keep.txt").stdout)
        val result = bridge.uninstall(FIXTURE)
        assertTrue("Fixture uninstall failed: ${result.stderr} ${result.stdout}", result.ok)
        assertTrue("Fixture still installed", bridge.exec("pm", "path", FIXTURE).stdout.isBlank())
    }

    // Run this method separately after reinstalling the fixture; confirm the OS dialog manually.
    @Test fun z_requestSystemDialog() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tidyra.systemDialog") == "true")
        requireFixture()
        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$FIXTURE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun requireFixture() {
        val info = context.packageManager.getApplicationInfo(FIXTURE, 0)
        assertEquals("Refuse a package that is not the dedicated fixture", "Tidyra USB Fixture", context.packageManager.getApplicationLabel(info).toString())
    }

    private companion object {
        const val FIXTURE = "digital.jinatra.usbfixture"
        const val MARKER = "TIDYRA_USB_KEEP_V1"
    }
}
