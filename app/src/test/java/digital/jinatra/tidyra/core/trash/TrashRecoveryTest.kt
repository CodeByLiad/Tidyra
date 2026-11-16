package digital.jinatra.tidyra.core.trash

import digital.jinatra.tidyra.core.safety.SafeFiles
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrashRecoveryTest {
    @get:Rule val temp = TemporaryFolder()
    private val root get() = File(temp.root, "trash").apply { mkdirs() }
    private fun interrupt(source: File, copied: String): File {
        val target = File(root, "operation-1").apply { writeText(copied) }
        val operations = File(root, "operations").apply { mkdirs() }
        File(operations, "operation-1.json").writeText(JSONObject()
            .put("original", source.path).put("trashed", target.path).put("size", source.length())
            .put("at", 1).put("digest", SafeFiles.digest(source)).toString())
        return target
    }

    @Test fun `partial copy is cleaned only when its original is verified intact`() = runBlocking {
        val source = File(temp.root, "photo.jpg").apply { writeText("complete original") }
        val partial = interrupt(source, "complete")
        assertTrue(TrashStore(root).contents().isEmpty())
        assertFalse(partial.exists())
        assertEquals("complete original", source.readText())
    }

    @Test fun `complete interrupted copy gets a durable visible recovery entry`() = runBlocking {
        val source = File(temp.root, "photo.jpg").apply { writeText("complete original") }
        val payload = interrupt(source, source.readText())
        source.delete()
        val store = TrashStore(root)
        val entry = store.contents().single()
        assertNotNull(entry.recoveryNote)
        assertEquals(0, store.purgeExpired(7).count)
        assertTrue(payload.exists())
        assertFalse(source.exists())
        assertEquals(entry, TrashStore(root).contents().single())
    }

    @Test fun `partial copy with unavailable original is preserved for explicit recovery`() = runBlocking {
        val source = File(temp.root, "photo.jpg").apply { writeText("complete original") }
        interrupt(source, "complete")
        source.delete()
        val store = TrashStore(root)
        val entry = store.contents().single()
        assertTrue(entry.recoveryNote!!.contains("incomplete"))
        assertEquals(8L, entry.size.value)
        assertTrue(store.restore(entry.id))
        assertEquals("complete", source.readText())
    }

    @Test fun `unindexed legacy bytes are visible and restore to the recovery folder`() = runBlocking {
        File(root, "legacy-copy").writeText("unique old bytes")
        val store = TrashStore(root)
        val entry = store.contents().single()
        assertNotNull(entry.recoveryNote)
        assertTrue(store.restore(entry.id))
        assertEquals("unique old bytes", File(entry.originalPath).readText())
    }

    @Test fun `legacy recovery folders restore nested files without losing contents`() = runBlocking {
        val nested = File(root, "legacy-folder/nested").apply { mkdirs() }
        File(nested, "notes.txt").writeText("important notes")
        val store = TrashStore(root)
        val entry = store.contents().single()
        assertTrue(store.restore(entry.id))
        assertEquals("important notes", File(entry.originalPath, "nested/notes.txt").readText())
        assertTrue(store.contents().isEmpty())
    }
}
