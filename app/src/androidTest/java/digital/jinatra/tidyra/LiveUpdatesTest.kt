package digital.jinatra.tidyra

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import digital.jinatra.tidyra.core.shizuku.ShizukuBridge
import digital.jinatra.tidyra.core.trash.TrashStore
import digital.jinatra.tidyra.ui.MainActivity
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class LiveUpdatesTest {
    @Test fun uninstallUpdatesWithoutRescanAndReportsTrashProgress() = deviceTest {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(context.packageName.endsWith(".usbtest"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("tidyra.liveUpdateFixture") == "true")
        val bridge = ShizukuBridge(context)
        assertEquals(ShizukuBridge.State.READY, bridge.state())
        val fixture = "digital.jinatra.usbfixture.peer"
        assertEquals("Tidyra Cache Peer", context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(fixture, 0)).toString())
        instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val ui = instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun root(): android.view.accessibility.AccessibilityNodeInfo {
            val until = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < until) {
                val node = ui.rootInActiveWindow
                if (node?.packageName?.toString() == context.packageName && node.refresh()) return node
                Thread.sleep(100)
            }
            error("QA app did not expose its active window; no other UI was inspected")
        }
        fun nodes(node: android.view.accessibility.AccessibilityNodeInfo): List<android.view.accessibility.AccessibilityNodeInfo> =
            listOf(node) + (0 until node.childCount).flatMap { index -> node.getChild(index)?.let(::nodes).orEmpty() }
        var appsTab: android.view.accessibility.AccessibilityNodeInfo? = null
        var frameDeadline = System.currentTimeMillis() + 10_000
        while (appsTab == null && System.currentTimeMillis() < frameDeadline) {
            appsTab = nodes(root()).firstOrNull { it.text?.toString() == "Apps" }
            if (appsTab == null) Thread.sleep(100)
        }
        var appControl = appsTab ?: error("Apps tab did not appear")
        while (!appControl.isClickable) appControl = appControl.parent ?: error("Apps tab is not actionable")
        assertTrue(appControl.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        var searchNode: android.view.accessibility.AccessibilityNodeInfo? = null
        frameDeadline = System.currentTimeMillis() + 10_000
        while (searchNode == null && System.currentTimeMillis() < frameDeadline) {
            searchNode = nodes(root()).firstOrNull { it.isEditable }
            if (searchNode == null) Thread.sleep(100)
        }
        val search = searchNode ?: error("App search field did not appear")
        assertTrue(search.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
            putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Tidyra Cache Peer")
        }))
        fun visible() = nodes(root()).any { it.text?.toString() == fixture && it.isVisibleToUser }
        var deadline = System.currentTimeMillis() + 10_000
        while (!visible() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue("Disposable app must be visible before uninstall", visible())
        assertTrue(bridge.uninstall(fixture).ok)
        deadline = System.currentTimeMillis() + 10_000
        while (visible() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertFalse("Uninstalled row remained without a scan", visible())
        // Use a separate ledger; repeated tests must never empty the app's existing Trash.
        val ledger = File(context.cacheDir, "trash-progress-${java.util.UUID.randomUUID()}")
        val store = TrashStore(ledger)
        assertTrue(store.contents().isEmpty())
        val directory = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "tidyra-ui-qa-${java.util.UUID.randomUUID()}")
        assertTrue(directory.mkdir())
        val document = File(directory, "Example document.txt").apply { writeText("TIDYRA_DISPOSABLE_UI_FIXTURE\n".repeat(100)) }
        val folder = File(directory, "Empty folder").apply { assertTrue(mkdir()) }
        val progress = mutableListOf<digital.jinatra.tidyra.core.model.OperationProgress>()
        store.trash(listOf(document.path, folder.path), onProgress = { progress += it })
        assertEquals(2, store.contents().size)
        assertEquals(2, progress.last().completed)
        assertTrue(progress.any { it.bytesDone != null })
        store.contents().forEach { assertTrue(store.restore(it.id)) }
        assertTrue(store.contents().isEmpty())
        assertEquals("TIDYRA_DISPOSABLE_UI_FIXTURE\n".repeat(100), document.readText())
        assertTrue(folder.isDirectory)
    }
}
