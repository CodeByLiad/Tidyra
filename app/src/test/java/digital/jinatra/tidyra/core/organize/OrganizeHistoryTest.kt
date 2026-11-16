package digital.jinatra.tidyra.core.organize

import digital.jinatra.tidyra.core.safety.SafeFiles
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class OrganizeHistoryTest {
    @get:Rule val temp = TemporaryFolder()
    private val history get() = File(temp.root, "private/history.json")
    private fun folder() = File(temp.root, "Download").apply { mkdirs() }
    private fun record(from: File, to: File, phase: String = "prepared", restore: File? = null) =
        JSONObject().put("batch", "batch-1").put("from", from.path).put("to", to.path)
            .put("size", (if (from.exists()) from else to).length())
            .put("modified", (if (from.exists()) from else to).lastModified())
            .put("digest", SafeFiles.digest(if (from.exists()) from else to)).put("phase", phase)
            .put("restore", restore?.path ?: JSONObject.NULL)
    private fun write(record: JSONObject) { history.parentFile!!.mkdirs(); history.writeText(JSONArray().put(record).toString()) }

    @Test fun `undo survives creating a new organiser and preserves new originals`() = runBlocking {
        val dir = folder()
        val source = File(dir, "invoice.txt").apply { writeText("old invoice") }
        val first = Organizer(history)
        first.apply(first.plan(dir))
        source.writeText("new invoice")
        val restarted = Organizer(history)
        val pending = restarted.pending()!!
        assertEquals(1, restarted.undo(pending))
        assertEquals("new invoice", source.readText())
        assertEquals("old invoice", File(dir, "invoice-restored.txt").readText())
        assertNull(Organizer(history).pending())
    }

    @Test fun `completed rename is recoverable if process died before marking it complete`() = runBlocking {
        val source = File(folder(), "invoice.txt").apply { writeText("invoice") }
        val target = File(source.parentFile, "Documents/invoice.txt")
        write(record(source, target))
        target.parentFile!!.mkdirs()
        Files.move(source.toPath(), target.toPath())
        source.writeText("new invoice")
        val restarted = Organizer(history)
        assertEquals(1, restarted.pending()!!.moved.size)
        assertEquals(1, restarted.undo(restarted.pending()!!))
        assertEquals("new invoice", source.readText())
        assertEquals("invoice", File(source.parentFile, "invoice-restored.txt").readText())
    }

    @Test fun `restart after undo rename does not repeat a completed undo`() = runBlocking {
        val source = File(folder(), "invoice.txt").apply { writeText("invoice") }
        val target = File(source.parentFile, "Documents/invoice.txt")
        val operation = record(source, target, "restoring", source)
        write(operation)
        // The restored original already exists; the filed target is gone.
        assertNull(Organizer(history).pending())
        assertEquals("invoice", source.readText())
    }

    @Test fun `undo retains earlier batches after the latest one is restored`() = runBlocking {
        val dir = folder()
        File(dir, "first.txt").writeText("first")
        val organizer = Organizer(history)
        organizer.apply(organizer.plan(dir))
        File(dir, "second.txt").writeText("second")
        organizer.apply(organizer.plan(dir))
        val restarted = Organizer(history)
        assertEquals("second.txt", restarted.pending()!!.moved.single().from.name)
        assertEquals(1, restarted.undo(restarted.pending()!!))
        assertEquals("first.txt", restarted.pending()!!.moved.single().from.name)
    }

    @Test fun `damaged history blocks changes without replacing the recovery record`() = runBlocking {
        val source = File(folder(), "invoice.txt").apply { writeText("invoice") }
        history.parentFile!!.mkdirs(); history.writeText("damaged")
        val organizer = Organizer(history)
        assertTrue(runCatching { organizer.apply(organizer.plan(source.parentFile!!)) }.isFailure)
        assertEquals("invoice", source.readText())
        assertEquals("damaged", history.readText())
    }
}
