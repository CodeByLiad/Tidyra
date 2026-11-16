package digital.jinatra.tidyra

import androidx.test.platform.app.InstrumentationRegistry
import digital.jinatra.tidyra.core.organize.Organizer
import digital.jinatra.tidyra.core.safety.SafeFiles
import digital.jinatra.tidyra.core.trash.TrashStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Run prepare and recover in separate instrumentation sessions with force-stop between. */
class ReleaseRecoveryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val qa get() = File(context.filesDir, "release-recovery-qa")
    private fun optIn() {
        assumeTrue(context.packageName.endsWith(".usbtest"))
        assumeTrue(InstrumentationRegistry.getArguments().getString("tidyra.recoveryFixture") == "true")
    }

    @Test fun prepareForRestart() = deviceTest {
        optIn()
        check(!qa.exists()) { "Recovery fixture already exists; preserve it for inspection" }
        val folder = File(context.getExternalFilesDir(null), "release-recovery-qa/Download").apply { mkdirs() }
        File(folder, "invoice.txt").writeText("TIDYRA_PERSISTENT_UNDO")
        val organizer = Organizer(File(qa, "history.json"))
        assertEquals(1, organizer.apply(organizer.plan(folder)).moved.size)
        val trashRoot = File(qa, "trash").apply { mkdirs() }
        val source = File(qa, "source.txt").apply { writeText("TIDYRA_COMPLETE_SOURCE") }
        File(trashRoot, "partial-payload").writeText("TIDYRA")
        val operations = File(trashRoot, "operations").apply { mkdirs() }
        File(operations, "partial.json").writeText(JSONObject()
            .put("original", source.path).put("trashed", File(trashRoot, "partial-payload").path)
            .put("size", source.length()).put("at", 1).put("digest", SafeFiles.digest(source)).toString())
        File(trashRoot, "legacy-payload").writeText("TIDYRA_UNIQUE_RECOVERY")
        assertTrue(File(folder, "Documents/invoice.txt").isFile)
    }

    @Test fun recoverAfterRestart() = deviceTest {
        optIn()
        val folder = File(context.getExternalFilesDir(null), "release-recovery-qa/Download")
        val original = File(folder, "invoice.txt").apply { writeText("TIDYRA_NEW_ORIGINAL") }
        val organizer = Organizer(File(qa, "history.json"))
        val pending = organizer.pending()!!
        assertEquals(1, organizer.undo(pending))
        assertEquals("TIDYRA_NEW_ORIGINAL", original.readText())
        assertEquals("TIDYRA_PERSISTENT_UNDO", File(folder, "invoice-restored.txt").readText())
        assertNull(Organizer(File(qa, "history.json")).pending())
        val trashRoot = File(qa, "trash")
        val store = TrashStore(trashRoot)
        val entry = store.contents().single()
        assertFalse(File(trashRoot, "partial-payload").exists())
        assertEquals("TIDYRA_COMPLETE_SOURCE", File(qa, "source.txt").readText())
        assertNotNull(entry.recoveryNote)
        assertEquals(0, store.purgeExpired(1).count)
        assertTrue(store.restore(entry.id))
        assertEquals("TIDYRA_UNIQUE_RECOVERY", File(entry.originalPath).readText())
        assertTrue(store.contents().isEmpty())
    }
}
