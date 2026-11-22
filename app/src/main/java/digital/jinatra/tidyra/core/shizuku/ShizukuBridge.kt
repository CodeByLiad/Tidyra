package digital.jinatra.tidyra.core.shizuku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process as AndroidProcess
import android.os.Build
import digital.jinatra.tidyra.core.safety.Protected
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import digital.jinatra.tidyra.core.cache.CachePolicy

/**
 * Optional ADB-level access.
 *
 * Everything the app can do without Shizuku, it does without Shizuku. This bridge only
 * adds what Android genuinely forbids a normal app: clearing another app's cache,
 * reading Android/data, and uninstalling without a dialog per app. Every call here is
 * expected to fail on a phone that has never heard of Shizuku, and the UI is written
 * for that case first.
 */
class ShizukuBridge(private val context: Context) {
    private val capabilities = context.getSharedPreferences("shizuku_capabilities", Context.MODE_PRIVATE)
    private fun backendKey() = "${Build.FINGERPRINT}:${runCatching { Shizuku.getUid() }.getOrDefault(-1)}"
    fun canClearCache(): Boolean = state() == State.READY && runCatching {
        Shizuku.checkRemotePermission("android.permission.INTERNAL_DELETE_CACHE_FILES") == 0 &&
            capabilities.getString("cache_unavailable_backend", null) != backendKey()
    }.getOrDefault(false)
    private fun markCacheUnavailable() {
        capabilities.edit().putString("cache_unavailable_backend", backendKey()).apply()
    }
    private var validatedCacheBackend: String? = null
    // Android assigns each profile a block of 100,000 UIDs. Stay in the caller's profile.
    private val userId get() = (AndroidProcess.myUid() / 100_000).toString()

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
    suspend fun exec(vararg command: String): ShellResult = execWithTimeout(30_000, *command)

    suspend fun execWithTimeout(timeoutMillis: Long, vararg command: String): ShellResult = execLive(timeoutMillis, command.toList())

    suspend fun automateCaches(packages: List<String>, onLine: (String) -> Unit): ShellResult {
        val selected = packages.distinct()
        require(selected.isNotEmpty() && selected.size <= 500 && selected.all { Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+").matches(it) })
        return execLive((selected.size * 15_000L + 15_000).coerceAtMost(600_000),
            listOf("/system/bin/env", "CLASSPATH=${context.applicationInfo.sourceDir}", "/system/bin/app_process", "/system/bin",
                "digital.jinatra.tidyra.core.cache.ShellCacheMain", "--caller", context.packageName) + selected, onLine, true)
    }

    private suspend fun execLive(timeoutMillis: Long, command: List<String>, onLine: ((String) -> Unit)? = null,
        handshake: Boolean = false): ShellResult = withContext(Dispatchers.IO) {
        if (state() != State.READY) {
            return@withContext ShellResult(-1, "", "Shizuku is not ready")
        }
        val caller = currentCoroutineContext()
        runCatching {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }

            @Suppress("UNCHECKED_CAST")
            val process = method.invoke(null, command.toTypedArray(), null, null) as ShizukuRemoteProcess
            val control = if (handshake) process.outputStream else null
            fun stopControl() {
                // Shizuku may retain a duplicate pipe descriptor. Send an explicit stop byte
                // before closing instead of depending solely on EOF propagation.
                runCatching { control?.apply { write(1); flush() } }
                runCatching { control?.close() }
            }
            try {
            caller.ensureActive()
            control?.apply { write("START\n".toByteArray()); flush() }
            val result = CommandRunner.run(object : CommandRunner.Process {
                override val stdout get() = process.inputStream
                override val stderr get() = process.errorStream
                override fun waitFor(millis: Long) = process.waitForTimeout(millis, TimeUnit.MILLISECONDS)
                override fun exitCode() = process.exitValue()
                override fun destroy() = process.destroy()
                override fun cancel() = stopControl()
            }, timeoutMillis, cancelled = { !caller.isActive }, onStdoutLine = onLine)
            ShellResult(result.code, result.out, result.error)
            } finally {
                stopControl()
                Thread({ runCatching { process.destroy() } }, "tidyra-remote-cleanup").apply { isDaemon = true; start() }
            }
        }.getOrElse { error ->
            ShellResult(-1, "", error.message ?: error::class.java.simpleName)
        }
    }

    suspend fun clearCache(packageName: String): ShellResult = withContext(Dispatchers.IO) {
        val reason = runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            CachePolicy.blockedReason(packageName, info.uid, info.flags and ApplicationInfo.FLAG_PERSISTENT != 0,
                pm.getPackagesForUid(info.uid).orEmpty().size > 1)
        }.getOrElse { "App unavailable" }
        if (reason != null) return@withContext ShellResult(-1, "", reason)
        // AOSP silently ignores shell callers missing this permission, leaving cmd waiting forever.
        if (!canClearCache()) return@withContext ShellResult(-1, "", "Android requires clearing cache in Settings on this Shizuku setup")
        // Old Android releases can ignore unknown flags. Never risk clearing app data.
        val help = if (validatedCacheBackend == backendKey()) ShellResult(0, "clear [--cache-only]", "")
            else execWithTimeout(5_000, "cmd", "package", "help")
        if (!help.ok || !Regex("clear[^\\n]*\\[--cache-only\\]").containsMatchIn(help.stdout)) {
            if (help.ok || help.stderr.contains("timed out", true)) markCacheUnavailable()
            return@withContext ShellResult(-1, "", "Cache-only clearing could not be verified; use Settings")
        }
        validatedCacheBackend = backendKey()
        // Invoke the service client directly, so timing out kills the actual command
        // instead of leaving cmd running beneath the /system/bin/pm shell wrapper.
        val result = execWithTimeout(8_000, "cmd", "package", "clear", "--user", userId, "--cache-only", packageName)
        if (result.stderr.contains("timed out", true) || result.stderr.contains("did not close", true)) markCacheUnavailable()
        if (result.ok && result.stdout.lineSequence().any { it.trim() == "Success" }) result
        else result.copy(exitCode = if (result.ok) -1 else result.exitCode,
            stderr = result.stderr.ifBlank { "Android did not confirm cache clearing" })
    }

