package digital.jinatra.tidyra.core.model

import java.io.File

/**
 * Every size in Tidyra is exact bytes. Nothing is rounded before it reaches the UI,
 * and the UI formats it one way everywhere so a megabyte reads the same on every screen.
 */
@JvmInline
value class Bytes(val value: Long) : Comparable<Bytes> {
    operator fun plus(other: Bytes) = Bytes(value + other.value)
    operator fun minus(other: Bytes) = Bytes(value - other.value)
    override fun compareTo(other: Bytes) = value.compareTo(other.value)

    companion object {
        val ZERO = Bytes(0)
        fun of(file: File) = Bytes(file.length())
    }
}

fun Iterable<Bytes>.sum(): Bytes = Bytes(sumOf { it.value })

/** What a byte belongs to on the device. The container row is drawn from these, in this order. */
enum class StorageCategory(val label: String) {
    APPS("Apps"),
    APP_DATA("App data"),
    PHOTOS("Photos"),
    VIDEO("Video"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    OTHER("Everything else"),
    FREE("Free"),
}

data class StorageSnapshot(
    val totalBytes: Bytes,
    val freeBytes: Bytes,
    val byCategory: Map<StorageCategory, Bytes>,
    val takenAtMillis: Long,
) {
    val usedBytes: Bytes get() = totalBytes - freeBytes
}

/** How much trust an action needs before Tidyra will even propose it. */
enum class Risk {
    /** Regenerates itself or is provably redundant: caches, verified duplicates, empty folders. */
    SAFE,

    /** Gone for good but clearly disposable: stale installers, leftover downloads. */
    REVIEW,

    /** Personal or business material. Never proposed without an offer to back it up first. */
    PERSONAL,
}

enum class FindingKind {
    DUPLICATE_FILES,
    STALE_INSTALLER,
    EMPTY_FOLDER,
    APP_CACHE,
    UNUSED_APP,
    LARGE_FILE,
    CHAT_MEDIA,
    LOOSE_FILES,
}

/**
 * One proposal. A finding never acts on anything by itself: it carries what it would free,
 * what would be lost, and the items, and waits to be approved item by item.
 */
data class Finding(
    val id: String,
    val kind: FindingKind,
    val title: String,
    val detail: String,
    val frees: Bytes,
    val risk: Risk,
    val items: List<Item>,
    /**
     * Ids of other findings that claim some of the same items. The UI draws a junction mark
     * and the plan counts the overlap once, so two findings can never promise the same bytes twice.
     */
    val overlaps: Set<String> = emptySet(),
    /** Set once a model has looked at this finding. Advisory only; it never changes what is proposed. */
    val aiNote: String? = null,
) {
    data class Item(
        val path: String,
        val size: Bytes,
        val lastModifiedMillis: Long,
        /** For duplicates: the copy being kept, which is why this one is safe to remove. */
        val keptCounterpart: String? = null,
        val packageName: String? = null,
        /**
         * What to call this in the UI. An app's own name, where a package name would be
         * unreadable — nobody recognises com.playdigious.pol.
         */
        val label: String? = null,
    )
}

/** What the user actually approved, resolved so overlapping findings cannot double-count. */
data class ActionPlan(
    val approved: List<Finding>,
    val itemPaths: Set<String>,
) {
    val frees: Bytes = run {
        val seen = HashSet<String>()
        var total = 0L
        approved.forEach { finding ->
            finding.items.forEach { item ->
                if (item.path in itemPaths && seen.add(item.path)) total += item.size.value
            }
        }
        Bytes(total)
    }

    val itemCount: Int get() = itemPaths.size
}

/** The result of carrying a plan out. Reported exactly, including what did not work. */
data class CleanupResult(
    val freed: Bytes,
    val itemsRemoved: Int,
    val itemsFailed: List<Failure>,
    val trashedTo: String?,
) {
    data class Failure(val path: String, val reason: String)
}
