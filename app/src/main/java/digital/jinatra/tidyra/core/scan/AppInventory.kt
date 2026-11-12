package digital.jinatra.tidyra.core.scan

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import android.os.storage.StorageManager
import digital.jinatra.tidyra.core.model.Bytes
import digital.jinatra.tidyra.core.model.Finding
import digital.jinatra.tidyra.core.model.FindingKind
import digital.jinatra.tidyra.core.model.Risk
import digital.jinatra.tidyra.core.safety.Protected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Per-app storage and time-since-last-use.
 *
 * Both need PACKAGE_USAGE_STATS, which the user grants in Settings. Without it this
 * returns an empty inventory rather than guessing — a cleaner that invents app sizes
 * is exactly the kind of software this one exists not to be.
 */
class AppInventory(private val context: Context) {

    data class AppEntry(
        val packageName: String,
        val label: String,
        val isSystem: Boolean,
        val appBytes: Bytes,
        val dataBytes: Bytes,
        val cacheBytes: Bytes,
        val lastUsedMillis: Long,
    ) {
        val totalBytes: Bytes get() = appBytes + dataBytes + cacheBytes
        val daysSinceUse: Long
            get() = if (lastUsedMillis <= 0) Long.MAX_VALUE
            else TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastUsedMillis)
    }

    fun hasUsageAccess(): Boolean {
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return false
        val now = System.currentTimeMillis()
        val stats = runCatching {
            usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - TimeUnit.DAYS.toMillis(7), now)
        }.getOrNull()
        return !stats.isNullOrEmpty()
    }

    suspend fun load(): List<AppEntry> = withContext(Dispatchers.IO) {
        if (!hasUsageAccess()) return@withContext emptyList()

        val pm = context.packageManager
        val storageStats = context.getSystemService(StorageStatsManager::class.java)
            ?: return@withContext emptyList()
        val user: UserHandle = Process.myUserHandle()
        val lastUsed = lastUsedByPackage()

        pm.getInstalledApplications(PackageManager.GET_META_DATA).mapNotNull { info ->
            val stats = runCatching {
                storageStats.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT,
                    info.packageName,
                    user,
                )
            }.getOrNull() ?: return@mapNotNull null

            AppEntry(
                packageName = info.packageName,
                label = pm.getApplicationLabel(info).toString(),
                isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                appBytes = Bytes(stats.appBytes),
                dataBytes = Bytes(stats.dataBytes),
                cacheBytes = Bytes(stats.cacheBytes),
                lastUsedMillis = lastUsed[info.packageName] ?: 0L,
            )
        }.sortedByDescending { it.totalBytes.value }
    }

    private fun lastUsedByPackage(): Map<String, Long> {
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val now = System.currentTimeMillis()
        val stats = runCatching {
            usage.queryUsageStats(
                UsageStatsManager.INTERVAL_YEARLY,
                now - TimeUnit.DAYS.toMillis(365),
                now,
            )
        }.getOrNull().orEmpty()

        return stats.groupBy { it.packageName }
            .mapValues { (_, list) -> list.maxOf { it.lastTimeUsed } }
    }

    /**
     * Apps untouched for [thresholdDays]. Never pre-selected, never acted on without the
     * system uninstall dialog, and system packages are excluded outright — a storage
     * cleaner has no business proposing that you remove part of the operating system.
     */
    fun unusedAppsFinding(apps: List<AppEntry>, thresholdDays: Long = 90): Finding? {
        val unused = apps.filter {
            it.daysSinceUse >= thresholdDays &&
                !Protected.isProtectedPackage(it.packageName, it.isSystem)
        }
        if (unused.isEmpty()) return null

        return Finding(
            id = "unused-apps",
            kind = FindingKind.UNUSED_APP,
            title = "${unused.size} " + (if (unused.size == 1) "app" else "apps") + " unused for $thresholdDays+ days",
            detail = "Uninstalling takes their data with them. Each one goes through Android's own uninstall dialog, one at a time.",
            frees = Bytes(unused.sumOf { it.totalBytes.value }),
            risk = Risk.REVIEW,
            items = unused.map {
                Finding.Item(
                    path = it.packageName,
                    size = it.totalBytes,
                    lastModifiedMillis = it.lastUsedMillis,
                    packageName = it.packageName,
                    label = it.label,
                )
            },
        )
    }

    /**
     * Cache across all apps. Clearing it needs Shizuku; without it the UI deep-links
     * to each app's storage screen instead of pretending it can do the job.
     */
    fun cacheFinding(apps: List<AppEntry>): Finding? {
        val withCache = apps.filter { it.cacheBytes.value > CACHE_FLOOR }
        if (withCache.isEmpty()) return null

        return Finding(
            id = "app-cache",
            kind = FindingKind.APP_CACHE,
            title = "Cache in ${withCache.size} " + if (withCache.size == 1) "app" else "apps",
            detail = "Rebuilds itself as you use the apps. Nothing is lost — not logins, not downloads you chose to keep.",
            frees = Bytes(withCache.sumOf { it.cacheBytes.value }),
            risk = Risk.SAFE,
            items = withCache.map {
                Finding.Item(
                    path = it.packageName,
                    size = it.cacheBytes,
                    lastModifiedMillis = it.lastUsedMillis,
                    packageName = it.packageName,
                    label = it.label,
                )
            },
        )
    }

    private companion object {
        const val CACHE_FLOOR = 1L * 1024 * 1024
    }
}
