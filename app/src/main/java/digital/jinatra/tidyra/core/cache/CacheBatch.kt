package digital.jinatra.tidyra.core.cache

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object CacheBatch {
    enum class Status { CONFIRMED, REQUESTED, FAILED, NOT_ATTEMPTED }
    data class Item(val packageName: String, val status: Status, val detail: String)
    data class Progress(val current: String?, val total: Int, val results: List<Item>)

    suspend fun run(packages: List<String>, available: () -> Boolean,
        clear: suspend (String) -> Item, progress: (Progress) -> Unit): List<Item> {
        val unique = packages.distinct()
        val results = mutableListOf<Item>()
        for ((index, pkg) in unique.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (!available()) {
                results += unique.drop(index).map { Item(it, Status.NOT_ATTEMPTED, "Automatic clearing unavailable; use Settings") }
                break
            }
            progress(Progress(pkg, unique.size, results.toList()))
            results += clear(pkg)
            progress(Progress(null, unique.size, results.toList()))
        }
        progress(Progress(null, unique.size, results.toList()))
        return results
    }
}
