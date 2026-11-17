package digital.jinatra.tidyra.ui.apps

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import digital.jinatra.tidyra.core.runtime.InstalledApp
import digital.jinatra.tidyra.core.runtime.RunningService
import digital.jinatra.tidyra.core.scan.AppInventory
import digital.jinatra.tidyra.core.safety.Protected
import digital.jinatra.tidyra.core.shizuku.ShizukuBridge
import digital.jinatra.tidyra.ui.common.AppIcon
import digital.jinatra.tidyra.ui.format.Format
import digital.jinatra.tidyra.ui.theme.LocalStationType
import digital.jinatra.tidyra.ui.theme.Module
import androidx.compose.foundation.shape.RoundedCornerShape
import java.text.DateFormat
import java.util.Date
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    state: AppsState,
    apps: List<AppInventory.AppEntry>,
    hasUsageAccess: Boolean,
    shizuku: ShizukuBridge.State,
    onUninstall: (String) -> Unit,
    onOpenAppSettings: (String) -> Unit,
    onClearCache: (String) -> Unit,
    canClearCache: Boolean,
    working: Boolean,
    onGrantUsage: () -> Unit,
    onRefresh: () -> Unit,
    onStop: (String, String?) -> Unit,
    onRequestShizuku: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var view by rememberSaveable { mutableStateOf("Installed") }
    var origin by rememberSaveable { mutableStateOf("Downloaded") }
    var query by rememberSaveable { mutableStateOf("") }
    var stopPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var stopComponent by rememberSaveable { mutableStateOf<String?>(null) }
    val type = LocalStationType.current
    val colors = MaterialTheme.colorScheme
    val busy = working || state.busy
    LaunchedEffect(shizuku) { onRefresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onRefresh() }
    val byPackage = state.installed.associateBy { it.packageName }
    val stats = if (hasUsageAccess) apps.associateBy { it.packageName } else emptyMap()
    fun matches(app: InstalledApp, extra: String = "") =
        (origin == "All" || app.isSystem == (origin == "System")) &&
            (query.isBlank() || "${app.label} ${app.packageName} $extra".contains(query.trim(), ignoreCase = true))
    val appRows = state.installed.filter { matches(it) && (view != "Running" ||
        it.processes(state.running).isNotEmpty() || state.running.services.any { service -> service.packageName == it.packageName }) }
    val serviceRows = state.running.services.filter { service -> byPackage[service.packageName]?.let { matches(it, service.component) } == true }
    val available = shizuku == ShizukuBridge.State.READY &&
        if (view == "Services") state.running.servicesAvailable else state.running.processesAvailable

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Module.x4)) {
        item {
            Column(Modifier.padding(horizontal = Module.x3, vertical = Module.x2), verticalArrangement = Arrangement.spacedBy(Module.u)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Apps", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRefresh, enabled = !busy) { Text(if (state.busy) "REFRESHING" else "REFRESH", style = type.stencil) }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Installed", "Running", "Services").forEachIndexed { index, title ->
                        SegmentedButton(selected = view == title, onClick = { view = title },
                            colors = SegmentedButtonDefaults.colors(activeContainerColor = colors.primary.copy(alpha = 0.06f),
                                activeContentColor = colors.primary, inactiveContainerColor = colors.surface,
                                inactiveContentColor = colors.onSurfaceVariant, activeBorderColor = colors.outline, inactiveBorderColor = colors.outline),
                            shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(title) }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Module.u)) {
                    listOf("All", "Downloaded", "System").forEach { title ->
                        FilterChip(selected = origin == title, onClick = { origin = title }, label = { Text(title) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primary.copy(alpha = 0.06f), selectedLabelColor = colors.primary))
                    }
                }
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Search apps or services") }, modifier = Modifier.fillMaxWidth())
                Text(when (view) {
                    "Installed" -> "Installed apps are listed without usage access. Sizes appear after a storage scan with usage access."
                    "Running" -> "A snapshot of app processes, including foreground and cached processes. Running does not mean wasting battery. Stopping apps does not free storage."
                    else -> "Services with a live process. Stop app stops the owning app and all its services. Android restricts stopping another app's private services individually."
                }, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                if (origin == "System") Text("System apps and their services are shown for inspection. Direct stop and uninstall are protected; Android Settings remains available.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                state.error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium) }
                if (state.updatedAt > 0) Text("Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.updatedAt))} · refresh to check again",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (view == "Installed" && !hasUsageAccess) TextButton(onClick = onGrantUsage) { Text("GRANT USAGE ACCESS", style = type.stencil) }
                if (view != "Installed" && shizuku != ShizukuBridge.State.READY) {
                    Text("Android restricts other apps' running information. Start Shizuku and allow Tidyra to inspect and stop apps. You can still manage installed apps in Android Settings.",
                        style = MaterialTheme.typography.bodyMedium)
                    if (shizuku == ShizukuBridge.State.NOT_PERMITTED) TextButton(onClick = onRequestShizuku) { Text("ALLOW SHIZUKU", style = type.stencil) }
                }
                if (view != "Installed") state.running.note?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium) }
                if (view == "Installed" || available) Text(
                    if (view == "Services") "${Format.count(serviceRows.size, "service")}" else "${Format.count(appRows.size, "app")}",
                    style = MaterialTheme.typography.titleLarge)
            }
            HorizontalDivider(color = colors.outline)
        }
        if (view == "Installed" || available) {
            if ((view == "Services" && serviceRows.isEmpty() || view != "Services" && appRows.isEmpty()) && !state.busy && state.error == null) item {
                Text("No matches in this snapshot. Change the filters or refresh.", Modifier.padding(Module.x3), style = MaterialTheme.typography.bodyLarge)
            }
            if (view == "Services") items(serviceRows, key = { it.component }) { service ->
                val app = byPackage.getValue(service.packageName)
                RuntimeRow(app, service, null, null, busy, shizuku, canClearCache,
                    onOpenAppSettings, onUninstall, onClearCache) { stopPackage = app.packageName; stopComponent = service.component }
            } else items(appRows, key = { it.packageName }) { app ->
                val processText = if (view == "Running") "${Format.count(app.processes(state.running).size, "process", "processes")} · ${Format.count(state.running.services.count { it.packageName == app.packageName }, "service")}" else null
                RuntimeRow(app, null, processText, if (view == "Installed") stats[app.packageName] else null,
                    busy, shizuku, canClearCache, onOpenAppSettings, onUninstall, onClearCache,
                    if (view == "Running") ({ stopPackage = app.packageName; stopComponent = null }) else null)
            }
        }
    }
    stopPackage?.let { pkg ->
        val app = byPackage[pkg]
        val component = stopComponent
        AlertDialog(shape = RoundedCornerShape(Module.half), onDismissRequest = { stopPackage = null },
            title = { Text("Force stop ${app?.label ?: pkg}?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(Module.u)) {
                Text(component ?: pkg, style = MaterialTheme.typography.bodyMedium)
                Text("This stops the entire app and all its services${if (component != null) ", including the service shown above" else ""}. Unsaved work may be lost, and notifications, alarms, and syncing may stop until you reopen it. Files and app data are not deleted. This does not free storage.")
            } },
            confirmButton = { TextButton(enabled = !busy && app?.stopBlockedReason == null && app != null && shizuku == ShizukuBridge.State.READY,
                onClick = { stopPackage = null; onStop(pkg, component) }) { Text("FORCE STOP APP") } },
            dismissButton = { TextButton(onClick = { stopPackage = null }) { Text("CANCEL") } })
    }
}

