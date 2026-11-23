package digital.jinatra.tidyra.core.cache

import digital.jinatra.tidyra.core.model.Bytes

/** Observed cache reduction, never the selected estimate or unrelated device-free changes. */
object CacheSavings {
    data class Measurement(val before: Bytes, val after: Bytes, val appCount: Int) {
        val saved: Bytes get() = Bytes((before.value - after.value).coerceAtLeast(0))
    }

    fun measure(before: Map<String, Long>, after: Map<String, Long>, results: List<CacheBatch.Item>): Measurement? {
        val attempted = results.filter { it.status == CacheBatch.Status.CONFIRMED || it.status == CacheBatch.Status.REQUESTED }
            .map { it.packageName }.distinct()
        if (attempted.isEmpty() || attempted.any { (before[it] ?: -1) < 0 || (after[it] ?: -1) < 0 }) return null
        return Measurement(Bytes(attempted.sumOf { before.getValue(it) }),
            Bytes(attempted.sumOf { after.getValue(it) }), attempted.size)
    }
}
