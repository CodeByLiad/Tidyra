package digital.jinatra.tidyra.core.safety

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

/** Exclusive destinations: neither apply nor undo may replace someone else's file. */
object SafeFiles {
    fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                hash.update(buffer, 0, size)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    /** Organising stays on the same volume; a rename is atomic and needs no second copy. */
    fun rename(source: File, target: File) {
        check(source.isFile && !Files.isSymbolicLink(source.toPath())) { "File is missing or is a symbolic link" }
        check(!target.exists()) { "Destination already exists" }
        target.parentFile?.let { check(it.isDirectory || it.mkdirs()) { "Cannot create destination folder" } }
        Files.move(source.toPath(), target.toPath())
    }
    fun restoredTarget(original: File): File {
        if (!original.exists()) return original
        val extension = if (original.isDirectory || original.extension.isEmpty()) "" else ".${original.extension}"
        val stem = if (original.isDirectory) original.name else original.nameWithoutExtension
        var index = 1
        while (true) {
            val suffix = if (index == 1) "-restored" else "-restored-$index"
            val candidate = File(original.parentFile, "$stem$suffix$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    /** Copy durably before removing the source. A full disk leaves the source intact. */
    fun move(source: File, target: File, allowDirectoryContents: Boolean = false, beforeRemove: () -> Unit = {}) {
        require(source.canonicalFile != target.canonicalFile) { "Source and destination are the same" }
        check(!Files.isSymbolicLink(source.toPath())) { "Symbolic links are left alone" }
        target.parentFile?.let { check(it.isDirectory || it.mkdirs()) { "Cannot create destination folder" } }
        if (source.isDirectory) {
            check(allowDirectoryContents || source.listFiles()?.isEmpty() == true) { "Folder is no longer empty; scan again" }
            check(target.mkdir()) { "Destination already exists or cannot be created" }
            if (allowDirectoryContents) {
                source.walkTopDown().forEach { child ->
                    check(!Files.isSymbolicLink(child.toPath())) { "Recovery folder contains a symbolic link; left alone" }
                    if (child != source) {
                        val copy = File(target, child.relativeTo(source).path)
                        if (child.isDirectory) check(copy.mkdir()) { "Cannot create recovery subfolder" }
                        else {
                            check(copy.createNewFile()) { "Recovery destination exists" }
                            child.inputStream().use { input -> FileOutputStream(copy).use { output -> input.copyTo(output); output.fd.sync() } }
                            check(digest(child) == digest(copy)) { "Recovery copy could not be verified" }
                            copy.setLastModified(child.lastModified())
                        }
                    }
                }
            }
        } else {
            check(source.isFile) { "Source is no longer available" }
            check(target.createNewFile()) { "Destination already exists" }
            try {
                val size = source.length()
                val modified = source.lastModified()
                source.inputStream().use { input ->
                    FileOutputStream(target).use { output ->
                        val copied = input.copyTo(output)
                        output.fd.sync()
                        check(copied == size && source.length() == size && source.lastModified() == modified) {
                            "File changed while copying; scan again"
                        }
                    }
                }
                target.setLastModified(modified)
            } catch (error: Exception) {
                target.delete()
                throw error
            }
        }
        try { beforeRemove() } catch (error: Exception) {
            target.delete()
            throw error
        }
        // Never recursively remove a directory that may have gained files meanwhile.
        val removed = if (source.isDirectory && allowDirectoryContents) source.deleteRecursively() else source.delete()
        if (!removed) throw IOException("Source could not be removed; the saved copy was kept")
    }
}
