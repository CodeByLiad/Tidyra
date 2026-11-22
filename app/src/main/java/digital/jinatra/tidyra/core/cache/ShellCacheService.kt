package digital.jinatra.tidyra.core.cache

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import digital.jinatra.tidyra.core.shizuku.ShizukuBridge
import kotlinx.coroutines.*
import org.json.JSONObject

/** A confirmed, finite selection. Never restarted or resumed after process death. */
class ShellCacheService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CANCEL) { if (job == null) stopSelf() else job?.cancel(); return START_NOT_STICKY }
        if (job != null) return START_NOT_STICKY
        val approved = intent?.getStringArrayListExtra("packages")?.distinct().orEmpty()
        startForeground(71, notification(0, approved.size))
        val runId = intent?.getLongExtra("runId", 0) ?: 0
        job = scope.launch {
            val results = linkedMapOf<String, CacheBatch.Item>()
            var current: String? = null
            var note = "Finished reviewing selected caches."
            var wake: PowerManager.WakeLock? = null
            fun publish(running: Boolean) = CacheAutomation.publish(CacheAutomation.State(connected = CacheAutomation.service != null, runId = runId,
                running = running, current = current, total = approved.size, results = results.values.toList(), note = if (running) null else note, viaShizuku = true))
            try {
                require(approved.size in 1..500)
                check(!getSystemService(KeyguardManager::class.java).isDeviceLocked) { "Unlock your phone before starting automatic clearing." }
                @Suppress("DEPRECATION")
                wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "$packageName:cache").apply { acquire(600_000) }
                publish(true)
                val command = ShizukuBridge(this@ShellCacheService).automateCaches(approved) { line ->
                    if (!line.startsWith("TIDYRA_CACHE:")) return@automateCaches
                    val event = runCatching { JSONObject(line.substringAfter("TIDYRA_CACHE:")) }.getOrNull() ?: return@automateCaches
                    val pkg = event.optString("package")
                    synchronized(results) {
                        if (pkg in approved) when (event.optString("event")) {
                            "progress" -> current = pkg
                            "requested", "skipped" -> {
                                results[pkg] = CacheBatch.Item(pkg, if (event.optString("event") == "requested") CacheBatch.Status.REQUESTED else CacheBatch.Status.NOT_ATTEMPTED, event.optString("detail").take(240))
                                current = null
                            }
                        }
                        publish(true)
                        getSystemService(NotificationManager::class.java).notify(71, notification(results.size, approved.size))
                    }
                }
                if (!command.ok || results.size != approved.size) note = "Some caches could not be completed automatically. Review the results or try again."
            } catch (cancelled: CancellationException) {
                note = "Cancelled. A request already sent to Android may still finish."
            } catch (error: Exception) {
                note = if (error is IllegalStateException) error.message ?: "Automatic clearing stopped." else "Automatic clearing could not start. Check Shizuku and try again."
            } finally {
                runCatching { wake?.let { if (it.isHeld) it.release() } }
                synchronized(results) {
                    approved.forEach { results.putIfAbsent(it, CacheBatch.Item(it, CacheBatch.Status.NOT_ATTEMPTED, "Not confirmed")) }
                    current = null
                    publish(false)
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }
    private fun notification(done: Int, total: Int): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("cache_batch", "Cache clearing", NotificationManager.IMPORTANCE_LOW))
        val cancel = PendingIntent.getService(this, 71, Intent(this, ShellCacheService::class.java).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, "cache_batch").setSmallIcon(android.R.drawable.ic_menu_delete)
            .setContentTitle("Clearing selected app caches").setContentText("Android Settings opens automatically. Keep your phone unlocked.")
            .setProgress(total, done, total == 0).setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(Notification.Action.Builder(null, "Cancel", cancel).build()).build()
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object {
        private const val CANCEL = "digital.jinatra.tidyra.CANCEL_CACHE"
        fun start(context: Context, packages: List<String>): Long {
            check(!CacheAutomation.state.value.running) { "A cache batch is already running" }
            val id = android.os.SystemClock.elapsedRealtimeNanos()
            context.startForegroundService(Intent(context, ShellCacheService::class.java).putStringArrayListExtra("packages", ArrayList(packages)).putExtra("runId", id))
            return id
        }
        fun cancel(context: Context) { context.startService(Intent(context, ShellCacheService::class.java).setAction(CANCEL)) }
    }
}
