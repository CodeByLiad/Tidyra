package digital.jinatra.tidyra.core.scan

import android.os.Environment
import android.os.StatFs
import digital.jinatra.tidyra.core.model.Bytes
import digital.jinatra.tidyra.core.model.Finding
import digital.jinatra.tidyra.core.model.FindingKind
import digital.jinatra.tidyra.core.model.Risk
import digital.jinatra.tidyra.core.model.StorageCategory
import digital.jinatra.tidyra.core.model.StorageSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Walks shared storage once and reports what it finds as it goes.
 *
 * One walk produces everything: the category totals, the large files, the empty folders,
 * the stale installers and the file index the duplicate finder works from. Walking
 * 20,000 files repeatedly is the difference between a scan that takes seconds and one
 * that takes a minute, so nothing here walks twice.
 */
class SharedStorageScanner {

    sealed interface Progress {
        data class Walking(val filesSeen: Int, val currentDir: String) : Progress
        data class Done(val result: ScanResult) : Progress
    }

    data class ScanResult(
        val snapshot: StorageSnapshot,
        val files: List<FileEntry>,
        val emptyDirectories: List<File>,
        val findings: List<Finding>,
    )

    data class FileEntry(
        val file: File,
        val size: Long,
        val lastModified: Long,
        val category: StorageCategory,
    )

    fun scan(root: File = Environment.getExternalStorageDirectory()): Flow<Progress> = flow {
        val files = ArrayList<FileEntry>(24_000)
        val emptyDirs = ArrayList<File>()
        var seen = 0

        // Explicit stack: /sdcard nests deep enough that recursion is a real risk,
        // and a stack lets the walk stay cancellable between directories.
        val stack = ArrayDeque<File>()
        stack.addLast(root)

        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val dir = stack.removeLast()
            val children = dir.listFiles()

            if (children == null) {
                // Android/data and Android/obb are unreadable without Shizuku on 11+.
                // That is a platform fact, not an error: skip and let the UI say so.
                continue
            }
            if (children.isEmpty() && dir != root && !dir.isHidden) {
                emptyDirs += dir
                continue
            }

            for (child in children) {
                if (child.isDirectory) {
                    stack.addLast(child)
                } else {
                    files += FileEntry(
                        file = child,
                        size = child.length(),
                        lastModified = child.lastModified(),
                        category = categorise(child),
                    )
                    seen++
                    if (seen % 500 == 0) emit(Progress.Walking(seen, dir.absolutePath))
                }
            }
        }

        val snapshot = snapshot(files)
        val findings = buildList {
            staleInstallers(files)?.let(::add)
            emptyFolders(emptyDirs)?.let(::add)
            largeFiles(files)?.let(::add)
            looseFiles(files, root)?.let(::add)
        }

        emit(Progress.Done(ScanResult(snapshot, files, emptyDirs, findings)))
    }.flowOn(Dispatchers.IO)

    private fun snapshot(files: List<FileEntry>): StorageSnapshot {
        val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong

        val byCategory = files.groupBy { it.category }
            .mapValues { (_, entries) -> Bytes(entries.sumOf { it.size }) }
            .toMutableMap()
        byCategory[StorageCategory.FREE] = Bytes(free)

        return StorageSnapshot(
            totalBytes = Bytes(total),
            freeBytes = Bytes(free),
            byCategory = byCategory,
            takenAtMillis = System.currentTimeMillis(),
        )
    }

    private fun categorise(file: File): StorageCategory = when (file.extension.lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng", "raw" -> StorageCategory.PHOTOS
        "mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v", "ts" -> StorageCategory.VIDEO
        "mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "amr" -> StorageCategory.AUDIO
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "epub" -> StorageCategory.DOCUMENTS
        "apk", "apks", "xapk", "apkm", "obb" -> StorageCategory.APPS
        else -> StorageCategory.OTHER
    }

    private fun staleInstallers(files: List<FileEntry>): Finding? {
        val cutoff = System.currentTimeMillis() - THIRTY_DAYS
        val installers = files.filter {
            it.file.extension.lowercase() in INSTALLER_EXTENSIONS &&
                it.lastModified < cutoff &&
                !it.file.absolutePath.contains("/Android/obb/")
        }
        if (installers.isEmpty()) return null

        return Finding(
            id = "stale-installers",
            kind = FindingKind.STALE_INSTALLER,
            title = if (installers.size == 1) "1 stale installer" else "${installers.size} stale installers",
            detail = "Package files older than 30 days. The app they install is either already on the phone or no longer wanted.",
            frees = Bytes(installers.sumOf { it.size }),
            risk = Risk.REVIEW,
            items = installers.map {
                Finding.Item(it.file.absolutePath, Bytes(it.size), it.lastModified)
            },
        )
    }

    private fun emptyFolders(dirs: List<File>): Finding? {
        if (dirs.isEmpty()) return null
        return Finding(
            id = "empty-folders",
            kind = FindingKind.EMPTY_FOLDER,
            title = "${dirs.size} empty folders",
            detail = "Left behind by apps that have moved on. Removing them frees no space; it only tidies.",
            frees = Bytes.ZERO,
            risk = Risk.SAFE,
            items = dirs.map { Finding.Item(it.absolutePath, Bytes.ZERO, it.lastModified()) },
        )
    }

    private fun largeFiles(files: List<FileEntry>): Finding? {
        val large = files.filter { it.size >= LARGE_FILE_THRESHOLD }
            .sortedByDescending { it.size }
            .take(50)
        if (large.isEmpty()) return null

        return Finding(
            id = "large-files",
            kind = FindingKind.LARGE_FILE,
            title = "${large.size} files over 100 MB",
            detail = "Not junk by definition — shown so you can decide. Nothing here is pre-selected.",
            frees = Bytes(large.sumOf { it.size }),
            risk = Risk.PERSONAL,
            items = large.map { Finding.Item(it.file.absolutePath, Bytes(it.size), it.lastModified) },
        )
    }

    /** Files sitting directly in Download, Documents and similar, with no folder of their own. */
    private fun looseFiles(files: List<FileEntry>, root: File): Finding? {
        val targets = LOOSE_FILE_DIRS.map { File(root, it).absolutePath }.toSet()
        val loose = files.filter { it.file.parent in targets && !it.file.name.startsWith(".") }
        if (loose.size < 3) return null

        return Finding(
            id = "loose-files",
            kind = FindingKind.LOOSE_FILES,
            title = "${loose.size} loose files",
            detail = "Sitting directly in ${LOOSE_FILE_DIRS.joinToString(", ")}. These get filed into folders, not deleted.",
            frees = Bytes.ZERO,
            risk = Risk.SAFE,
            items = loose.map { Finding.Item(it.file.absolutePath, Bytes(it.size), it.lastModified) },
        )
    }

    private companion object {
        const val THIRTY_DAYS = 30L * 24 * 60 * 60 * 1000
        const val LARGE_FILE_THRESHOLD = 100L * 1024 * 1024
        val INSTALLER_EXTENSIONS = setOf("apk", "apks", "xapk", "apkm")
        val LOOSE_FILE_DIRS = listOf("Download", "Documents", "Pictures")
    }
}
