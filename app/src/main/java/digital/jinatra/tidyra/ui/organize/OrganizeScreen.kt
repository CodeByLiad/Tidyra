package digital.jinatra.tidyra.ui.organize

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import digital.jinatra.tidyra.R
import digital.jinatra.tidyra.core.organize.Organizer
import digital.jinatra.tidyra.ui.common.FilePreview
import digital.jinatra.tidyra.ui.format.Format
import digital.jinatra.tidyra.ui.theme.Module
import java.io.File

@Composable
fun OrganizeScreen(
    state: OrganizeState,
    onSelect: (File) -> Unit,
    onApply: () -> Unit,
    onUndo: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onReview: () -> Unit,
    onEdit: () -> Unit,
    onOpenDestination: (Set<String>) -> Unit,
    onNavigateDestination: (File) -> Unit,
    onChooseDestination: () -> Unit,
    onCancelDestination: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onBrowseSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var previewPath by rememberSaveable { mutableStateOf<String?>(null) }
    var showSkipped by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val reviewing = state.reviewPlan != null
    val moves = (state.reviewPlan ?: state.plan)?.moves.orEmpty()
    val selected = state.selectedPaths
    val blocked = selected.any { it in state.issues }
    val preview = state.plan?.moves?.firstOrNull { it.from.path == previewPath }
    BackHandler(enabled = reviewing || state.destinationDirectory != null) {
        if (state.destinationDirectory != null) onCancelDestination() else onEdit()
    }
    if (state.destinationDirectory != null) {
        DestinationBrowser(state, onNavigateDestination, onChooseDestination, onCancelDestination, onCreateFolder, modifier)
        return
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Module.x2, vertical = Module.half), verticalAlignment = Alignment.CenterVertically) {
            if (reviewing) {
                Text("Review move", Modifier.weight(1f).padding(start = Module.u), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onEdit, enabled = !state.working) { Text("Edit") }
            } else {
                TextButton(onClick = onBrowseSource, enabled = !state.working,
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurface),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Change source folder" }) {
                    Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(20.dp))
                    Text(state.selected?.let { folderPath(it, state.root) } ?: "Choose folder", Modifier.weight(1f).padding(horizontal = Module.u),
                        style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(20.dp))
                }
                IconButton(onClick = { state.selected?.let(onSelect) }, enabled = !state.working && state.selected != null) {
                    Icon(painterResource(R.drawable.ic_refresh), "Refresh folder")
                }
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
        if (!reviewing && moves.size > 1) Row(Modifier.fillMaxWidth().padding(horizontal = Module.x3), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onSelectAll(selected.size != moves.size) }, enabled = !state.working) {
                Text(if (selected.size == moves.size) "Deselect all" else "Select all")
            }
            Text(if (selected.isEmpty()) Format.count(moves.size, "file") else "${selected.size} selected", Modifier.weight(1f).padding(start = Module.u),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (selected.size > 1) TextButton(onClick = { onOpenDestination(selected) }, enabled = !state.working) { Text("Set folder") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = Module.x2)) {
            if (state.working) item { Note("${state.actionLabel}…") }
            else if (state.selected == null) item { Note("Choose a folder to see its files.") }
            else if (moves.isEmpty()) item { Note("No loose files here. Open another folder to see its files.") }
            items(moves, key = { it.from.path }) { move ->
                MoveRow(move, state.root, if (reviewing) null else move.from.path in selected,
                    state.issues[move.from.path], state.working,
                    onToggle = { onToggle(move.from.path, it) }, onPreview = { previewPath = move.from.path },
                    onDestination = { onOpenDestination(setOf(move.from.path)) })
            }
            if (!reviewing && state.plan?.skipped?.isNotEmpty() == true) {
                item { TextButton(onClick = { showSkipped = !showSkipped }, modifier = Modifier.padding(horizontal = Module.x3)) {
                    Text("${state.plan.skipped.size} left alone")
                } }
                if (showSkipped) items(state.plan.skipped, key = { it.first.path }) { (file, reason) -> Note("${file.name}: $reason") }
            }
            state.message?.let { item { Note(it) } }
            if (!reviewing) state.lastResult?.let { result -> item {
                Row(Modifier.fillMaxWidth().padding(horizontal = Module.x3, vertical = Module.u), verticalAlignment = Alignment.CenterVertically) {
                    Text("Last move · ${Format.count(result.moved.size, "file")}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    TextButton(onClick = onUndo, enabled = !state.working) { Text("Undo") }
                }
            } }
        }
        if (reviewing || selected.isNotEmpty()) {
            HorizontalDivider(color = colors.outlineVariant)
            Column(Modifier.padding(horizontal = Module.x3, vertical = Module.u)) {
                if (blocked && !reviewing) Text("Choose another folder for conflicting files.", style = MaterialTheme.typography.bodySmall, color = colors.error)
                Button(onClick = if (reviewing) onApply else onReview, enabled = !state.working && !blocked,
                    shape = RoundedCornerShape(Module.half), modifier = Modifier.fillMaxWidth()) {
                    Text(if (reviewing) "Move ${Format.count(moves.size, "file")}" else "Review ${Format.count(selected.size, "file")}")
                }
            }
        }
    }
    preview?.let { move ->
        AlertDialog(shape = RoundedCornerShape(Module.half), onDismissRequest = { previewPath = null },
            title = { Text(move.from.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Module.u)) {
                FilePreview(move.from, Modifier.fillMaxWidth().height(280.dp), large = true, version = move.expectedModified)
                Text("From: ${folderPath(move.from.parentFile!!, state.root)}", style = MaterialTheme.typography.bodySmall)
                Text("To: ${folderPath(move.to.parentFile!!, state.root)}", style = MaterialTheme.typography.bodySmall)
                Text("${Format.size(move.expectedSize)} · ${move.from.name}", style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(onClick = { previewPath = null }) { Text("Close") } },
            dismissButton = { TextButton(enabled = !state.working, onClick = { previewPath = null; onOpenDestination(setOf(move.from.path)) }) { Text("Change folder") } })
    }
}

