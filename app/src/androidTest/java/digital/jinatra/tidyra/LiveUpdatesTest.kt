package digital.jinatra.tidyra

import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import digital.jinatra.tidyra.core.shizuku.ShizukuBridge
import digital.jinatra.tidyra.core.trash.TrashStore
import digital.jinatra.tidyra.ui.MainActivity
import digital.jinatra.tidyra.ui.apps.AppsViewModel
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class LiveUpdatesTest {
    @Test fun uninstallUpdatesWithoutRescanAndPrepareDisposableTrashPreview() = deviceTest {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".usbtest"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("tidyra.liveUpdateFixture") == "true")
        val bridge = ShizukuBridge(context)
        assertEquals(ShizukuBridge.State.READY, bridge.state())
        val fixture = "digital.jinatra.usbfixture.peer"
        assertEquals("Tidyra Cache Peer", context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(fixture, 0)).toString())
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        lateinit var model: AppsViewModel
        instrumentation.runOnMainSync { model = ViewModelProvider(activity)[AppsViewModel::class.java]; model.refreshInstalled() }
        var deadline = System.currentTimeMillis() + 10_000
        while (model.state.value.installed.none { it.packageName == fixture } && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue(model.state.value.installed.any { it.packageName == fixture })
        assertTrue(bridge.uninstall(fixture).ok)
        deadline = System.currentTimeMillis() + 10_000
        while (model.state.value.installed.any { it.packageName == fixture } && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertFalse("Uninstalled row remained without a scan", model.state.value.installed.any { it.packageName == fixture })
        val store = TrashStore(context)
        assertTrue("Do not alter existing QA trash", store.contents().isEmpty())
        val directory = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "tidyra-ui-qa-${java.util.UUID.randomUUID()}")
        assertTrue(directory.mkdir())
        val document = File(directory, "Example document.txt").apply { writeText("TIDYRA_DISPOSABLE_UI_FIXTURE\n".repeat(100)) }
        val folder = File(directory, "Empty folder").apply { assertTrue(mkdir()) }
        val progress = mutableListOf<digital.jinatra.tidyra.core.model.OperationProgress>()
        val result = store.trash(listOf(document.path, folder.path), onProgress = { progress += it })
        assertTrue(result.itemsFailed.toString(), result.itemsFailed.isEmpty())
        assertEquals(2, store.contents().size)
        assertEquals(2, progress.last().completed)
        assertTrue(progress.any { it.bytesDone != null })
    }
}
