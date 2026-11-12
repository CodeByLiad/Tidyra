package digital.jinatra.tidyra.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.jinatra.tidyra.ui.station.StationScreen
import digital.jinatra.tidyra.ui.station.StationViewModel
import digital.jinatra.tidyra.ui.theme.TidyraTheme

class MainActivity : ComponentActivity() {

    private val model: StationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TidyraTheme {
                val state by model.state.collectAsStateWithLifecycle()

                StationScreen(
                    state = state,
                    onScan = model::scan,
                    onToggleFinding = model::toggleFinding,
                    onApprove = model::applyPlan,
                    onGrantAllFiles = ::requestAllFilesAccess,
                    onGrantUsage = ::requestUsageAccess,
                    onOpenFinding = { },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // All-files and usage access are both granted in Settings, outside the app, so the
        // only way to learn the answer is to look again each time the user comes back.
        model.refreshPermissions()
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
}
