package digital.jinatra.tidyra.ui.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import digital.jinatra.tidyra.core.runtime.InstalledApp
import digital.jinatra.tidyra.core.runtime.RunningSnapshot
import digital.jinatra.tidyra.core.runtime.RuntimeInventory
import digital.jinatra.tidyra.core.shizuku.ShizukuBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppsState(val installed: List<InstalledApp> = emptyList(), val running: RunningSnapshot = RunningSnapshot(),
    val busy: Boolean = false, val updatedAt: Long = 0, val message: String? = null, val error: String? = null)

class AppsViewModel(app: Application) : AndroidViewModel(app) {
    private val inventory = RuntimeInventory(app)
    private val bridge = ShizukuBridge(app)
    private val _state = MutableStateFlow(AppsState())
    val state = _state.asStateFlow()

    fun refresh() = operate {
        val installed = inventory.installed()
        val running = if (bridge.state() == ShizukuBridge.State.READY) bridge.running() else RunningSnapshot()
        _state.update { it.copy(installed = installed,
            running = running,
            updatedAt = System.currentTimeMillis()) }
    }

    fun stop(packageName: String, component: String?) = operate {
        val result = bridge.stop(packageName, component)
        val snapshot = if (bridge.state() == ShizukuBridge.State.READY) bridge.running() else RunningSnapshot()
        val apps = inventory.installed()
        val app = apps.firstOrNull { it.packageName == packageName }
        val stillRunning = app?.processes(snapshot)?.isNotEmpty() == true || snapshot.services.any { it.packageName == packageName }
        val verified = snapshot.processesAvailable && snapshot.servicesAvailable
        val message = when {
            !result.ok -> "Could not stop: ${result.stderr.take(200)}"
            !verified -> "Stop command sent; Android could not verify the result. Refresh to check."
            stillRunning -> "Still running or restarted. Android may keep bound services active."
            else -> "${app?.label ?: packageName} stopped. Reopen it to use it again."
        }
        _state.update { it.copy(installed = apps, running = snapshot, updatedAt = System.currentTimeMillis(), message = message) }
    }

    fun dismissMessage() { _state.update { it.copy(message = null) } }
    private fun operate(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.update { it.copy(error = "Could not refresh apps. Try again; no result is assumed.", updatedAt = 0, running = RunningSnapshot()) } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
}
