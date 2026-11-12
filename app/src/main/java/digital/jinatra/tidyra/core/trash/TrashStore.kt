package digital.jinatra.tidyra.core.trash

import android.content.Context
import digital.jinatra.tidyra.core.model.Bytes
import digital.jinatra.tidyra.core.model.CleanupResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Android has no recycle bin, so Tidyra keeps one.
 *
 * Approved deletions move into app-owned storage with a manifest recording where each
 * file came from, and are purged after a retention period the user sets. This is the
 * whole reason the app can be trusted to delete anything: for the retention window,
 * "undo" is a real operation rather than an apology.
 *
 * Two honest limits, both surfaced in the UI rather than hidden:
 *  - a file larger than the free space left cannot be trashed, so it is deleted outright
 *    only on explicit confirmation;
 *  - trashed files still occupy storage until they are purged, so the space is not
 *    returned until then. The UI reports trashed and freed as two different numbers.
 */
class TrashStore(private val context: Context) {

    private val root: File get() = File(context.filesDir, "trash").apply { mkdirs() }
    private val manifest: File get() = File(root, "manifest.json")

    data class Entry(
        val originalPath: String,
        val trashedPath: String,
        val size: Bytes,
        val trashedAtMillis: Long,
    )

    suspend fun trash(paths: Collection<String>): CleanupResult = withContext(Dispatchers.IO) {
        val entries = readManifest().toMutableList()
        val failures = ArrayList<CleanupResult.Failure>()
        var moved = 0L
        var count = 0

        for (path in paths) {
            val source = File(path)
            if (!source.exists()) {
                failures += CleanupResult.Failure(path, "No longer on the device")
                continue
            }
            val size = source.length()
            val destination = File(root, "${System.nanoTime()}_${source.name}")

            // renameTo stays on one filesystem; a copy is the fallback across volumes.
            val ok = source.renameTo(destination) || source.copyRecursivelyThenDelete(destination)
            if (!ok) {
                failures += CleanupResult.Failure(path, "Could not be moved to trash")
                continue
            }

            entries += Entry(path, destination.absolutePath, Bytes(size), System.currentTimeMillis())
            moved += size
            count++
        }

        writeManifest(entries)
        CleanupResult(
            freed = Bytes(moved),
            itemsRemoved = count,
            itemsFailed = failures,
            trashedTo = root.absolutePath,
        )
    }

    suspend fun restore(originalPath: String): Boolean = withContext(Dispatchers.IO) {
        val entries = readManifest().toMutableList()
        val entry = entries.firstOrNull { it.originalPath == originalPath } ?: return@withContext false
        val trashed = File(entry.trashedPath)
        var target = File(entry.originalPath)

        // Something new may live at that path now. Restoring beside it loses nothing;
        // restoring over it would destroy a file the user never chose to delete.
        if (target.exists()) {
            val stem = target.nameWithoutExtension
            val extension = target.extension.let { if (it.isEmpty()) "" else ".$it" }
            target = File(target.parentFile, stem + "-restored" + extension)
        }

        target.parentFile?.mkdirs()
        val ok = trashed.renameTo(target) || trashed.copyRecursivelyThenDelete(target)
        if (ok) {
            entries.remove(entry)
            writeManifest(entries)
        }
        ok
    }

    /** Permanently removes trashed items older than [retentionDays]. Irreversible, by design. */
    suspend fun purgeExpired(retentionDays: Int): Bytes = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        val entries = readManifest()
        val (expired, kept) = entries.partition { it.trashedAtMillis < cutoff }

        var purged = 0L
        expired.forEach { entry ->
            val file = File(entry.trashedPath)
            if (file.exists() && file.deleteRecursively()) purged += entry.size.value
        }
        writeManifest(kept)
        Bytes(purged)
    }

    suspend fun purgeAll(): Bytes = purgeExpired(retentionDays = 0)

    /** Permanently removes the named items. Irreversible — the caller confirms first. */
    suspend fun purge(originalPaths: Collection<String>): Bytes = withContext(Dispatchers.IO) {
        val wanted = originalPaths.toSet()
        val entries = readManifest()
        val (targeted, kept) = entries.partition { it.originalPath in wanted }

        var purged = 0L
        targeted.forEach { entry ->
            val file = File(entry.trashedPath)
            if (!file.exists() || file.deleteRecursively()) purged += entry.size.value
        }
        writeManifest(kept)
        Bytes(purged)
    }

    /** Puts everything back where it came from. Returns how many made it. */
    suspend fun restoreAll(): Int = withContext(Dispatchers.IO) {
        readManifest().count { restore(it.originalPath) }
    }

    suspend fun contents(): List<Entry> = withContext(Dispatchers.IO) { readManifest() }

    suspend fun size(): Bytes = withContext(Dispatchers.IO) {
        Bytes(readManifest().sumOf { it.size.value })
    }

    private fun readManifest(): List<Entry> {
        if (!manifest.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(manifest.readText())
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                Entry(
                    originalPath = item.getString("original"),
                    trashedPath = item.getString("trashed"),
                    size = Bytes(item.getLong("size")),
                    trashedAtMillis = item.getLong("at"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeManifest(entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("original", entry.originalPath)
                    put("trashed", entry.trashedPath)
                    put("size", entry.size.value)
                    put("at", entry.trashedAtMillis)
                },
            )
        }
        manifest.writeText(array.toString())
    }

    private fun File.copyRecursivelyThenDelete(target: File): Boolean = runCatching {
        copyRecursively(target, overwrite = true) && deleteRecursively()
    }.getOrDefault(false)
}