@Composable
private fun MoveRow(move: Organizer.Move, root: File, selected: Boolean?, issue: String?, working: Boolean,
    onToggle: (Boolean) -> Unit, onPreview: () -> Unit, onDestination: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = Module.x3, vertical = Module.x2), verticalArrangement = Arrangement.spacedBy(Module.half)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Module.u)) {
            FilePreview(move.from, Modifier.size(96.dp).clip(RoundedCornerShape(Module.half))
                .clickable(role = Role.Button, onClickLabel = "Preview ${move.from.name}", onClick = onPreview), version = move.expectedModified)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Module.half)) {
                Text(move.from.name, Modifier.clickable(onClick = onPreview), style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (selected == null) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                Text(Format.size(move.expectedSize), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (selected == null) Text("From: ${folderPath(move.from.parentFile!!, root)}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                TextButton(onClick = onDestination, enabled = !working, contentPadding = PaddingValues(0.dp)) {
                    Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(16.dp))
                    Text("To: ${folderPath(move.to.parentFile!!, root)}", Modifier.padding(start = Module.half),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (selected != null) Checkbox(checked = selected, onCheckedChange = onToggle, enabled = !working,
                modifier = Modifier.semantics { contentDescription = "Select ${move.from.name}" })
        }
        issue?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
    }
    HorizontalDivider(Modifier.padding(horizontal = Module.x3), color = colors.outlineVariant)
}

@Composable
private fun DestinationBrowser(state: OrganizeState, onNavigate: (File) -> Unit, onChoose: () -> Unit,
    onCancel: () -> Unit, onCreate: (String) -> Unit, modifier: Modifier) {
    val directory = state.destinationDirectory!!
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Module.x3), verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.choosingSource) "Source folder" else "Destination", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (!state.choosingSource) TextButton(onClick = { creating = true; name = "" }, enabled = !state.working) { Text("New folder") }
            TextButton(onClick = onCancel, enabled = !state.working) { Text("Cancel") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Module.x3), verticalAlignment = Alignment.CenterVertically) {
            Text(folderPath(directory, state.root), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            TextButton(onClick = { directory.parentFile?.let(onNavigate) }, enabled = !state.working && directory != state.root) { Text("Up") }
        }
        HorizontalDivider(color = colors.outlineVariant)
        LazyColumn(Modifier.weight(1f)) {
            if (state.working) item { Note("${state.actionLabel}…") }
            if (state.destinationFolders.isEmpty() && !state.working) item { Note("No subfolders") }
            items(state.destinationFolders, key = { it.path }) { folder ->
                TextButton(onClick = { onNavigate(folder) }, enabled = !state.working, modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = Module.x3, vertical = Module.x2)) {
                    Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                    Text(folder.name, modifier = Modifier.weight(1f).padding(start = Module.x2), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                }
            }
            state.message?.let { item { Note(it) } }
        }
        HorizontalDivider(color = colors.outlineVariant)
        Button(onClick = onChoose, enabled = !state.working, shape = RoundedCornerShape(Module.half),
            modifier = Modifier.padding(horizontal = Module.x3, vertical = Module.u).fillMaxWidth()) {
            Text(if (state.choosingSource) "Open folder" else "Use this folder")
        }
    }
    if (creating) AlertDialog(shape = RoundedCornerShape(Module.half), onDismissRequest = { creating = false },
        title = { Text("New folder", style = MaterialTheme.typography.titleMedium) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Folder name") }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { creating = false; onCreate(name) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } })
}

private fun folderPath(folder: File, root: File): String = when {
    folder.path == root.path -> "Internal storage"
    folder.path.startsWith(root.path + File.separator) -> folder.path.removePrefix(root.path + File.separator).replace(File.separator, " / ")
    else -> folder.path
}

@Composable
private fun Note(text: String) = Text(text, Modifier.padding(horizontal = Module.x3, vertical = Module.u),
    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
