package digital.jinatra.tidyra

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import digital.jinatra.tidyra.core.organize.Organizer
import digital.jinatra.tidyra.core.preview.LocalImageDecoder
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class OrganizePreviewTest {
    @Test fun localPreviewAndCustomAlbumUndoPreserveImage() = deviceTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".usbtest"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("tidyra.organiseFixture") == "true")
        val root = File(context.getExternalFilesDir(null), "organise-preview-qa")
        check(!root.exists()) { "Preserve an existing fixture for review" }
        val source = File(root, "Pictures/qa-colours.png").apply { parentFile!!.mkdirs() }
        val bitmap = Bitmap.createBitmap(480, 240, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        for (x in 0 until 240) for (y in 0 until 240) bitmap.setPixel(x, y, Color.RED)
        source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        val preview = LocalImageDecoder.load(source, 120)!!
        assertEquals(120, preview.width)
        assertEquals(60, preview.height)
        assertEquals(Color.RED, preview.getPixel(10, 10))
        assertEquals(Color.BLUE, preview.getPixel(110, 10))
        val corrupt = File(root, "bad.png").apply { writeText("not an image") }
        assertNull(LocalImageDecoder.load(corrupt, 120))
        val history = File(context.filesDir, "organise-preview-qa/history.json")
        val organizer = Organizer(history)
        val move = Organizer.retarget(organizer.plan(source.parentFile!!).moves.single(), File(root, "Albums/Chosen album"), root)
        assertFalse(move.to.exists())
        assertTrue(source.exists())
        assertEquals(1, organizer.apply(Organizer.Plan(listOf(move), emptyList())).moved.size)
        assertNotNull(LocalImageDecoder.load(move.to, 120))
        val restarted = Organizer(history)
        assertEquals(1, restarted.undo(restarted.pending()!!))
        assertTrue(source.isFile)
        assertFalse(move.to.exists())
        Unit
    }
}
