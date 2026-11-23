package digital.jinatra.tidyra.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import digital.jinatra.tidyra.core.model.Finding
import digital.jinatra.tidyra.ui.common.FilePreview
import digital.jinatra.tidyra.ui.format.Format
import digital.jinatra.tidyra.ui.theme.Module
import java.io.File

/** The retained original is read-only; approval only selects its verified extra copies. */
@Composable
internal fun DuplicateComparison(keptPath: String, copy: Finding.Item, extraCopies: Int, first: Boolean,
    last: Boolean, selected: Set<String>,
    onToggle: (String, Boolean) -> Unit) {
    var preview by remember { mutableStateOf<File?>(null) }
    Column(Modifier.padding(horizontal = Module.x3, vertical = Module.x2)) {
        if (first) Text("${extraCopies + 1} identical files · ${Format.size(copy.size)} each",
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        // Each pair is a separate lazy item, including for very large duplicate groups.
        Row(Modifier.fillMaxWidth().padding(top = Module.u), horizontalArrangement = Arrangement.spacedBy(Module.x2)) {
            DuplicateFile(keptPath, true, false, {}, { preview = File(keptPath) }, Modifier.weight(1f))
            DuplicateFile(copy.path, false, copy.path in selected, { onToggle(copy.path, it) },
                { preview = File(copy.path) }, Modifier.weight(1f))
        }
    }
    if (last) HorizontalDivider()
    preview?.let { file ->
        Dialog(onDismissRequest = { preview = null }) {
            Surface {
                Column(Modifier.padding(Module.x2)) {
                    Text(file.name, style = MaterialTheme.typography.titleMedium)
                    FilePreview(file, Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 400.dp).height(320.dp), large = true)
                    Text(displayPath(file.path), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { preview = null }, modifier = Modifier.align(Alignment.End)) { Text("Close preview") }
                }
            }
        }
    }
}

@Composable
private fun DuplicateFile(path: String, kept: Boolean, selected: Boolean, onToggle: (Boolean) -> Unit,
    onPreview: () -> Unit, modifier: Modifier = Modifier) {
    val file = File(path)
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        FilePreview(file, Modifier.fillMaxWidth().height(120.dp)
            .clickable(onClickLabel = "View ${file.name}", onClick = onPreview))
        Text(file.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Module.u).clickable(onClick = onPreview))
        Text(displayPath(file.parent.orEmpty()), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(onClickLabel = "Full file location", onClick = onPreview))
        if (kept) Text("Keeping this copy", style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically))
        else Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(selected, role = Role.Checkbox, onValueChange = onToggle), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(selected, onCheckedChange = null)
            Text(if (selected) "Move to Trash" else "Select copy", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun displayPath(path: String): String = path.removePrefix("/storage/emulated/0/")
