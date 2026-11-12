package digital.jinatra.tidyra.core.organize

import digital.jinatra.tidyra.core.model.Bytes
import digital.jinatra.tidyra.core.safety.Protected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Files loose files into folders by type.
 *
 * The plan is always shown in full before a single file moves, and moving is reversible
 * in one step because the plan itself is the undo record. Nothing is deleted here, ever:
 * organising and deleting are different operations and the app never mixes them.
 */
class Organizer {

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

    data class Move(val from: File, val to: File) {
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
    fun plan(directory: File, includeOther: Boolean = false): Plan {
        val children = directory.listFiles()?.filter { it.isFile } ?: return Plan(emptyList(), emptyList())
        val moves = ArrayList<Move>()
        val skipped = ArrayList<Pair<File, String>>()

        for (file in children) {
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
            if (target.exists()) {
                skipped += file to "A file of that name is already in ${bucket.folderName}"
                continue
            }
            moves += Move(file, target)
        }
        return Plan(moves, skipped)
    }

    suspend fun apply(plan: Plan): Result = withContext(Dispatchers.IO) {
        val done = ArrayList<Move>()
        val failed = ArrayList<Pair<Move, String>>()

        for (move in plan.moves) {
            move.to.parentFile?.mkdirs()
            val ok = runCatching { move.from.renameTo(move.to) }.getOrDefault(false)
            if (ok) done += move else failed += move to "Could not be moved"
        }
        Result(done, failed)
    }

    /** Puts every moved file back where it was. The plan is its own undo record. */
    suspend fun undo(result: Result): Int = withContext(Dispatchers.IO) {
        var restored = 0
        result.moved.forEach { move ->
            move.from.parentFile?.mkdirs()
            if (runCatching { move.to.renameTo(move.from) }.getOrDefault(false)) {
                restored++
                move.to.parentFile?.takeIf { it.listFiles()?.isEmpty() == true }?.delete()
            }
        }
        restored
    }

    data class Result(val moved: List<Move>, val failed: List<Pair<Move, String>>)

    private fun bucketFor(file: File): Bucket {
        val extension = file.extension.lowercase()
        return Bucket.entries.firstOrNull { extension in it.extensions } ?: Bucket.OTHER
    }
}
