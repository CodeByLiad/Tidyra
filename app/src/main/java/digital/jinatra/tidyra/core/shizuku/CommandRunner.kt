package digital.jinatra.tidyra.core.shizuku

import java.io.InputStream
import java.util.concurrent.TimeUnit

/** Never let a descendant holding a pipe open extend the process deadline. */
object CommandRunner {
    interface Process {
        val stdout: InputStream
        val stderr: InputStream
        fun waitFor(millis: Long): Boolean
        fun exitCode(): Int
        fun destroy()
        fun cancel() {}
    }
    data class Result(val code: Int, val out: String, val error: String)
    private class Capture {
        private val text = StringBuilder()
        @Volatile var truncated = false
        @Volatile var failed = false
        @Synchronized fun append(chars: CharArray, count: Int) {
            val room = (4 * 1024 * 1024 - text.length).coerceAtLeast(0)
            text.append(chars, 0, minOf(room, count))
            if (count > room) truncated = true
        }
        @Synchronized fun value() = text.toString()
    }
    fun run(process: Process, timeoutMillis: Long, onStdoutLine: ((String) -> Unit)? = null, cancelled: () -> Boolean = { false }): Result {
        require(timeoutMillis > 0)
        val out = Capture()
        val err = Capture()
        fun drain(stream: InputStream, capture: Capture, listener: ((String) -> Unit)? = null) = Thread({
            runCatching { stream.bufferedReader().use { reader ->
                val buffer = CharArray(4096)
                val line = StringBuilder()
                while (true) {
                    val n = reader.read(buffer)
                    if (n < 0) break
                    capture.append(buffer, n)
                    if (listener != null) for (index in 0 until n) {
                        val char = buffer[index]
                        if (char == '\n') { listener(line.toString().trimEnd('\r')); line.setLength(0) }
                        else { check(line.length < 16_384) { "Progress line is too large" }; line.append(char) }
                    }
                }
                if (listener != null && line.isNotEmpty()) listener(line.toString())
            } }.onFailure { capture.failed = true }
        }, "tidyra-command-output").apply { isDaemon = true; start() }
        val output = drain(process.stdout, out, onStdoutLine)
        val errors = drain(process.stderr, err)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        try {
            while (true) {
                if (cancelled()) return Result(-1, out.value(), "Command cancelled")
                val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                if (remaining <= 0) return Result(-1, out.value(), "Shizuku command timed out")
                if (process.waitFor(minOf(200, remaining.coerceAtLeast(1)))) break
            }
            output.join(200)
            errors.join(200)
            if (output.isAlive || errors.isAlive) return Result(-1, out.value(), "Command output did not close")
            if (out.failed || err.failed) return Result(-1, out.value(), "Command output could not be read completely")
            if (out.truncated || err.truncated) return Result(-1, out.value(), "Command output was too large; result is incomplete")
            return Result(process.exitCode(), out.value(), err.value())
        } finally {
            // Closing the cache helper's control pipe stops new actions before remote cleanup.
            runCatching { process.cancel() }
            // A stuck Binder destroy or broken pipe must not extend the visible deadline.
            Thread({ runCatching { process.destroy() }; runCatching { process.stdout.close() }; runCatching { process.stderr.close() } },
                "tidyra-command-close").apply { isDaemon = true; start() }
        }
    }
}
