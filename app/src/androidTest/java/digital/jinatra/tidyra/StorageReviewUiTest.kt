package digital.jinatra.tidyra

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import digital.jinatra.tidyra.core.model.*
import digital.jinatra.tidyra.core.scan.DuplicateFinder
import digital.jinatra.tidyra.core.scan.SharedStorageScanner
import digital.jinatra.tidyra.core.shizuku.ShizukuBridge
import digital.jinatra.tidyra.core.trash.TrashStore
import digital.jinatra.tidyra.ui.MainActivity
import digital.jinatra.tidyra.ui.detail.FindingDetailScreen
import digital.jinatra.tidyra.ui.station.StationScreen
import digital.jinatra.tidyra.ui.station.StationState
import digital.jinatra.tidyra.ui.theme.TidyraTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** Only generated images and a private isolated Trash ledger; never production storage. */
class StorageReviewUiTest {
    @Test fun labelledStorageAndDuplicateComparison() = deviceTest {
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        assumeTrue(context.packageName.endsWith(".usbtest"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("tidyra.reviewUi") == "true")
        val activity = inst.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val ui = inst.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun nodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(node) +
            (0 until node.childCount).flatMap { node.getChild(it)?.let(::nodes).orEmpty() }
        fun visible(): List<AccessibilityNodeInfo> {
            ui.clearCache()
            val root = ui.rootInActiveWindow ?: return emptyList()
            if (root.packageName?.toString() != context.packageName || !root.refresh()) return emptyList()
            return nodes(root).filter { it.isVisibleToUser }
        }
        fun find(text: String): AccessibilityNodeInfo {
            repeat(50) {
                visible().firstOrNull { it.text?.toString() == text }?.let { return it }
                Thread.sleep(100)
            }
            error("Missing review UI text: $text")
        }
        fun click(text: String) {
            var node = find(text)
            while (!node.isClickable) node = node.parent ?: error("No action for $text")
            check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            inst.waitForIdleSync()
        }
        fun scrollTo(text: String) {
            var frames = 0
            while (visible().none { it.isScrollable } && frames++ < 50) Thread.sleep(100)
            repeat(8) {
                if (visible().any { it.text?.toString() == text }) return
                val list = visible().firstOrNull { it.isScrollable } ?: error("No review list")
                check(list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
                Thread.sleep(180)
            }
            find(text)
        }
        fun capture(name: String) {
            check(visible().isNotEmpty())
            ui.takeScreenshot()!!.let { bmp ->
                File(context.cacheDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
            }
        }
        fun render(dark: Boolean = false, scale: Float = 1f, content: @Composable () -> Unit) {
            inst.runOnMainSync {
                activity.setContent {
                    key(content) {
                        val density = LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                            TidyraTheme(dark) { Surface { content() } }
                        }
                    }
                }
            }
            inst.waitForIdleSync()
        }
        val gb = 1024L * 1024 * 1024
        val categories = linkedMapOf(StorageCategory.APPS to 20L, StorageCategory.APP_DATA to 15L,
            StorageCategory.PHOTOS to 5L, StorageCategory.DOCUMENTS to 2L)
        val state = StationState(snapshot = StorageSnapshot(Bytes(100 * gb), Bytes(40 * gb), emptyMap(), 0),
            containers = categories.map { StationState.Container(it.key, Bytes(it.value * gb), Bytes.ZERO) },
            hasAllFilesAccess = true, hasUsageAccess = true, shizuku = ShizukuBridge.State.READY)
        val opened = AtomicReference<StorageCategory>()
        fun storage(dark: Boolean) = render(dark) {
            StationScreen(state, {}, { _, _ -> }, {}, {}, {}, onOpenFinding = {},
                onOpenCategory = { opened.set(it) }, modifier = Modifier.statusBarsPadding())
        }
        storage(false)
        scrollTo("System & unmeasured")
        find("18.0 GB")
        find("40.0 GB")
        assertTrue(visible().any { it.contentDescription?.contains("1: Apps") == true })
        capture("storage-breakdown-light")
        val appRow = visible().first { it.contentDescription?.startsWith("1. Apps,") == true }
        assertTrue(appRow.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        assertEquals(StorageCategory.APPS, opened.get())
        storage(true)
        scrollTo("System & unmeasured")
        capture("storage-breakdown-dark")

        val root = File(context.cacheDir, "duplicate-review-${UUID.randomUUID()}").apply { mkdirs() }
        val original = File(root, "Camera/Original.png").apply { parentFile!!.mkdirs() }
        val image = Bitmap.createBitmap(480, 240, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.BLUE)
        for (x in 0 until 240) for (y in 0 until 240) image.setPixel(x, y, Color.RED)
        original.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        val files = listOf(original, File(root, "Downloads/Copy one.png"), File(root, "Chat/Copy two.png"))
        files.drop(1).forEach { it.parentFile!!.mkdirs(); original.copyTo(it) }
        val hash = MessageDigest.getInstance("SHA-256").digest(original.readBytes()).toList()
        val finder = DuplicateFinder()
        val groups = finder.find(files.map { SharedStorageScanner.FileEntry(it, it.length(), it.lastModified(), StorageCategory.PHOTOS) }, minSize = 1)
        assertEquals(1, groups.size)
        val finding = finder.toFinding(groups)!!
        val kept = groups.single().keep
        val allowed = finding.items.map { it.path }.toSet()
        val selected = mutableStateOf(emptySet<String>())
        render {
            FindingDetailScreen(finding, selected.value,
                onToggleItem = { path, checked ->
                    check(path in allowed && path != kept.path)
                    selected.value = if (checked) selected.value + path else selected.value - path
                }, onSelectAll = { all -> selected.value = if (all) allowed else emptySet() },
                onBack = {}, modifier = Modifier.statusBarsPadding())
        }
        scrollTo("Keeping this copy")
        find("Select copy")
        // Actual native checkboxes approve extra copies; the kept file has no removal control.
        click("Select copy")
        assertEquals(1, selected.value.size)
        assertFalse(kept.path in selected.value)
        capture("duplicate-comparison-light")
        val imageNode = visible().first { it.contentDescription?.startsWith("Image preview of") == true }
        var previewControl = imageNode
        while (!previewControl.isClickable) previewControl = previewControl.parent ?: error("Preview action missing")
        assertTrue(previewControl.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        find("Close preview")
        capture("duplicate-preview")
        click("Close preview")
        scrollTo("Select copy")
        click("Select copy")
        assertEquals(allowed, selected.value)
        render(dark = true, scale = 1.5f) {
            FindingDetailScreen(finding, selected.value, { _, _ -> }, {}, onBack = {}, modifier = Modifier.statusBarsPadding())
        }
        scrollTo("Keeping this copy")
        capture("duplicate-comparison-dark-large")

        // Mutate only this generated directory using its own private ledger.
        check(selected.value == allowed && allowed.all { File(it).canonicalPath.startsWith(root.canonicalPath + File.separator) })
        val trash = TrashStore(File(root, "isolated-trash"))
        val outcome = trash.trash(selected.value.toList(), finding.items.associateBy { it.path })
        assertEquals(2, outcome.itemsRemoved)
        assertTrue(kept.isFile)
        assertEquals(hash, MessageDigest.getInstance("SHA-256").digest(kept.readBytes()).toList())
        trash.contents().forEach { assertTrue(trash.restore(it.id)) }
        files.forEach { assertEquals(hash, MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList()) }
        assertTrue(trash.contents().isEmpty())
        root.deleteRecursively()
        Unit
    }
}
