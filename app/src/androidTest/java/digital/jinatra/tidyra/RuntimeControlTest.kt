package digital.jinatra.tidyra

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import digital.jinatra.tidyra.core.shizuku.ShizukuBridge
import digital.jinatra.tidyra.core.runtime.RuntimeInventory
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class RuntimeControlTest {
    @Test fun fixtureServiceAndAppStopPreserveData() = deviceTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".usbtest"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("tidyra.runtimeFixture") == "true")
        val bridge = ShizukuBridge(context)
        val deadline = System.currentTimeMillis() + 10000
        while (bridge.state() != ShizukuBridge.State.READY && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertEquals(ShizukuBridge.State.READY, bridge.state())
        val pkg = "digital.jinatra.usbfixture"
        val info = context.packageManager.getApplicationInfo(pkg, 0)
        assertEquals("Tidyra USB Fixture", context.packageManager.getApplicationLabel(info).toString())
        val installed = RuntimeInventory(context).installed()
        assertNotNull(installed.first { it.packageName == "com.android.settings" }.stopBlockedReason)
        assertFalse(bridge.stop("com.android.settings").ok)
        assertFalse(bridge.stop(context.packageName).ok)
        assertFalse(bridge.stop(pkg, "com.android.settings/.Fake").ok)
        val before = bridge.running()
        assertTrue(before.processesAvailable)
        assertTrue(before.servicesAvailable)
        val app = installed.first { it.packageName == pkg }
        assertNull(app.stopBlockedReason)
        assertTrue(app.processes(before).isNotEmpty())
        val service = before.services.single { it.packageName == pkg && it.component.endsWith(".FixtureService") }
        val stopService = bridge.stop(pkg, service.component)
        assertTrue("Service owner stop failed: ${stopService.stdout} ${stopService.stderr}", stopService.ok)
        assertFalse(bridge.running().services.any { it.component == service.component })
        assertTrue(app.processes(bridge.running()).isEmpty())
        assertEquals("TIDYRA_USB_KEEP_V1", bridge.exec("run-as", pkg, "cat", "files/keep.txt").stdout)
        assertTrue(bridge.exec("am", "start", "-W", "-n", "$pkg/.MainActivity").ok)
        assertTrue(app.processes(bridge.running()).isNotEmpty())
        val stopApp = bridge.stop(pkg)
        assertTrue("Force stop failed: ${stopApp.stderr}", stopApp.ok)
        assertTrue(app.processes(bridge.running()).isEmpty())
        assertEquals("TIDYRA_USB_KEEP_V1", bridge.exec("run-as", pkg, "cat", "files/keep.txt").stdout)
        assertTrue("An expired service row must not block an explicitly approved owner-app stop", bridge.stop(pkg, service.component).ok)
        Unit
    }
}
