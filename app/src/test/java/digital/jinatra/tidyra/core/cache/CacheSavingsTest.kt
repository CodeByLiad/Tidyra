package digital.jinatra.tidyra.core.cache

import org.junit.Assert.*
import org.junit.Test

class CacheSavingsTest {
    private fun item(pkg: String, status: CacheBatch.Status = CacheBatch.Status.REQUESTED) = CacheBatch.Item(pkg, status, "")
    @Test fun `uses measured change rather than requested amount`() {
        val result = CacheSavings.measure(mapOf("a" to 100L), mapOf("a" to 35L), listOf(item("a")))!!
        assertEquals(65L, result.saved.value)
        assertEquals(1, result.appCount)
    }
    @Test fun `failed and unattempted packages never contribute`() {
        val result = CacheSavings.measure(mapOf("a" to 100L, "b" to 100L, "c" to 100L),
            mapOf("a" to 40L, "b" to 0L, "c" to 0L),
            listOf(item("a", CacheBatch.Status.CONFIRMED), item("b", CacheBatch.Status.FAILED), item("c", CacheBatch.Status.NOT_ATTEMPTED)))!!
        assertEquals(60L, result.saved.value)
    }
    @Test fun `a duplicate result is counted once`() {
        val result = CacheSavings.measure(mapOf("a" to 100L), mapOf("a" to 0L), listOf(item("a"), item("a")))!!
        assertEquals(100L, result.saved.value)
        assertEquals(1, result.appCount)
    }
    @Test fun `missing before or after measurement is unavailable not zero`() {
        assertNull(CacheSavings.measure(emptyMap(), mapOf("a" to 0L), listOf(item("a"))))
        assertNull(CacheSavings.measure(mapOf("a" to 100L), emptyMap(), listOf(item("a"))))
        assertNull(CacheSavings.measure(mapOf("a" to -1L), mapOf("a" to 0L), listOf(item("a"))))
    }
    @Test fun `net cache growth offsets reductions and never reports negative savings`() {
        assertEquals(0L, CacheSavings.measure(mapOf("a" to 100L, "b" to 0L), mapOf("a" to 0L, "b" to 130L),
            listOf(item("a"), item("b")))!!.saved.value)
    }
    @Test fun `an unchanged measurement is genuine zero and a cancelled batch is unavailable`() {
        assertEquals(0L, CacheSavings.measure(mapOf("a" to 100L), mapOf("a" to 100L), listOf(item("a")))!!.saved.value)
        assertNull(CacheSavings.measure(mapOf("a" to 100L), mapOf("a" to 0L), listOf(item("a", CacheBatch.Status.NOT_ATTEMPTED))))
    }
}
