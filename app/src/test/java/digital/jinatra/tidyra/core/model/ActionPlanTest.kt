package digital.jinatra.tidyra.core.model

import org.junit.Assert.*
import org.junit.Test

class ActionPlanTest {
    private fun finding(id: String, kind: FindingKind, vararg paths: String) = Finding(
        id, kind, id, "", Bytes(10L * paths.size), Risk.REVIEW,
        paths.map { Finding.Item(it, Bytes(10), 0) })

    @Test fun `organising and app actions never become file deletions`() {
        val plan = ActionPlan(listOf(
            finding("loose", FindingKind.LOOSE_FILES, "/Download/invoice.pdf"),
            finding("cache", FindingKind.APP_CACHE, "com.example.app"),
            finding("unused", FindingKind.UNUSED_APP, "com.example.unused"),
        ), setOf("/Download/invoice.pdf", "com.example.app", "com.example.unused"))
        assertEquals(0, plan.itemCount)
        assertEquals(0L, plan.frees.value)
        assertTrue(plan.fileItems.isEmpty())
    }

    @Test fun `overlapping findings only act on selected files once`() {
        val plan = ActionPlan(listOf(
            finding("duplicates", FindingKind.DUPLICATE_FILES, "a", "b"),
            finding("large", FindingKind.LARGE_FILE, "a", "c"),
        ), setOf("a", "c", "no-longer-a-finding"))
        assertEquals(2, plan.itemCount)
        assertEquals(20L, plan.frees.value)
        assertEquals(listOf("a", "c"), plan.fileItems.map { it.path })
    }
}
