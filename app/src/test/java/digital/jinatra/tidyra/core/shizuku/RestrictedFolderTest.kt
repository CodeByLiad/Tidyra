package digital.jinatra.tidyra.core.shizuku

import org.junit.Assert.*
import org.junit.Test

class RestrictedFolderTest {
    @Test fun `reads direct children without counting root twice or trusting malformed paths`() {
        val root = "/storage/emulated/0/Android/data"
        val result = RestrictedFolder.parse("""
            4 $root/com.one
            8 $root/com.two
            12 $root
            -1 $root/com.negative
            9223372036854775807 $root/com.overflow
            5 /unrelated/path
            3 $root/com.one/nested
            2 $root/../escape
            4 $root/com.one
            nonsense
        """.trimIndent(), root)
        assertEquals(listOf("$root/com.two", "$root/com.one"), result.map { it.path })
        assertEquals(listOf(8192L, 4096L), result.map { it.size.value })
    }
}
