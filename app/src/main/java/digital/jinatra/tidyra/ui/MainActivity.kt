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

class MainActivity : ComponentActivity() {

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
                var requestedAction by rememberSaveable { mutableStateOf<Pair<String, Boolean>?>(null) }

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
                            if (station.shizuku == ShizukuBridge.State.READY) requestedAction = pkg to true
                            else uninstall(pkg)
                        },
                        onOpenAppSettings = ::openAppSettings,
                        onClearCache = { pkg ->
                            if (station.shizuku == ShizukuBridge.State.READY && station.canClearCache) requestedAction = pkg to false
                            else openAppSettings(pkg)
                        },
                        onRetentionDays = stationModel::setRetentionDays,
                        onDismissMessage = stationModel::dismissMessage,
                        onSelectFolder = organizeModel::select,
                        onApplyOrganise = organizeModel::apply,
                        onUndoOrganise = organizeModel::undo,
                        onDownloadModel = aiModel::download,
                        onCancelDownload = aiModel::cancelDownload,
                        onDeleteModel = aiModel::delete,
                        onLoadModel = aiModel::load,
                        onUnloadModel = aiModel::unload,
                        onToken = aiModel::setToken,
                        onImportModel = { pickModelFile.launch(arrayOf("*/*")) },
                    ),
                )
                requestedAction?.let { (pkg, removing) ->
                    val name = station.apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                    AlertDialog(
                        shape = RoundedCornerShape(Module.half),
                        onDismissRequest = { requestedAction = null },
                        title = { Text(if (removing) "Uninstall $name?" else "Clear cache for $name?") },
                        text = { Text(if (removing)
                            "$pkg and its app data will be removed using Shizuku. This bypasses Android's uninstall dialog and cannot be undone through Tidyra's trash."
                            else "$pkg will lose only its temporary cache. It may load more slowly next time. App data and logins stay. If cache-only clearing is unsupported, nothing will be cleared.") },
                        confirmButton = {
                            TextButton(enabled = !station.working && !station.scanning, onClick = {
                                requestedAction = null
                                if (removing) stationModel.uninstallWithShizuku(pkg) else stationModel.clearCacheWithShizuku(pkg)
                            }) { Text(if (removing) "UNINSTALL" else "CLEAR CACHE") }
                        },
                        dismissButton = { TextButton(onClick = { requestedAction = null }) { Text("CANCEL") } },
                    )
                }
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
        stationModel.shizuku.removePermissionListener(shizukuListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // All-files and usage access are granted in Settings, outside the app, so the only
        // way to learn the answer is to look again each time the user comes back.
        stationModel.refreshPermissions()
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
}
