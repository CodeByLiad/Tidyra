package digital.jinatra.tidyra.core.safety

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class CopyProgressTest {
    @Test fun reportsRealBytesAndPreservesContentIfObserverFails() {
        val directory = Files.createTempDirectory("tidyra-progress").toFile()
        try {
            val source = java.io.File(directory, "source").apply { writeBytes(ByteArray(170_001) { (it % 251).toByte() }) }
            val digest = SafeFiles.digest(source)
            val target = java.io.File(directory, "target")
            val seen = mutableListOf<Long>()
            SafeFiles.move(source, target, onCopy = { bytes, total ->
                assertEquals(170_001L, total)
                seen += bytes
                if (bytes == 65_536L) error("Observer stopped")
            })
            assertEquals(digest, SafeFiles.digest(target))
            assertFalse(source.exists())
            assertEquals(0L, seen.first())
            assertEquals(170_001L, seen.last())
            assertEquals(seen.sorted(), seen)
        } finally { directory.deleteRecursively() }
    }
}
