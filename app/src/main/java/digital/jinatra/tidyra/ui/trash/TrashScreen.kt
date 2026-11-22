package digital.jinatra.tidyra.ui.trash

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.*
import digital.jinatra.tidyra.core.model.Bytes
import digital.jinatra.tidyra.core.trash.TrashPreferences
import digital.jinatra.tidyra.core.trash.TrashStore
import digital.jinatra.tidyra.ui.common.SelectionRing
import digital.jinatra.tidyra.ui.format.Format
import digital.jinatra.tidyra.ui.theme.LocalStationType
import digital.jinatra.tidyra.ui.theme.Module
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TrashScreen(
    entries: List<TrashStore.Entry>,
    totalBytes: Bytes,
    working: Boolean,
    retentionDays: Int,
    onRetentionDays: (Int) -> Unit,
    onRestore: (String) -> Unit,
    onRestoreMany: (Set<String>) -> Unit,
    onRestoreAll: () -> Unit,
    onPurgeMany: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalStationType.current
    val colors = MaterialTheme.colorScheme
    val locale = LocalLocale.current.platformLocale
    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var deleting by remember { mutableStateOf<List<TrashStore.Entry>?>(null) }
    var choosingRetention by remember { mutableStateOf(false) }
    var proposedDays by remember { mutableIntStateOf(retentionDays) }
    val chosen = entries.filter { it.id in picked }
    LaunchedEffect(entries) { picked = picked.intersect(entries.map { it.id }.toSet()) }
    BackHandler(selecting) { selecting = false; picked = emptySet() }

    if (choosingRetention) AlertDialog(
        shape = RoundedCornerShape(Module.half),
        onDismissRequest = { choosingRetention = false },
        title = { Text("Keep trash for") },
        text = {
            Column {
                Text("Applies on the next launch. Items already older than this will be permanently deleted then.")
                TrashPreferences.OPTIONS.forEach { days ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = proposedDays == days, onClick = { proposedDays = days })
                        TextButton(onClick = { proposedDays = days }) {
                            Text(if (days == 0) "Until I empty it" else "$days days")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onRetentionDays(proposedDays); choosingRetention = false }) { Text("SAVE") } },
        dismissButton = { TextButton(onClick = { choosingRetention = false }) { Text("CANCEL") } },
    )

    deleting?.let { snapshot ->
        AlertDialog(
            shape = RoundedCornerShape(Module.half),
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${Format.count(snapshot.size, "item")} forever?") },
            text = {
                Text("${Format.size(Bytes(snapshot.sumOf { it.size.value }))} will be removed from trash. " +
                    "These saved copies cannot be restored after this. Original folders: " +
                    snapshot.map { it.originalPath.substringBeforeLast('/') }.distinct().take(3).joinToString(", "))
            },
            confirmButton = {
                TextButton(enabled = !working, onClick = {
                    onPurgeMany(snapshot.map { it.id }.toSet())
                    deleting = null
                    selecting = false
                    picked = emptySet()
                }) { Text("DELETE FOREVER") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("KEEP FILES") } },
        )
    }

    Column(modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = Module.x4)) {
        item {
            Column(Modifier.padding(horizontal = Module.x3, vertical = Module.x2)) {
                Text("Trash", style = type.section, modifier = Modifier.semantics { heading() })
                Text(
                    if (entries.isEmpty()) "Nothing in trash. Files you approve for removal land here first."
                    else if (totalBytes.value == 0L) "${Format.count(entries.size, "saved item")}. No storage space to reclaim."
                    else "${Format.size(totalBytes)} held in ${Format.count(entries.size, "item")}. Space returns when these copies are permanently deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = Module.u),
                )
                Text("If a file already exists at its old location, the restored copy gets a new name.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = Module.u))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Module.u), verticalArrangement = Arrangement.spacedBy(Module.half)) {
                    Text(if (retentionDays == 0) "Kept until you empty trash" else "Auto-delete after $retentionDays days, on launch",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterVertically))
                    Action("CHANGE", !working) { proposedDays = retentionDays; choosingRetention = true }
                }
            }
            HorizontalDivider(color = colors.outline)
        }
        items(entries, key = { it.id }) { entry ->
            val name = entry.originalPath.substringAfterLast('/')
            val folder = entry.originalPath.substringBeforeLast('/').removePrefix("/storage/emulated/0/")
            Row(
                Modifier.fillMaxWidth().heightIn(min = androidx.compose.ui.unit.Dp(48f))
                    .combinedClickable(enabled = !working,
                        onClick = { if (selecting) picked = if (entry.id in picked) picked - entry.id else picked + entry.id },
                        onLongClick = { selecting = true; picked = picked + entry.id })
                    .semantics {
                        if (selecting) { role = Role.Checkbox; selected = entry.id in picked }
                    }
                    .padding(horizontal = Module.x3, vertical = Module.u),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selecting) {
                    SelectionRing(entry.id in picked, false, colors.outline, colors.primary, colors.onPrimary)
                    Spacer(Modifier.width(Module.x2))
                }
                Column(Modifier.weight(1f).padding(end = Module.u)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    Text("From $folder", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    entry.recoveryNote?.let { note ->
                        Text(note + " Kept until you decide; automatic expiry is off for this copy.",
                            style = MaterialTheme.typography.bodySmall, color = colors.primary,
                            modifier = Modifier.padding(top = Module.u))
                    }
                    Text("Trashed ${SimpleDateFormat("d MMM, HH:mm", locale).format(Date(entry.trashedAtMillis))}",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Format.size(entry.size), style = type.amount)
                    if (!selecting) FilledTonalButton(shape = RoundedCornerShape(Module.half), colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.surfaceContainerHigh, contentColor = colors.onSurface), enabled = !working, onClick = { onRestore(entry.id) }) { Text("Restore") }
                }
            }
            HorizontalDivider(color = colors.outlineVariant)
        }
    }
    if (entries.isNotEmpty()) Surface(color = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Module.x3, vertical = Module.u)) {
            if (selecting) Text("${chosen.size} selected", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Module.u)) {
                OutlinedButton(shape = RoundedCornerShape(Module.half), border = BorderStroke(1.dp, colors.onSurfaceVariant), colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface), enabled = !working, onClick = { selecting = !selecting; picked = emptySet() }) { Text(if (selecting) "Cancel" else "Select") }
                Button(shape = RoundedCornerShape(Module.half), modifier = Modifier.weight(1f), enabled = !working && (!selecting || chosen.isNotEmpty()), onClick = {
                    if (selecting) { onRestoreMany(chosen.map { it.id }.toSet()); selecting = false; picked = emptySet() } else onRestoreAll()
                }) { Text(if (selecting) "Restore ${chosen.size}" else "Restore all") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Module.u)) {
                if (selecting) TextButton(enabled = !working, onClick = { picked = if (chosen.size == entries.size) emptySet() else entries.map { it.id }.toSet() }) { Text(if (chosen.size == entries.size) "Deselect all" else "Select all") }
                OutlinedButton(shape = RoundedCornerShape(Module.half), border = BorderStroke(1.dp, colors.error), modifier = Modifier.weight(1f), enabled = !working && (!selecting || chosen.isNotEmpty()), colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error), onClick = { deleting = if (selecting) chosen.toList() else entries.toList() }) { Text(if (selecting) "Delete selected" else "Empty trash") }
            }
        }
    }
    }
}

@Composable
private fun Action(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(Module.half)) {
        Text(label, style = LocalStationType.current.stencil)
    }
}
