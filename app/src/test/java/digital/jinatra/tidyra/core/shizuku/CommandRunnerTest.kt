package digital.jinatra.tidyra.core.shizuku

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import org.junit.Assert.*
import org.junit.Test

class CommandRunnerTest {
    private class Fake(override val stdout: InputStream = ByteArrayInputStream("done".toByteArray()),
        override val stderr: InputStream = ByteArrayInputStream(byteArrayOf()), val completes: Boolean = true) : CommandRunner.Process {
        var destroyed = false
        override fun waitFor(millis: Long): Boolean { if (!completes) Thread.sleep(millis); return completes }
        override fun exitCode() = 7
        override fun destroy() { destroyed = true }
    }
    @Test fun preservesOutputAndActualExitStatus() {
        val process = Fake()
        val result = CommandRunner.run(process, 2000)
        assertEquals(7, result.code)
        assertEquals("done", result.out)
        assertTrue(process.destroyed)
    }
    @Test fun timeoutDestroysProcessWithoutWaitingForInheritedPipe() {
        val closed = CountDownLatch(1)
        val stuck = object : InputStream() {
            override fun read(): Int { closed.await(); return -1 }
            override fun close() { closed.countDown() }
        }
        val process = Fake(stdout = stuck, completes = false)
        val started = System.nanoTime()
        assertTrue(CommandRunner.run(process, 100).error.contains("timed out"))
        assertTrue((System.nanoTime() - started) / 1_000_000 < 1500)
        assertTrue(process.destroyed)
        closed.countDown()
    }
    @Test fun exitedParentWithOpenPipeIsNotSuccessful() {
        val closed = CountDownLatch(1)
        val stuck = object : InputStream() {
            override fun read(): Int { closed.await(); return -1 }
            override fun close() { closed.countDown() }
        }
        try {
            val started = System.nanoTime()
            assertEquals("Command output did not close", CommandRunner.run(Fake(stdout = stuck), 1000).error)
            assertTrue((System.nanoTime() - started) / 1_000_000 < 1500)
        } finally { closed.countDown() }
    }
    @Test fun cancellationStopsWaiting() {
        val process = Fake(completes = false)
        assertEquals("Command cancelled", CommandRunner.run(process, 30_000) { true }.error)
        assertTrue(process.destroyed)
    }
    @Test fun brokenOutputCannotMasqueradeAsCompleteResult() {
        val broken = object : InputStream() { override fun read(): Int = throw IOException("broken") }
        assertEquals(-1, CommandRunner.run(Fake(stdout = broken), 1000).code)
    }
    @Test fun oversizedOutputIsRejectedRatherThanSilentlyTruncated() {
        val bytes = ByteArray(4 * 1024 * 1024 + 1) { 65 }
        val result = CommandRunner.run(Fake(stdout = ByteArrayInputStream(bytes)), 5000)
        assertEquals(-1, result.code)
        assertTrue(result.error.contains("incomplete"))
    }
}