    private fun allowedPackage(packageName: String): Boolean = runCatching {
        if (!Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+").matches(packageName)) return@runCatching false
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        !Protected.isProtectedPackage(packageName, info.flags and
            (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
    }.getOrDefault(false)

    /**
     * Shell-level uninstall. Guarded here as well as in the UI: this runs with ADB
     * rights, so a wrong package name is not a bad screen, it is a broken phone.
     */
    suspend fun uninstall(packageName: String): ShellResult {
        if (!allowedPackage(packageName)) {
            return ShellResult(-1, "", "Refused: $packageName is protected")
        }
        return exec("cmd", "package", "uninstall", "--user", userId, packageName)
    }

    data class RestrictedResult(val folders: List<RestrictedFolder>, val note: String?)

    suspend fun running(): digital.jinatra.tidyra.core.runtime.RunningSnapshot = coroutineScope {
        val processRead = async { execWithTimeout(4_000, "ps", "-A", "-o", "UID,PID,NAME") }
        val serviceRead = async { execWithTimeout(7_000, "dumpsys", "-t", "5", "activity", "services") }
        val processes = processRead.await()
        val services = serviceRead.await()
        val processOk = processes.ok && Regex("(?m)^\\s*UID\\s+PID\\s+NAME\\s*$").containsMatchIn(processes.stdout)
        val serviceOk = services.ok && services.stdout.contains("ACTIVITY MANAGER SERVICES")
        digital.jinatra.tidyra.core.runtime.RunningSnapshot(
            if (processOk) digital.jinatra.tidyra.core.runtime.RunningParser.processes(processes.stdout, userId.toInt()) else emptyList(),
            if (serviceOk) digital.jinatra.tidyra.core.runtime.RunningParser.services(services.stdout, userId.toInt()) else emptyList(),
            processOk, serviceOk, if (processOk && serviceOk) null else "Some running information was unavailable within the time limit. Installed apps remain available; refresh to try again.")
    }

    /** Recheck live package policy at the privileged boundary, never trust a stale row. */
    suspend fun stop(packageName: String, component: String? = null): ShellResult {
        val app = digital.jinatra.tidyra.core.runtime.RuntimeInventory(context).installed()
            .firstOrNull { it.packageName == packageName }
        if (app == null || app.stopBlockedReason != null) return ShellResult(-1, "", "Stop refused: ${app?.stopBlockedReason ?: "app unavailable"}")
        if (component == null) return exec("cmd", "activity", "force-stop", "--user", userId, packageName)
        val target = android.content.ComponentName.unflattenFromString(component)
        if (target == null || target.packageName != packageName || !Regex("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+").matches(component))
            return ShellResult(-1, "", "Invalid service component")
        // Shell cannot individually stop another app's non-exported services. The UI
        // explicitly confirms stopping the owner and ALL its services before this call.
        // A service ending during review must not block that approved owner-app stop.
        return exec("cmd", "activity", "force-stop", "--user", userId, packageName)
    }

    suspend fun restrictedFolders(): RestrictedResult {
        val folders = mutableListOf<RestrictedFolder>()
        val packages = context.packageManager.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }.map { it.packageName }
        var incomplete = false
        val deadline = android.os.SystemClock.elapsedRealtime() + 12_000
        suspend fun measure(vararg args: String): ShellResult {
            val remaining = deadline - android.os.SystemClock.elapsedRealtime()
            return if (remaining <= 0) ShellResult(-1, "", "Folder measurement time limit reached")
                else execWithTimeout(minOf(remaining, 4_000), *args)
        }
        for (name in listOf("data", "obb")) {
            val root = "/storage/emulated/$userId/Android/$name"
            val result = measure("du", "-k", "-d", "1", root)
            folders += RestrictedFolder.parse(result.stdout, root)
            if (!result.ok) incomplete = true
            // Android can hide newly installed packages from the parent directory
            // listing while allowing a known path. Measure installed user packages too.
            for (batch in packages.chunked(64)) {
                val known = measure("du", "-k", "-s", *batch.map { "$root/$it" }.toTypedArray())
                folders += RestrictedFolder.parse(known.stdout, root)
                // Most apps have no directory here. Missing paths are expected.
                if (known.stderr.contains("Permission denied", ignoreCase = true) || known.exitCode == -1) incomplete = true
            }
        }
        return RestrictedResult(folders.distinctBy { it.path }.sortedByDescending { it.size.value },
            if (incomplete) "Some restricted folders could not be read. Shizuku access varies by Android version." else null)
    }
}
