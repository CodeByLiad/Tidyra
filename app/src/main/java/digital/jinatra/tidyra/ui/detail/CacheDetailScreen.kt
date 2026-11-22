package digital.jinatra.tidyra.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import digital.jinatra.tidyra.core.model.Bytes
import digital.jinatra.tidyra.core.model.FindingKind
import digital.jinatra.tidyra.ui.common.AppIcon
import digital.jinatra.tidyra.ui.format.Format
import digital.jinatra.tidyra.ui.station.StationState
import digital.jinatra.tidyra.ui.theme.LocalStationType
import digital.jinatra.tidyra.ui.theme.Module

/** Cache approval is independent of the file-trash plan. Nothing is preselected. */
@Composable
fun CacheDetailScreen(state: StationState, onToggle: (String, Boolean) -> Unit,
    onSelectAll: (Boolean) -> Unit, onRequest: (List<String>) -> Unit, onCancel: () -> Unit,
    onNextSettings: () -> Unit, onEndSettings: () -> Unit, onSettings: (String) -> Unit,
    onConfigureAutomation: () -> Unit, onUseManual: () -> Unit,
    onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val rows = state.findings.firstOrNull { it.kind == FindingKind.APP_CACHE }?.items.orEmpty()
    val apps = state.apps.associateBy { it.packageName }
    val eligible = rows.filter { apps[it.path]?.cacheBlockedReason == null }
    val selected = rows.filter { it.path in state.selectedCachePackages }
    val all = eligible.isNotEmpty() && eligible.all { it.path in state.selectedCachePackages }
    val busy = state.working || state.scanning
    val queueApp = state.cacheQueue.getOrNull(state.cacheQueueIndex)
    val results = state.cacheResults.associateBy { it.packageName }
    val shellAutomatic = state.shizuku == digital.jinatra.tidyra.core.shizuku.ShizukuBridge.State.READY
    val automatic = !state.preferManualCache && (state.canClearCache || shellAutomatic || (state.useCacheAutomation && state.cacheAutomationAvailable && state.cacheAutomationProblem == null))
    Column(modifier.fillMaxSize().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = Module.x3)) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("‹ Back") }
            Text("App cache", style = LocalStationType.current.section, modifier = Modifier.semantics { heading() })
            Text("${rows.size} apps · ${Format.size(Bytes(rows.sumOf { it.size.value }))}",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Text(if (automatic) "Select apps to clear temporary files. App data and logins stay."
                else if (state.preferManualCache) "Select apps to review in Settings, one at a time. Choose Clear cache; app data and logins stay."
                else if (state.cacheAutomationProblem != null) state.cacheAutomationProblem + " Select apps to review one by one."
                else "Android requires Settings on this setup. Select apps to review one by one; choose Clear cache, not Clear storage.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = Module.u, bottom = Module.u))
            if (shellAutomatic) TextButton(enabled = !busy, onClick = if (state.preferManualCache) onConfigureAutomation else onUseManual) {
                Text(if (state.preferManualCache) "Use automatic clearing" else "Use manual Settings")
            }
            if (!state.canClearCache && !shellAutomatic) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Automate Settings", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (state.useCacheAutomation && !state.cacheAutomationAvailable) TextButton(onClick = onConfigureAutomation, enabled = !busy) { Text("Enable") }
                Switch(checked = state.useCacheAutomation, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Automate Settings" },
                    colors = SwitchDefaults.colors(uncheckedThumbColor = colors.onSurfaceVariant,
                        uncheckedTrackColor = colors.surfaceContainer, uncheckedBorderColor = colors.outline,
                        checkedThumbColor = colors.onPrimary, checkedTrackColor = colors.primary),
                    onCheckedChange = { if (it) onConfigureAutomation() else onUseManual() })
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = Module.u)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = Module.x3), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selected.size} selected · ${Format.size(Bytes(selected.sumOf { it.size.value }))}",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onSelectAll(!all) }, enabled = !busy && eligible.isNotEmpty()) {
                        Text(if (all) "Deselect all" else "Select all")
                    }
                }
                HorizontalDivider()
            }
            items(rows, key = { it.path }) { item ->
                val blocked = apps[item.path]?.cacheBlockedReason
                Column {
                    Row(Modifier.fillMaxWidth().padding(end = Module.u), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).toggleable(value = item.path in state.selectedCachePackages,
                        enabled = !busy && blocked == null, role = Role.Checkbox,
                        onValueChange = { onToggle(item.path, it) }).padding(top = Module.u, bottom = Module.u),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = item.path in state.selectedCachePackages, onCheckedChange = null,
                            enabled = !busy && blocked == null, modifier = Modifier.padding(horizontal = Module.u))
                        AppIcon(item.path, 36.dp)
                        Column(Modifier.weight(1f).padding(start = Module.u)) {
                            Text(item.label ?: item.path, style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(Format.size(item.size), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        }
                    }
                    // A sibling action stays outside the checkbox touch target.
                        TextButton(onClick = { onSettings(item.path) }, enabled = !busy) { Text("Settings") }
                    }
                    (results[item.path]?.detail ?: blocked)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = Module.x3, end = Module.x3, bottom = Module.u))
                    }
                    HorizontalDivider()
                }
            }
            if (rows.isEmpty()) item {
                Text("No app cache above 1 MB in the latest measurement.", Modifier.padding(Module.x3))
            }
            if (state.cacheResults.isNotEmpty()) item {
                // Keep outcomes visible even when cleared apps disappear below the scan threshold.
                Column(Modifier.padding(Module.x3)) {
                    state.cacheResults.filter { result -> rows.none { it.path == result.packageName } }.forEach { result ->
                        Text("${apps[result.packageName]?.label ?: result.packageName}: ${result.detail}",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = Module.u))
                    }
                }
            }
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = Module.x3, vertical = Module.u)) {
            state.cacheNote?.takeUnless { it == state.cacheAutomationProblem }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = Module.u))
            }
            when {
                queueApp != null -> {
                    Text("${state.cacheQueueIndex + 1} of ${state.cacheQueue.size} · ${apps[queueApp]?.label ?: queueApp}",
                        style = MaterialTheme.typography.titleMedium)
                    Text("Return here after reviewing Storage & cache.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onEndSettings) { Text("End review") }
                        TextButton(onClick = { onSettings(queueApp) }) { Text("Reopen") }
                        Button(onClick = onNextSettings) { Text(if (state.cacheQueueIndex + 1 == state.cacheQueue.size) "Finish" else "Next app") }
                    }
                }
                state.cacheProgress != null -> {
                    val progress = state.cacheProgress
                    LinearProgressIndicator(progress = { progress.results.size.toFloat() / progress.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${progress.results.size} of ${progress.total} · ${apps[progress.current]?.label ?: "Preparing"}",
                            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onCancel) { Text("Cancel") }
                    }
                }
                else -> Button(onClick = { onRequest(selected.map { it.path }) }, enabled = selected.isNotEmpty() && !busy,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "Updating…" else if (automatic) "Clear selected cache" else "Open selected settings")
                }
            }
        }
    }
}