@Composable
private fun RuntimeRow(
    app: InstalledApp, service: RunningService?, processText: String?, stats: AppInventory.AppEntry?,
    busy: Boolean, shizuku: ShizukuBridge.State, canClearCache: Boolean,
    onSettings: (String) -> Unit, onUninstall: (String) -> Unit, onClearCache: (String) -> Unit,
    onStop: (() -> Unit)?,
) {
    val type = LocalStationType.current
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = Module.x3, vertical = Module.x2)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            AppIcon(app.packageName)
            Column(Modifier.weight(1f).padding(start = Module.x2), verticalArrangement = Arrangement.spacedBy(Module.half)) {
                Text(app.label, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(service?.component?.substringAfter('/') ?: app.packageName, style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(if (service != null) "PID ${service.pid} · ${if (service.foreground) "Foreground service" else "Service"}"
                    else processText ?: if (stats != null) "${Format.size(stats.totalBytes)} · ${Format.days(stats.daysSinceUse)}" else "Storage size not measured",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                if (onStop != null && app.stopBlockedReason != null) Text(app.stopBlockedReason, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        // Actions wrap below the content rather than squeezing names on narrow phones.
        if (onStop != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onSettings(app.packageName) }, enabled = !busy) { Text("APP SETTINGS", style = type.stencil) }
                TextButton(onClick = onStop, enabled = !busy && app.stopBlockedReason == null && shizuku == ShizukuBridge.State.READY) {
                    Text("STOP APP", style = type.stencil)
                }
            }
        } else {
            TextButton(onClick = { onSettings(app.packageName) }, enabled = !busy) { Text("APP SETTINGS", style = type.stencil) }
            if (stats != null && stats.cacheBytes.value > 0) TextButton(onClick = { onClearCache(app.packageName) }, enabled = !busy) {
                Text(if (canClearCache && stats.cacheBlockedReason == null && shizuku == ShizukuBridge.State.READY) "CACHE ${Format.size(stats.cacheBytes)}" else "STORAGE SETTINGS", style = type.stencil)
            }
            if (!Protected.isProtectedPackage(app.packageName, app.isSystem)) TextButton(onClick = { onUninstall(app.packageName) }, enabled = !busy) { Text("UNINSTALL", style = type.stencil) }
        }
    }
    HorizontalDivider(color = colors.outlineVariant)
}
