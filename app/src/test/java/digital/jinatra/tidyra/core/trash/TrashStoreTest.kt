package digital.jinatra.tidyra.core.trash

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrashStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun store() = TrashStore(File(temp.root, "trash"))
    private fun source(name: String = "photo.jpg", text: String = "original") =
        File(temp.root, name).apply { writeText(text) }

    @Test fun `file changed since scan is left alone`() = runBlocking {
        val source = source()
        val item = digital.jinatra.tidyra.core.model.Finding.Item(source.path,
            digital.jinatra.tidyra.core.model.Bytes(source.length()), source.lastModified())
        source.writeText("a replacement with different content")
        val result = store().trash(listOf(source.path), mapOf(source.path to item))
        assertEquals(0, result.itemsRemoved)
        assertEquals(1, result.itemsFailed.size)
        assertEquals("a replacement with different content", source.readText())
    }

    @Test fun `duplicate is left alone if kept copy disappears`() = runBlocking {
        val source = source()
        val item = digital.jinatra.tidyra.core.model.Finding.Item(source.path,
            digital.jinatra.tidyra.core.model.Bytes(source.length()), source.lastModified(),
            keptCounterpart = File(temp.root, "missing.jpg").path)
        val result = store().trash(listOf(source.path), mapOf(source.path to item))
        assertEquals(0, result.itemsRemoved)
        assertEquals("original", source.readText())
    }

    @Test fun `restore preserves original and every existing restored name`() = runBlocking {
        val source = source()
        val store = store()
        store.trash(listOf(source.path))
        source.writeText("new original")
        source("photo-restored.jpg", "first restored")
        source("photo-restored-2.jpg", "second restored")
        assertTrue(store.restore(store.contents().single().id))
        assertEquals("new original", source.readText())
        assertEquals("first restored", File(temp.root, "photo-restored.jpg").readText())
        assertEquals("second restored", File(temp.root, "photo-restored-2.jpg").readText())
        assertEquals("original", File(temp.root, "photo-restored-3.jpg").readText())
        assertTrue(store.contents().isEmpty())
    }

    @Test fun `two versions of the same path can be restored independently`() = runBlocking {
        val source = source(text = "version one")
        val store = store()
        store.trash(listOf(source.path))
        source.writeText("version two")
        store.trash(listOf(source.path))
        val entries = store.contents()
        assertEquals(2, entries.map { it.id }.distinct().size)
        assertTrue(store.restore(entries[1].id))
        assertEquals("version two", source.readText())
        assertEquals(entries[0].id, store.contents().single().id)
        assertTrue(store.restore(entries[0].id))
        assertEquals("version one", File(temp.root, "photo-restored.jpg").readText())
    }

    @Test fun `failed ledger commit leaves the original intact`() = runBlocking {
        val source = source()
        File(temp.root, "trash/manifest.pending").mkdirs()
        val result = store().trash(listOf(source.path))
        assertEquals(0, result.itemsRemoved)
        assertEquals(1, result.itemsFailed.size)
        assertEquals("original", source.readText())
        assertTrue(store().contents().isEmpty())
        assertTrue(File(temp.root, "trash").listFiles()!!.all { it.name in setOf("manifest.pending", "operations") })
    }

    @Test fun `damaged ledger is preserved and blocks new mutations`() = runBlocking {
        val source = source()
        File(temp.root, "trash").mkdirs()
        val manifest = File(temp.root, "trash/manifest.json").apply { writeText("damaged") }
        assertTrue(runCatching { store().trash(listOf(source.path)) }.isFailure)
        assertEquals("damaged", manifest.readText())
        assertEquals("original", source.readText())
    }

    @Test fun `purge refuses outside paths and retains failed entries`() = runBlocking {
        val source = source()
        val store = store()
        store.trash(listOf(source.path))
        val outside = source("keep.txt", "keep me")
        val manifest = File(temp.root, "trash/manifest.json")
        val array = JSONArray(manifest.readText())
        array.getJSONObject(0).put("trashed", outside.path)
        manifest.writeText(array.toString())
        val result = store.purge(listOf(outside.path))
        assertEquals(0, result.count)
        assertEquals(0L, result.bytes.value)
        assertEquals(1, result.failures.size)
        assertTrue(store.contents().any { it.id == outside.path })
        assertEquals("keep me", outside.readText())
    }

    @Test fun `folder that gained contents after scan is never removed`() = runBlocking {
        val dir = File(temp.root, "folder").apply { mkdir() }
        File(dir, "new.txt").writeText("new content")
        val result = store().trash(listOf(dir.path))
        assertEquals(0, result.itemsRemoved)
        assertEquals("new content", File(dir, "new.txt").readText())
    }

    @Test fun `concurrent stores retain every recovery record`() = runBlocking {
        val paths = (1..8).map { source("item-$it.txt").path }
        paths.map { path -> async { store().trash(listOf(path)) } }.awaitAll()
        assertEquals(8, store().contents().size)
        assertEquals(8, store().restoreAll())
        assertTrue(paths.all { File(it).readText() == "original" })
    }

    @Test fun `keep until emptied skips automatic expiry but manual purge still works`() = runBlocking {
        val source = source()
        val store = store()
        store.trash(listOf(source.path))
        val manifest = File(temp.root, "trash/manifest.json")
        val array = JSONArray(manifest.readText())
        array.getJSONObject(0).put("at", 1)
        manifest.writeText(array.toString())
        assertEquals(0, store.purgeExpired(0).count)
        assertEquals(1, store.contents().size)
        val result = store.purgeAll()
        assertEquals(1, result.count)
        assertEquals("original".length.toLong(), result.bytes.value)
        assertTrue(store.contents().isEmpty())
    }
}
