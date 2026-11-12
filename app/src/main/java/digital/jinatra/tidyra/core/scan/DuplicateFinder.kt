package digital.jinatra.tidyra.core.scan

import digital.jinatra.tidyra.core.model.Bytes
import digital.jinatra.tidyra.core.model.Finding
import digital.jinatra.tidyra.core.model.FindingKind
import digital.jinatra.tidyra.core.model.Risk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Finds files that are byte-for-byte identical, and will not call anything a duplicate
 * until it has proved it.
 *
 * Three passes, each cheaper than the one after it, so the expensive one runs on almost nothing:
 *  1. group by exact size — files of different sizes cannot be identical;
 *  2. within a size group, hash the head and tail (8 KB each) — settles nearly every false match;
 *  3. only for files that still agree, hash the whole file.
 *
 * Same name and same size is not proof, and a cleaner that deletes on that basis will
 * eventually delete something it should not have.
 */
class DuplicateFinder {

    data class DuplicateSet(val keep: File, val copies: List<File>, val eachSize: Long) {
        val reclaimable: Long get() = eachSize * copies.size
    }

    suspend fun find(
        files: List<SharedStorageScanner.FileEntry>,
        minSize: Long = MIN_SIZE,
        onProgress: (hashed: Int, total: Int) -> Unit = { _, _ -> },
    ): List<DuplicateSet> = withContext(Dispatchers.IO) {
        val candidates = files.filter { it.size >= minSize }
            .groupBy { it.size }
            .filterValues { it.size > 1 }

        val toHash = candidates.values.sumOf { it.size }
        var hashed = 0
        val result = ArrayList<DuplicateSet>()

        for ((size, group) in candidates) {
            coroutineContext.ensureActive()

            val byEdges = group.groupBy { edgeHash(it.file) }
            for ((_, edgeGroup) in byEdges) {
                if (edgeGroup.size < 2) {
                    hashed += edgeGroup.size
                    continue
                }
                val byFull = edgeGroup.groupBy { fullHash(it.file) }
                for ((_, identical) in byFull) {
                    hashed += identical.size
                    onProgress(hashed, toHash)
                    if (identical.size < 2) continue

                    // Keep the oldest copy: it is the one the other was copied from,
                    // and it is the one the user's apps are most likely to still reference.
                    val sorted = identical.sortedBy { it.lastModified }
                    result += DuplicateSet(
                        keep = sorted.first().file,
                        copies = sorted.drop(1).map { it.file },
                        eachSize = size,
                    )
                }
            }
        }
        result.sortedByDescending { it.reclaimable }
    }

    fun toFinding(sets: List<DuplicateSet>): Finding? {
        if (sets.isEmpty()) return null
        val copies = sets.sumOf { it.copies.size }
        val frees = sets.sumOf { it.reclaimable }

        return Finding(
            id = "duplicates",
            kind = FindingKind.DUPLICATE_FILES,
            title = "$copies duplicate files",
            detail = "Every one checked byte for byte against the copy being kept. The kept copy is never touched.",
            frees = Bytes(frees),
            risk = Risk.SAFE,
            items = sets.flatMap { set ->
                set.copies.map { copy ->
                    Finding.Item(
                        path = copy.absolutePath,
                        size = Bytes(set.eachSize),
                        lastModifiedMillis = copy.lastModified(),
                        keptCounterpart = set.keep.absolutePath,
                    )
                }
            },
        )
    }

    /** First and last 8 KB. Cheap, and enough to separate files that merely share a size. */
    private fun edgeHash(file: File): String = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val digest = MessageDigest.getInstance("SHA-256")
            val length = raf.length()
            val buffer = ByteArray(EDGE_BYTES)

            val head = raf.read(buffer)
            if (head > 0) digest.update(buffer, 0, head)

            if (length > EDGE_BYTES * 2) {
                raf.seek(length - EDGE_BYTES)
                val tail = raf.read(buffer)
                if (tail > 0) digest.update(buffer, 0, tail)
            }
            digest.digest().toHex()
        }
    }.getOrElse { UNREADABLE + file.absolutePath }

    private fun fullHash(file: File): String = runCatching {
        file.inputStream().buffered(BUFFER).use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().toHex()
        }
    }.getOrElse { UNREADABLE + file.absolutePath }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val MIN_SIZE = 64L * 1024
        const val EDGE_BYTES = 8 * 1024
        const val BUFFER = 64 * 1024

        /**
         * An unreadable file gets a hash unique to its own path, so it can never match
         * another file and can never be proposed for deletion.
         */
        const val UNREADABLE = "unreadable:"
    }
}
