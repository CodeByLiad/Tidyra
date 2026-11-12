package digital.jinatra.tidyra.core.shizuku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader

/**
 * Optional ADB-level access.
 *
 * Everything the app can do without Shizuku, it does without Shizuku. This bridge only
 * adds what Android genuinely forbids a normal app: clearing another app's cache,
 * reading Android/data, and uninstalling without a dialog per app. Every call here is
 * expected to fail on a phone that has never heard of Shizuku, and the UI is written
 * for that case first.
 */
class ShizukuBridge {

    enum class State {
        /** Shizuku is not installed, or its service is not running. */
        UNAVAILABLE,

        /** Running, but the user has not granted Tidyra permission yet. */
        NOT_PERMITTED,

        READY,
    }

    fun state(): State = when {
        !runCatching { Shizuku.pingBinder() }.getOrDefault(false) -> State.UNAVAILABLE
        runCatching { Shizuku.checkSelfPermission() }.getOrDefault(-1) != 0 -> State.NOT_PERMITTED
        else -> State.READY
    }

    fun requestPermission(requestCode: Int) {
        runCatching { Shizuku.requestPermission(requestCode) }
    }

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
    }

    data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
        val ok: Boolean get() = exitCode == 0
    }

    /**
     * Runs a command with shell-level rights.
     *
     * Shizuku.newProcess is hidden API, so it is reached reflectively — the signature has
     * been stable for years, but a failure here is reported as a failed command rather
     * than crashing the app.
     */
    suspend fun exec(vararg command: String): ShellResult = withContext(Dispatchers.IO) {
        if (state() != State.READY) {
            return@withContext ShellResult(-1, "", "Shizuku is not ready")
        }
        runCatching {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }

            @Suppress("UNCHECKED_CAST")
            val process = method.invoke(null, arrayOf(*command), null, null) as Process
            val out = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val err = process.errorStream.bufferedReader().use(BufferedReader::readText)
            val code = process.waitFor()
            ShellResult(code, out, err)
        }.getOrElse { error ->
            ShellResult(-1, "", error.message ?: error::class.java.simpleName)
        }
    }

    /**
     * Trims every app's cache. This is the one job that makes Shizuku worth pairing:
     * a normal app cannot clear another app's cache at all.
     */
    suspend fun trimAllCaches(): ShellResult = exec("pm", "trim-caches", "${Long.MAX_VALUE}")

    suspend fun clearCache(packageName: String): ShellResult =
        exec("pm", "clear", "--cache-only", packageName)

    suspend fun uninstall(packageName: String): ShellResult =
        exec("pm", "uninstall", "--user", "0", packageName)

    /** Sizes inside another app's Android/data, which is unreadable to us on Android 11+. */
    suspend fun duOfRestrictedDir(path: String): ShellResult = exec("du", "-sk", path)
}
