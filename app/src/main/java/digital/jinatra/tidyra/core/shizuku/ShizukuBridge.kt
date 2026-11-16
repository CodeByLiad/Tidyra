package digital.jinatra.tidyra.core.shizuku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
class ShizukuBridge(private val context: Context) {
    private val capabilities = context.getSharedPreferences("shizuku_capabilities", Context.MODE_PRIVATE)
    fun canClearCache(): Boolean = capabilities.getString("cache_unavailable_build", null) != Build.FINGERPRINT
    private fun markCacheUnavailable() { capabilities.edit().putString("cache_unavailable_build", Build.FINGERPRINT).apply() }
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
            val process = method.invoke(null, arrayOf(*command), null, null) as ShizukuRemoteProcess
            coroutineScope {
                val out = async(Dispatchers.IO) { process.inputStream.bufferedReader().use(BufferedReader::readText) }
                val err = async(Dispatchers.IO) { process.errorStream.bufferedReader().use(BufferedReader::readText) }
                try {
                    // Process.waitFor(timeout) uses local process state on Android. Shizuku
                    // exposes its own Binder-backed wait; using the inherited one can return
                    // before the remote process exits and then make exitValue() throw.
                    if (!process.waitForTimeout(30, TimeUnit.SECONDS)) {
                        process.destroy()
                        ShellResult(-1, out.await(), "Shizuku command timed out. ${err.await()}")
                    } else ShellResult(process.exitValue(), out.await(), err.await())
                } finally { process.destroy() }
            }
        }.getOrElse { error ->
            ShellResult(-1, "", error.message ?: error::class.java.simpleName)
        }
    }

    suspend fun clearCache(packageName: String): ShellResult {
        if (!allowedPackage(packageName)) return ShellResult(-1, "", "This package is protected or unavailable")
        if (!canClearCache()) return ShellResult(-1, "", "Cache-only clearing did not complete on this Android build. Use the app's storage settings.")
        // Old Android releases can ignore unknown flags. Never risk clearing app data.
        val help = exec("cmd", "package", "help")
        if (!help.ok || !Regex("clear[^\\n]*\\[--cache-only\\]").containsMatchIn(help.stdout)) {
            if (help.ok) markCacheUnavailable()
            return ShellResult(-1, "", "This Android version does not advertise cache-only clearing")
        }
        // Invoke the service client directly, so timing out kills the actual command
        // instead of leaving cmd running beneath the /system/bin/pm shell wrapper.
        val result = exec("cmd", "package", "clear", "--user", userId, "--cache-only", packageName)
        if (result.stderr.contains("timed out", ignoreCase = true)) markCacheUnavailable()
        return result
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

    suspend fun running(): digital.jinatra.tidyra.core.runtime.RunningSnapshot {
        val processes = exec("ps", "-A", "-o", "UID,PID,NAME")
        val services = exec("dumpsys", "activity", "services")
        val processOk = processes.ok && Regex("(?m)^\\s*UID\\s+PID\\s+NAME\\s*$").containsMatchIn(processes.stdout)
        val serviceOk = services.ok && services.stdout.contains("ACTIVITY MANAGER SERVICES")
        return digital.jinatra.tidyra.core.runtime.RunningSnapshot(
            if (processOk) digital.jinatra.tidyra.core.runtime.RunningParser.processes(processes.stdout, userId.toInt()) else emptyList(),
            if (serviceOk) digital.jinatra.tidyra.core.runtime.RunningParser.services(services.stdout, userId.toInt()) else emptyList(),
            processOk, serviceOk, if (processOk && serviceOk) null else "Android did not provide a complete running snapshot. Refresh or use app settings.")
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
        for (name in listOf("data", "obb")) {
            val root = "/storage/emulated/$userId/Android/$name"
            val result = exec("du", "-k", "-d", "1", root)
            folders += RestrictedFolder.parse(result.stdout, root)
            if (!result.ok) incomplete = true
            // Android can hide newly installed packages from the parent directory
            // listing while allowing a known path. Measure installed user packages too.
            for (batch in packages.chunked(64)) {
                val known = exec("du", "-k", "-s", *batch.map { "$root/$it" }.toTypedArray())
                folders += RestrictedFolder.parse(known.stdout, root)
                // Most apps have no directory here. Missing paths are expected.
                if (known.stderr.contains("Permission denied", ignoreCase = true) || known.exitCode == -1) incomplete = true
            }
        }
        return RestrictedResult(folders.distinctBy { it.path }.sortedByDescending { it.size.value },
            if (incomplete) "Some restricted folders could not be read. Shizuku access varies by Android version." else null)
    }
}
