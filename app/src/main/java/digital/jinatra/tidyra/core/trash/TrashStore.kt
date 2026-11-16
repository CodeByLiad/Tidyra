package digital.jinatra.tidyra.core.trash

import android.content.Context
import digital.jinatra.tidyra.core.model.Bytes
import digital.jinatra.tidyra.core.model.CleanupResult
import digital.jinatra.tidyra.core.model.Finding
import digital.jinatra.tidyra.core.safety.Protected
import digital.jinatra.tidyra.core.safety.SafeFiles
import digital.jinatra.tidyra.core.safety.AtomicJson
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.security.MessageDigest

/** A durable undo ledger. Only a complete saved copy may replace a source file. */
class TrashStore internal constructor(private val root: File,
    private val recoveryDirectory: File = File(root.parentFile, "recovered")) {
    constructor(context: Context) : this(File(context.filesDir, "trash"),
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Tidyra Recovered"))

    private val mutex = locks.getOrPut(root.canonicalPath) { Mutex() }
    private val manifest get() = File(root, "manifest.json")

    data class Entry(val originalPath: String, val trashedPath: String, val size: Bytes, val trashedAtMillis: Long,
        val recoveryNote: String? = null) {
        // One original can be trashed repeatedly; each saved copy has its own identity.
        val id: String get() = trashedPath
    }

    data class BatchResult(val count: Int, val bytes: Bytes, val failures: List<String>)

    suspend fun trash(paths: Collection<String>, expected: Map<String, Finding.Item> = emptyMap()): CleanupResult = locked {
        root.mkdirs()
        val entries = readManifest().toMutableList()
        val failures = mutableListOf<CleanupResult.Failure>()
        var moved = 0L
        var count = 0
        for (path in paths.distinct()) {
            runCatching {
                val source = File(path)
                expected[path]?.let { item ->
                    check(source.lastModified() == item.lastModifiedMillis &&
                        (source.isDirectory || source.length() == item.size.value)) { "Changed since scan; scan again" }
                    item.keptCounterpart?.let { kept ->
                        val counterpart = File(kept)
                        check(counterpart.isFile && counterpart.length() == source.length() &&
                            digest(source).contentEquals(digest(counterpart))) { "The kept copy changed or disappeared; scan again" }
                    }
                }
                check(Protected.reason(source.canonicalPath) == null) { "Protected file; left alone" }
                check(!source.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "Already in trash" }
                val entry = Entry(path, File(root, UUID.randomUUID().toString()).absolutePath,
                    Bytes(if (source.isDirectory) 0 else source.length()), System.currentTimeMillis())
                val intent = File(File(root, "operations"), File(entry.trashedPath).name + ".json")
                // This record exists even if the process stops halfway through the copy.
                AtomicJson.write(intent, encode(entry).put("digest",
                    if (source.isDirectory) "directory" else SafeFiles.digest(source)).toString())
                SafeFiles.move(source, File(entry.trashedPath)) {
                    // Commit before removing the original, so interrupted moves are recoverable.
                    writeManifest(entries + entry)
                    entries += entry
                }
                moved += entry.size.value
                count++
                intent.delete()
            }.onFailure { failures += CleanupResult.Failure(path, it.message ?: "Could not move to trash") }
        }
        CleanupResult(Bytes(moved), count, failures, root.absolutePath)
    }

    suspend fun restore(id: String): Boolean = locked { restoreEntry(id) }

    private fun restoreEntry(id: String): Boolean {
        val entries = readManifest().toMutableList()
        val entry = entries.firstOrNull { it.id == id } ?: return false
        return runCatching {
            SafeFiles.move(savedFile(entry), SafeFiles.restoredTarget(File(entry.originalPath)), allowDirectoryContents = true)
            entries.remove(entry)
            writeManifest(entries)
            true
        }.getOrDefault(false)
    }

    suspend fun restoreAll(): Int = locked { readManifest().count { restoreEntry(it.id) } }
    suspend fun contents(): List<Entry> = locked { readManifest() }
    suspend fun size(): Bytes = locked { Bytes(readManifest().sumOf { it.size.value }) }

    suspend fun purgeExpired(retentionDays: Int): BatchResult = locked {
        require(retentionDays >= 0)
        if (retentionDays == 0) return@locked BatchResult(0, Bytes.ZERO, emptyList())
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays.toLong())
        // Uncertain recovered copies need a deliberate manual decision, never timed expiry.
        purgeEntries(readManifest().filter { it.recoveryNote == null && it.trashedAtMillis < cutoff }.map { it.id }.toSet())
    }

    suspend fun purgeAll(): BatchResult = locked { purgeEntries(readManifest().map { it.id }.toSet()) }
    suspend fun purge(ids: Collection<String>): BatchResult = locked { purgeEntries(ids.toSet()) }

    private fun purgeEntries(ids: Set<String>): BatchResult {
        val entries = readManifest().toMutableList()
        val failures = mutableListOf<String>()
        var bytes = 0L
        var count = 0
        for (entry in entries.toList().filter { it.id in ids }) {
            runCatching {
                val file = savedFile(entry)
                val existed = file.exists()
                // Failed removals stay in the ledger, so they can be retried or restored.
                check(!existed || file.deleteRecursively()) { "Could not delete ${File(entry.originalPath).name}" }
                if (existed) bytes += entry.size.value
                count++
                entries.remove(entry)
                writeManifest(entries)
            }.onFailure { failures += it.message ?: "Could not empty trash" }
        }
        return BatchResult(count, Bytes(bytes), failures)
    }

    private fun savedFile(entry: Entry): File = File(entry.trashedPath).also {
        check(it.canonicalFile.parentFile == root.canonicalFile) { "Invalid trash location" }
    }

    private fun readManifest(): List<Entry> {
        if (!manifest.exists()) return emptyList()
        // A damaged ledger must never be silently replaced by an empty one.
        val array = JSONArray(manifest.readText())
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            decode(item)
        }
    }

    private fun writeManifest(entries: List<Entry>) {
        root.mkdirs()
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(encode(entry))
        }
        // Retain the legacy pending filename so an interrupted old build is recognised.
        val pending = File(root, "manifest.pending")
        FileOutputStream(pending).use { output -> output.write(array.toString().toByteArray(Charsets.UTF_8)); output.fd.sync() }
        Files.move(pending.toPath(), manifest.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun encode(entry: Entry) = JSONObject().put("original", entry.originalPath).put("trashed", entry.trashedPath)
        .put("size", entry.size.value).put("at", entry.trashedAtMillis).put("recoveryNote", entry.recoveryNote ?: JSONObject.NULL)

    private fun decode(item: JSONObject): Entry {
        val entry = Entry(item.getString("original"), item.getString("trashed"),
            Bytes(item.getLong("size")), item.getLong("at"), item.optString("recoveryNote").takeIf { it.isNotEmpty() && it != "null" })
        check(File(entry.originalPath).isAbsolute && File(entry.trashedPath).isAbsolute && entry.size.value >= 0) { "Invalid trash recovery record" }
        return entry
    }

    private fun recover() {
        if (!root.exists()) return
        val entries = readManifest().toMutableList()
        val operations = File(root, "operations")
        operations.listFiles()?.filter { it.extension == "json" }?.forEach { intent ->
            // Damaged intent metadata is left alone. Its payload is recovered below with
            // an unknown-original warning instead of discarding potentially unique bytes.
            val item = runCatching { JSONObject(intent.readText()) }.getOrNull() ?: return@forEach
            val entry = runCatching { decode(item) }.getOrNull() ?: return@forEach
            val destination = runCatching { savedFile(entry) }.getOrNull() ?: return@forEach
            if (entries.any { it.id == entry.id } || !destination.exists()) { intent.delete(); return@forEach }
            val source = File(entry.originalPath)
            val hash = item.optString("digest").takeIf { it == "directory" || it.matches(Regex("[a-f0-9]{64}")) }
                ?: return@forEach
            val originalIntact = if (hash == "directory") source.isDirectory && source.listFiles()?.isEmpty() == true
                else source.isFile && source.length() == entry.size.value && SafeFiles.digest(source) == hash
            val complete = if (hash == "directory") destination.isDirectory && destination.listFiles()?.isEmpty() == true
                else destination.isFile && destination.length() == entry.size.value && SafeFiles.digest(destination) == hash
            if (originalIntact && (destination.isFile || destination.isDirectory && destination.listFiles()?.isEmpty() == true)) {
                // Only an app-owned partial copy with its verified original still intact
                // is disposable. A missing or changed original always preserves the copy.
                check(destination.delete()) { "Could not remove an interrupted redundant copy" }
            } else {
                entries += entry.copy(size = Bytes(payloadSize(destination)), recoveryNote = if (complete)
                    "Recovered after interruption. This saved copy is complete; an original may also remain."
                    else "Interrupted copy; it may be incomplete. The original changed or is unavailable. Inspect after restoring.")
                writeManifest(entries)
            }
            intent.delete()
        }
        val indexed = entries.map { File(it.trashedPath).canonicalPath }.toSet()
        root.listFiles()?.filter { it.name !in setOf("manifest.json", "manifest.pending", "operations") &&
            it.canonicalPath !in indexed && !Files.isSymbolicLink(it.toPath()) }?.forEach { orphan ->
            entries += Entry(File(recoveryDirectory, "recovered-${orphan.name}").absolutePath,
                orphan.absolutePath, Bytes(payloadSize(orphan)), System.currentTimeMillis(),
                "Recovery copy from an interrupted older version. Original location and completeness are unknown. Inspect after restoring.")
            writeManifest(entries)
        }
    }

    private fun payloadSize(file: File): Long = if (file.isFile) file.length() else
        file.walkTopDown().onEnter { !Files.isSymbolicLink(it.toPath()) }.filter { it.isFile }.sumOf { it.length() }

    private suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock { recover(); block() }
    }
    private fun digest(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }
    private companion object { val locks = ConcurrentHashMap<String, Mutex>() }
}
