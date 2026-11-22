package digital.jinatra.tidyra.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.jinatra.tidyra.ui.model.ModelViewModel
import digital.jinatra.tidyra.ui.organize.OrganizeViewModel
import digital.jinatra.tidyra.ui.station.StationViewModel
import digital.jinatra.tidyra.ui.theme.TidyraTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import digital.jinatra.tidyra.core.shizuku.ShizukuBridge
import digital.jinatra.tidyra.core.safety.Protected
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.compose.foundation.shape.RoundedCornerShape
import digital.jinatra.tidyra.ui.theme.Module
import digital.jinatra.tidyra.core.cache.CacheAutomation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var pendingShellCaches: List<String>? = null
    private val cacheNotifications = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        pendingShellCaches?.let(stationModel::clearCachesViaAccessibility)
        pendingShellCaches = null
    }
    private fun startShellCaches(packages: List<String>) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingShellCaches = packages.toList()
            cacheNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else stationModel.clearCachesViaAccessibility(packages)
    }
    private val packageEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                stationModel.onPackageRemoved(pkg)
                appsModel.onPackageRemoved(pkg)
            } else if (intent.action != Intent.ACTION_PACKAGE_REMOVED) {
                appsModel.refreshInstalled()
                stationModel.reconcileInstalledApps()
            }
        }
    }

    private val stationModel: StationViewModel by viewModels()
    private val appsModel: digital.jinatra.tidyra.ui.apps.AppsViewModel by viewModels()
    private val organizeModel: OrganizeViewModel by viewModels()
    private val aiModel: ModelViewModel by viewModels()

    /** A model file the user already has. No network, no token, no licence gate. */
    private val pickModelFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "imported-model.task"
        aiModel.import(uri, name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ContextCompat.registerReceiver(this, packageEvents, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package")
        }, ContextCompat.RECEIVER_EXPORTED)

        setContent {
            var dark by rememberSaveable { mutableStateOf<Boolean?>(null) }
            val isDark = dark ?: isSystemInDarkTheme()
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }

            TidyraTheme(darkTheme = isDark) {
                val station by stationModel.state.collectAsStateWithLifecycle()
                val apps by appsModel.state.collectAsStateWithLifecycle()
                val organize by organizeModel.state.collectAsStateWithLifecycle()
                val ai by aiModel.state.collectAsStateWithLifecycle()
                var requestedAction by rememberSaveable { mutableStateOf<String?>(null) }
                var requestedCaches by rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
                var cacheAutomationConsent by rememberSaveable { mutableStateOf(false) }

                Root(
                    apps = apps,
                    station = station,
                    organize = organize,
                    model = ai,
                    darkTheme = isDark,
                    callbacks = RootCallbacks(
                        onRefreshApps = appsModel::refresh,
                        onStopRuntime = appsModel::stop,
                        onDismissAppsMessage = appsModel::dismissMessage,
                        onScan = stationModel::scan,
                        onToggleFinding = stationModel::toggleFinding,
                        onToggleItem = stationModel::toggleItem,
                        onRestore = stationModel::restore,
                        onEmptyTrash = stationModel::emptyTrash,
                        onRestoreMany = stationModel::restoreMany,
                        onRestoreAll = stationModel::restoreAllFromTrash,
                        onPurgeMany = stationModel::purgeMany,
                        onToggleTheme = { dark = !isDark },
                        onRequestShizuku = ::requestShizuku,
                        onExplain = stationModel::explain,
                        onApprove = stationModel::applyPlan,
                        onGrantAllFiles = ::requestAllFilesAccess,
                        onGrantUsage = ::requestUsageAccess,
                        onUninstall = { pkg ->
                            if (!station.working && !station.scanning) {
                                if (station.shizuku == ShizukuBridge.State.READY) requestedAction = pkg
                                else uninstall(pkg)
                            }
                        },
                        onOpenAppSettings = ::openAppSettings,
                        onClearCache = { pkg ->
                            if (!station.working && !station.scanning) {
                                if (station.apps.firstOrNull { it.packageName == pkg }?.cacheBlockedReason != null) openAppSettings(pkg)
                                else requestedCaches = arrayListOf(pkg)
                            }
                        },
                        onToggleCache = stationModel::toggleCache,
                        onSelectAllCache = stationModel::selectAllCache,
                        onRequestCaches = { if (!station.working && !station.scanning && it.isNotEmpty()) requestedCaches = ArrayList(it) },
                        onCancelCache = stationModel::cancelCache,
                        onNextCacheSettings = { stationModel.nextCacheSettings()?.let(::openCacheSettings) },
                        onEndCacheSettings = stationModel::finishCacheSettings,
                        onOpenCacheSettings = ::openCacheSettings,
                        onConfigureCacheAutomation = {
                            if (stationModel.shizuku.state() == digital.jinatra.tidyra.core.shizuku.ShizukuBridge.State.READY) stationModel.useCacheAutomation(true)
                            else cacheAutomationConsent = true
                        },
                        onUseManualCache = { stationModel.useCacheAutomation(false) },
                        onRetentionDays = stationModel::setRetentionDays,
                        onDismissMessage = stationModel::dismissMessage,
                        onSelectFolder = organizeModel::select,
                        onApplyOrganise = organizeModel::apply,
                        onUndoOrganise = organizeModel::undo,
                        onToggleOrganise = organizeModel::toggle,
                        onSelectAllOrganise = organizeModel::selectAll,
                        onReviewOrganise = organizeModel::review,
                        onEditOrganise = organizeModel::editReview,
                        onOpenDestination = organizeModel::openDestination,
                        onNavigateDestination = organizeModel::navigateDestination,
                        onChooseDestination = organizeModel::chooseDestination,
                        onCancelDestination = organizeModel::cancelDestination,
                        onCreateFolder = organizeModel::createFolder,
                        onBrowseSource = organizeModel::browseSource,
                        onDownloadModel = aiModel::download,
                        onCancelDownload = aiModel::cancelDownload,
                        onDeleteModel = aiModel::delete,
                        onLoadModel = aiModel::load,
                        onUnloadModel = aiModel::unload,
                        onToken = aiModel::setToken,
                        onImportModel = { pickModelFile.launch(arrayOf("*/*")) },
                    ),
                )
                requestedAction?.let { pkg ->
                    val name = station.apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                    AlertDialog(
                        shape = RoundedCornerShape(Module.half),
                        onDismissRequest = { requestedAction = null },
                        title = { Text("Uninstall $name?") },
                        text = { Text("$pkg and its app data will be removed using Shizuku. This bypasses Android's uninstall dialog and cannot be undone through Tidyra's trash.") },
                        confirmButton = {
                            TextButton(enabled = !station.working && !station.scanning, onClick = {
                                requestedAction = null
                                stationModel.uninstallWithShizuku(pkg)
                            }) { Text("UNINSTALL") }
                        },
                        dismissButton = { TextButton(onClick = { requestedAction = null }) { Text("CANCEL") } },
                    )
                }
                requestedCaches?.let { packages ->
                    val automatic = !station.preferManualCache && (station.canClearCache || stationModel.shizuku.state() == digital.jinatra.tidyra.core.shizuku.ShizukuBridge.State.READY || (station.useCacheAutomation && station.cacheAutomationAvailable && station.cacheAutomationProblem == null))
                    val names = packages.take(4).joinToString(", ") { pkg -> station.apps.firstOrNull { it.packageName == pkg }?.label ?: pkg }
                    val summary = names + if (packages.size > 4) " and ${packages.size - 4} more" else ""
                    AlertDialog(
                        shape = RoundedCornerShape(Module.half),
                        onDismissRequest = { requestedCaches = null },
                        title = { Text(if (automatic) "Clear cache in ${packages.size} ${if (packages.size == 1) "app" else "apps"}?" else "Review ${packages.size} ${if (packages.size == 1) "app" else "apps"} in Settings?") },
                        text = { Text(if (automatic)
                            "$summary. Only temporary cache will be requested for clearing. App data and logins stay. You can cancel remaining requests." +
                                if (!station.canClearCache) " Android Settings will step through the selected apps automatically. Keep your phone unlocked. Cancel remaining requests from the notification or return to Tidyra. Unrecognized screens are left for review." else ""
                            else "$summary. In each app's Storage & cache screen, tap Clear cache, then return to Tidyra for the next app. Avoid Clear storage; it removes app data.") },
                        confirmButton = {
                            TextButton(enabled = !station.working && !station.scanning, onClick = {
                                requestedCaches = null
                                if (station.preferManualCache) stationModel.startCacheSettings(packages)?.let(::openCacheSettings)
                                else if (stationModel.shizuku.canClearCache()) stationModel.clearCaches(packages)
                                else if (stationModel.shizuku.state() == digital.jinatra.tidyra.core.shizuku.ShizukuBridge.State.READY) startShellCaches(packages)
                                else if (station.useCacheAutomation && CacheAutomation.state.value.connected && CacheAutomation.unavailableReason(this@MainActivity) == null) stationModel.clearCachesViaAccessibility(packages)
                                else stationModel.startCacheSettings(packages)?.let(::openCacheSettings)
                            }) { Text(if (automatic) "Clear cache" else "Open settings") }
                        },
                        dismissButton = { TextButton(onClick = { requestedCaches = null }) { Text("Cancel") } },
                    )
                }
                if (cacheAutomationConsent) AlertDialog(
                    shape = RoundedCornerShape(Module.half),
                    onDismissRequest = { cacheAutomationConsent = false },
                    title = { Text("Automate Clear cache?") },
                    text = { Text("Optional Accessibility access lets Tidyra read Android Settings screens and tap Clear cache for the apps you select and confirm. It does not read inside your apps, tap Clear storage, accept unknown dialogs, or save or send screen content. A visible Cancel control stops remaining requests. Unsupported layouts or ambiguous app names stay manual. You can disable access anytime in Android Accessibility settings.") },
                    confirmButton = { TextButton(onClick = {
                        cacheAutomationConsent = false
                        CacheAutomation.resetUnsupported(this@MainActivity)
                        stationModel.useCacheAutomation(true)
                        if (!CacheAutomation.state.value.connected) runCatching { startActivity(CacheAutomation.permissionIntent(this@MainActivity)) }
                            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
                    }) { Text(if (station.cacheAutomationAvailable) "Use automation" else "Enable in Settings") } },
                    dismissButton = { TextButton(onClick = { cacheAutomationConsent = false }) { Text("Cancel") } },
                )
            }
        }
    }

    /**
     * Shizuku grants permission through its own app, and answers asynchronously. Without
     * this listener the app would keep showing "allow Tidyra" after the user already had.
     */
    private val shizukuListener = rikka.shizuku.Shizuku.OnRequestPermissionResultListener { _, _ ->
        stationModel.refreshPermissions()
    }

    private fun requestShizuku() {
        stationModel.shizuku.addPermissionListener(shizukuListener)
        stationModel.shizuku.requestPermission(SHIZUKU_REQUEST)
    }

    override fun onDestroy() {
        unregisterReceiver(packageEvents)
        stationModel.shizuku.removePermissionListener(shizukuListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // All-files and usage access are granted in Settings, outside the app, so the only
        // way to learn the answer is to look again each time the user comes back.
        stationModel.refreshPermissions()
        stationModel.reconcileInstalledApps()
        appsModel.refreshInstalled()
    }

    private fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val direct = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        runCatching { startActivity(direct) }.onFailure {
            // Some OEM builds have no per-app screen; the full list is the fallback.
            runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        }
    }

    private fun requestUsageAccess() {
        runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
    }

    /** Android's own dialog, one app at a time. Tidyra never uninstalls behind your back. */
    private fun uninstall(packageName: String) {
        runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            if (Protected.isProtectedPackage(packageName,
                    info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)) return
            startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")),
            )
        }
    }

    private companion object {
        const val SHIZUKU_REQUEST = 4001
    }

    private fun openAppSettings(packageName: String) {
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    private fun openCacheSettings(packageName: String) {
        runCatching { startActivity(CacheAutomation.storageIntent(this, packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { openAppSettings(packageName) }
    }
}
