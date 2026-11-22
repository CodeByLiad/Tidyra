package digital.jinatra.tidyra.core.organize

import digital.jinatra.tidyra.core.model.Bytes
import digital.jinatra.tidyra.core.safety.Protected
import digital.jinatra.tidyra.core.safety.SafeFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Files loose files into folders by type.
 *
 * The plan is always shown in full before a single file moves, and moving is reversible
 * in one step because the plan itself is the undo record. Nothing is deleted here, ever:
 * organising and deleting are different operations and the app never mixes them.
 */
class Organizer(historyFile: File? = null) {
    private val history = OrganizeHistory(historyFile)
    private val mutex = if (historyFile == null) Mutex() else locks.getOrPut(historyFile.canonicalPath) { Mutex() }

    suspend fun pending(): Result? = withContext(Dispatchers.IO) {
        mutex.withLock { history.latest(history.recover()) }
    }

    enum class Bucket(val folderName: String, val extensions: Set<String>) {
        DOCUMENTS("Documents", setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "rtf", "odt", "epub")),
        IMAGES("Images", setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "svg", "dng")),
        VIDEOS("Videos", setOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v")),
        AUDIO("Audio", setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "amr")),
        INSTALLERS("Installers", setOf("apk", "apks", "xapk", "apkm")),
        ARCHIVES("Archives", setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")),
        BACKUPS("Backups", setOf("bin", "bak", "db", "sql", "key", "kdbx")),
        OTHER("Other", emptySet()),
    }

    data class Move(val from: File, val to: File,
        val expectedSize: Long = from.length(), val expectedModified: Long = from.lastModified(),
        val approvedRoot: File? = null) {
        val bucketName: String get() = to.parentFile?.name.orEmpty()
    }

    data class Plan(val moves: List<Move>, val skipped: List<Pair<File, String>>) {
        val byBucket: Map<String, List<Move>> get() = moves.groupBy { it.bucketName }
        val fileCount: Int get() = moves.size
        val bytesMoved: Bytes get() = Bytes(moves.sumOf { it.from.length() })
    }

    /**
     * Builds the plan. Nothing touches the filesystem until [apply].
     *
     * @param includeOther when false, files that match no bucket are left exactly where they are
     *   rather than being swept into an "Other" folder nobody asked for.
     */
    fun plan(directory: File, includeOther: Boolean = false, includeConflicts: Boolean = false): Plan {
        val children = directory.listFiles()?.filter { it.isFile } ?: return Plan(emptyList(), emptyList())
        val moves = ArrayList<Move>()
        val skipped = ArrayList<Pair<File, String>>()

        for (file in children) {
            if (java.nio.file.Files.isSymbolicLink(file.toPath())) {
                skipped += file to "Symbolic link, left alone"
                continue
            }
            if (file.name.startsWith(".")) {
                skipped += file to "Hidden file, left alone"
                continue
            }
            // The same rule that stops Tidyra deleting these stops it relocating them. A
            // backup or a key file sitting in Download may be what some other app or some
            // restore step goes looking for by path; tidying is not worth breaking that.
            val protected = Protected.reason(file.path)
            if (protected != null) {
                skipped += file to "Left where it is: $protected"
                continue
            }
            val bucket = bucketFor(file)
            if (bucket == Bucket.OTHER && !includeOther) {
                skipped += file to "No obvious type"
                continue
            }

            val target = File(File(directory, bucket.folderName), file.name)
            if (target.exists() && !includeConflicts) {
                skipped += file to "A file of that name is already in ${bucket.folderName}"
                continue
            }
            moves += Move(file, target)
        }
        return Plan(moves, skipped)
    }

    suspend fun apply(plan: Plan, onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }): Result = withContext(Dispatchers.IO) {
      mutex.withLock {
        val records = history.recover()
        val batch = UUID.randomUUID().toString()
        val done = ArrayList<Move>()
        val failed = ArrayList<Pair<Move, String>>()

        for ((index, move) in plan.moves.withIndex()) {
            runCatching { onProgress(index, plan.moves.size, move.from.name) }
            var record: OrganizeHistory.Record? = null
            var renamed = false
            runCatching {
                check(validScope(move)) {
                    "Organising must stay inside the approved folder"
                }
                check(Protected.reason(move.from.canonicalPath) == null) { "Protected file; left alone" }
                check(Protected.reason(move.to.canonicalPath) == null) { "Protected destination; choose another folder" }
                check(move.from.length() == move.expectedSize && move.from.lastModified() == move.expectedModified) {
                    "File changed since preview; preview again"
                }
                check(!move.to.exists()) { "Destination already exists" }
                val prepared = OrganizeHistory.Record(batch, move, SafeFiles.digest(move.from))
                record = prepared
                records += prepared
                history.save(records)
                SafeFiles.rename(move.from, move.to)
                renamed = true
                done += move
                prepared.phase = "moved"
                history.save(records)
            }.onFailure {
                if (!renamed && record != null) {
                    records.remove(record)
                    history.save(records)
                }
                failed += move to (it.message ?: "Could not be moved")
            }
            runCatching { onProgress(index + 1, plan.moves.size, move.from.name) }
        }
        Result(done, failed)
      }
    }

    /** Puts every moved file back where it was. The plan is its own undo record. */
    suspend fun undo(result: Result, onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }): Int = withContext(Dispatchers.IO) {
      mutex.withLock {
        val records = history.recover()
        var restored = 0
        result.moved.forEachIndexed { index, move ->
            runCatching { onProgress(index, result.moved.size, move.from.name) }
            val record = records.firstOrNull { it.move == move } ?: return@forEachIndexed
            if (runCatching {
                check(history.matches(move.to, record) && move.to.lastModified() == move.expectedModified) {
                    "Filed copy has changed; left alone"
                }
                val destination = SafeFiles.restoredTarget(move.from)
                record.phase = "restoring"
                record.restorePath = destination.absolutePath
                history.save(records)
                SafeFiles.rename(move.to, destination)
                records.remove(record)
                history.save(records)
            }.isSuccess) {
                restored++
            }
            runCatching { onProgress(index + 1, result.moved.size, move.from.name) }
        }
        restored
      }
    }

    data class Result(val moved: List<Move>, val failed: List<Pair<Move, String>>)

    private fun bucketFor(file: File): Bucket {
        val extension = file.extension.lowercase()
        return Bucket.entries.firstOrNull { extension in it.extensions } ?: Bucket.OTHER
    }
    companion object {
        private val locks = ConcurrentHashMap<String, Mutex>()

        fun destinationAllowed(root: File, directory: File): Boolean = runCatching {
            val base = root.canonicalFile
            val target = directory.canonicalFile
            if (!target.toPath().startsWith(base.toPath())) return@runCatching false
            val relative = base.toPath().relativize(target.toPath()).map { it.toString() }
            if (relative.firstOrNull()?.equals("Android", true) == true || relative.any { it.startsWith(".") && it.isNotEmpty() }) return@runCatching false
            var ancestor: File? = directory.absoluteFile
            while (ancestor != null && ancestor.toPath().startsWith(root.absoluteFile.toPath())) {
                if (java.nio.file.Files.isSymbolicLink(ancestor.toPath())) return@runCatching false
                if (ancestor == root.absoluteFile) break
                ancestor = ancestor.parentFile
            }
            Protected.reason(target.path + File.separator) == null && (!target.exists() || target.isDirectory)
        }.getOrDefault(false)

        fun retarget(move: Move, directory: File, root: File): Move {
            require(destinationAllowed(root, directory)) { "Choose a folder in internal storage, outside protected and app-managed folders" }
            require(move.from.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) { "Source is outside the approved storage" }
            require(move.from.canonicalFile.parentFile != directory.canonicalFile) { "The file is already in this folder" }
            return move.copy(to = File(directory.canonicalFile, move.from.name), approvedRoot = root.canonicalFile)
        }

        internal fun validScope(move: Move): Boolean = runCatching {
            if (!move.from.isAbsolute || !move.to.isAbsolute || move.from.name != move.to.name || move.from.canonicalFile == move.to.canonicalFile) return@runCatching false
            val root = move.approvedRoot
            if (root == null) move.from.canonicalFile.parentFile == move.to.canonicalFile.parentFile?.parentFile
            else root.isAbsolute && move.from.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) &&
                destinationAllowed(root, move.to.parentFile!!)
        }.getOrDefault(false)
    }
}
