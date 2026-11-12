package digital.jinatra.tidyra.ui.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import digital.jinatra.tidyra.core.model.Bytes
import digital.jinatra.tidyra.core.trash.TrashStore
import digital.jinatra.tidyra.ui.format.Format
import digital.jinatra.tidyra.ui.theme.Crimson
import digital.jinatra.tidyra.ui.theme.LocalStationType
import digital.jinatra.tidyra.ui.theme.Module
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The recycle bin Android does not have.
 *
 * Deleting over ADB or through most cleaners is final. Here every approved deletion is a
 * move, recorded with where it came from, and this screen is where it goes back. The one
 * honest caveat is stated up front: trashed files still take up space until they are
 * purged, so "moved to trash" and "freed" are two different numbers.
 */
@Composable
fun TrashScreen(
    entries: List<TrashStore.Entry>,
    totalBytes: Bytes,
    working: Boolean,
    onRestore: (String) -> Unit,
    onEmpty: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalStationType.current
    val colors = MaterialTheme.colorScheme
    var confirming by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = Module.x4),
    ) {
        item {
            Column(Modifier.padding(start = Module.x3, end = Module.x3, top = Module.x2, bottom = Module.x2)) {
                Text(
                    text = "Trash",
                    style = type.wordmark,
                    color = colors.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = if (entries.isEmpty()) {
                        "Empty. Anything you approve for deletion lands here first, and can be put back."
                    } else {
                        "${Format.size(totalBytes)} held in ${Format.count(entries.size, "file")}. " +
                            "This space is not free until the trash is emptied — that part is irreversible."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = Module.half),
                )
            }
            HorizontalDivider(color = colors.outline)
        }

        if (entries.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Module.x3, vertical = Module.x2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (confirming) "This cannot be undone" else "Auto-purged after 14 days",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (confirming) Crimson else colors.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { if (confirming) onEmpty().also { confirming = false } else confirming = true }, enabled = !working) {
                        Text(
                            text = if (confirming) "YES, DELETE FOR GOOD" else "EMPTY TRASH",
                            style = type.stencil,
                            color = Crimson,
                        )
                    }
                }
                HorizontalDivider(color = colors.outline)
            }

            items(entries, key = { it.trashedPath }) { entry ->
                TrashRow(entry, working, onRestore)
            }
        }
    }
}

@Composable
private fun TrashRow(entry: TrashStore.Entry, working: Boolean, onRestore: (String) -> Unit) {
    val type = LocalStationType.current
    val colors = MaterialTheme.colorScheme
    val name = entry.originalPath.substringAfterLast('/')
    val folder = entry.originalPath.substringBeforeLast('/').removePrefix("/storage/emulated/0/")

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Module.x3, vertical = Module.u + 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "from $folder",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "trashed ${dateOf(entry.trashedAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Format.size(entry.size),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                )
                TextButton(
                    onClick = { onRestore(entry.originalPath) },
                    enabled = !working,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("PUT BACK", style = type.stencil, color = Crimson)
                }
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
    }
}

private val formatter = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

private fun dateOf(millis: Long): String = formatter.format(Date(millis))
