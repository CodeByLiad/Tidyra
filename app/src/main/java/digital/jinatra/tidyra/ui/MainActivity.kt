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
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.jinatra.tidyra.ui.model.ModelViewModel
import digital.jinatra.tidyra.ui.organize.OrganizeViewModel
import digital.jinatra.tidyra.ui.station.StationViewModel
import digital.jinatra.tidyra.ui.theme.TidyraTheme

class MainActivity : ComponentActivity() {

    private val stationModel: StationViewModel by viewModels()
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
            TidyraTheme {
                val station by stationModel.state.collectAsStateWithLifecycle()
                val organize by organizeModel.state.collectAsStateWithLifecycle()
                val ai by aiModel.state.collectAsStateWithLifecycle()

                Root(
                    station = station,
                    organize = organize,
                    model = ai,
                    callbacks = RootCallbacks(
                        onScan = stationModel::scan,
                        onToggleFinding = stationModel::toggleFinding,
                        onToggleItem = stationModel::toggleItem,
                        onApprove = stationModel::applyPlan,
                        onGrantAllFiles = ::requestAllFilesAccess,
                        onGrantUsage = ::requestUsageAccess,
                        onUninstall = ::uninstall,
                        onOpenAppSettings = ::openAppSettings,
                        onClearAllCaches = { stationModel.clearCachesWithShizuku { _, _ -> } },
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
            }
        }
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
            startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")),
            )
        }
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
