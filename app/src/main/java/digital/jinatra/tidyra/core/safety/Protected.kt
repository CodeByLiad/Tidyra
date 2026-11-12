package digital.jinatra.tidyra.core.safety

import java.io.File

/**
 * The things Tidyra will not offer to delete, whatever the scan says.
 *
 * A storage cleaner that is 99% right is not good enough: the 1% is somebody's chat
 * history or the only copy of a wallet key. These rules are deliberately blunt and
 * deliberately conservative — a file wrongly protected costs the user some space, while a
 * file wrongly deleted can cost them something they cannot get back.
 *
 * This is a floor, not the whole safety story. Nothing is deleted without per-item
 * approval, and approved deletions go to the trash first.
 */
object Protected {

    /** Path fragments that mark something as nobody's junk. */
    private val SEGMENTS = listOf(
        "/databases/",          // WhatsApp, Telegram and friends keep chat history here
        "/backups/",
        "/backup/",
        "/.thumbnails/",
        "/android/obb/",        // paid game assets, often many GB and not re-downloadable
        "/keystore",
        "/wallet",
    )

    /** Extensions that are almost always the only copy of something irreplaceable. */
    private val EXTENSIONS = setOf(
        "crypt12", "crypt13", "crypt14", "crypt15", // WhatsApp message backups
        "kdbx", "key", "pem", "p12", "keystore", "jks", "seed", "wallet",
        "bin", "db", "sqlite", "realm",
    )

    /** Names that identify a backup regardless of where they sit. */
    private val NAME_HINTS = listOf("backup", "msgstore", "wa.db", "seed", "recovery", "mnemonic")

    fun isProtected(path: String): Boolean = reason(path) != null

    /**
     * Why this path is off limits, or null when it is ordinary. The reason is shown to the
     * user: a refusal without an explanation reads as a bug.
     */
    fun reason(path: String): String? {
        val lower = path.lowercase()

        SEGMENTS.firstOrNull { lower.contains(it) }?.let {
            return when {
                it.contains("database") -> "app databases hold chat history"
                it.contains("backup") -> "this is a backup"
                it.contains("obb") -> "game data that may not be re-downloadable"
                it.contains("thumbnail") -> "rebuilt automatically, not worth the risk"
                else -> "key material"
            }
        }

        val name = lower.substringAfterLast('/')
        val extension = name.substringAfterLast('.', "")
        if (extension in EXTENSIONS) return "a .$extension file is usually the only copy of something"
        if (NAME_HINTS.any { name.contains(it) }) return "the name says backup"

        return null
    }

    /**
     * Which copy of a set of identical files to keep.
     *
     * Lower rank wins. The point is that a photo in DCIM/Camera is the original and the
     * copy in an app's media folder is the derivative — keeping the older file by
     * timestamp would sometimes delete the camera roll and leave the chat attachment,
     * which is exactly backwards.
     */
    fun keepRank(file: File): Int {
        val lower = file.absolutePath.lowercase()
        return when {
            lower.contains("/dcim/camera/") -> 0
            lower.contains("/dcim/") -> 1
            lower.contains("/pictures/") || lower.contains("/documents/") -> 2
            lower.contains("/download/") -> 3
            lower.contains("/movies/") || lower.contains("/music/") -> 4
            lower.contains("/android/media/") -> 6
            lower.contains("/cache/") || lower.contains("/.cache/") -> 8
            lower.contains("/android/data/") -> 7
            else -> 5
        }
    }

    /** Packages Tidyra must never offer to uninstall. */
    fun isProtectedPackage(packageName: String, isSystem: Boolean): Boolean =
        isSystem ||
            packageName.startsWith("digital.jinatra.tidyra") ||
            packageName.startsWith("com.android.") ||
            packageName.startsWith("com.google.android.gms") ||
            packageName == "moe.shizuku.privileged.api"
}
