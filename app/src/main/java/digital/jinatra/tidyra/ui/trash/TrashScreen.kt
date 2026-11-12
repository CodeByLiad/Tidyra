package digital.jinatra.tidyra.ui.trash

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
 * Every approved deletion is a move, recorded with where it came from, and this is where
 * it goes back. Long-press any row to start picking several; the bar then acts on exactly
 * what is picked. The one honest caveat stays in view: trashed files still occupy storage
 * until they are purged, so "moved to trash" and "freed" are different numbers.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(
    entries: List<TrashStore.Entry>,
    totalBytes: Bytes,
    working: Boolean,
    onRestore: (String) -> Unit,
    onRestoreMany: (Set<String>) -> Unit,
    onRestoreAll: () -> Unit,
    onPurgeMany: (Set<String>) -> Unit,
    onEmpty: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalStationType.current
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current

    val picked = remember { mutableStateMapOf<String, Boolean>() }
    var selecting by remember { mutableStateOf(false) }
    var confirmingEmpty by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val chosen = picked.filterValues { it }.keys
    val chosenBytes = Bytes(entries.filter { it.originalPath in chosen }.sumOf { it.size.value })

    fun clearSelection() {
        picked.clear()
        selecting = false
        confirmingDelete = false
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = Module.x4),
    ) {
        item {
            Text(
                text = if (entries.isEmpty()) {
                    "Empty. Anything you approve for deletion lands here first, and can be put back."
                } else {
                    "${Format.size(totalBytes)} held in ${Format.count(entries.size, "file")}. " +
                        "This space is not free until the trash is emptied — that part is irreversible."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = Module.x3, end = Module.x3, top = Module.x2, bottom = Module.x2),
            )
            HorizontalDivider(color = colors.outline)
        }

        if (entries.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Module.x3, vertical = Module.u),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Module.x2),
                ) {
                    if (selecting) {
                        Text(
                            text = if (confirmingDelete) {
                                "Deleting ${chosen.size} for good"
                            } else {
                                "${chosen.size} picked · ${Format.size(chosenBytes)}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (confirmingDelete) Crimson else colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (!confirmingDelete) {
                            Action("PUT BACK", enabled = chosen.isNotEmpty() && !working) {
                                onRestoreMany(chosen.toSet())
                                clearSelection()
                            }
                        }
                        Action(
                            label = if (confirmingDelete) "YES, DELETE" else "DELETE",
                            enabled = chosen.isNotEmpty() && !working,
                        ) {
                            if (confirmingDelete) {
                                onPurgeMany(chosen.toSet())
                                clearSelection()
                            } else {
                                confirmingDelete = true
                            }
                        }
                        Action("CANCEL", enabled = true, color = colors.onSurfaceVariant) { clearSelection() }
                    } else {
                        Text(
                            text = if (confirmingEmpty) "This cannot be undone" else "Auto-purged after 14 days · long-press to pick several",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (confirmingEmpty) Crimson else colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (!confirmingEmpty) {
                            Action("RESTORE ALL", enabled = !working) { onRestoreAll() }
                        }
                        Action(
                            label = if (confirmingEmpty) "YES, EMPTY" else "EMPTY",
                            enabled = !working,
                        ) {
                            if (confirmingEmpty) {
                                onEmpty()
                                confirmingEmpty = false
                            } else {
                                confirmingEmpty = true
                            }
                        }
                    }
                }
                HorizontalDivider(color = colors.outline)
            }

            items(entries, key = { it.trashedPath }) { entry ->
                TrashRow(
                    entry = entry,
                    selecting = selecting,
                    selected = picked[entry.originalPath] == true,
                    working = working,
                    onRestore = { onRestore(entry.originalPath) },
                    onTap = {
                        if (selecting) {
                            picked[entry.originalPath] = picked[entry.originalPath] != true
                        }
                    },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        selecting = true
                        picked[entry.originalPath] = true
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashRow(
    entry: TrashStore.Entry,
    selecting: Boolean,
    selected: Boolean,
    working: Boolean,
    onRestore: () -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val type = LocalStationType.current
    val colors = MaterialTheme.colorScheme
    val name = entry.originalPath.substringAfterLast('/')
    val folder = entry.originalPath.substringBeforeLast('/').removePrefix("/storage/emulated/0/")

    Column(
        Modifier.combinedClickable(onClick = onTap, onLongClick = onLongPress),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Module.x3, vertical = Module.u + 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (selected) Crimson else colors.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Text("✓", color = colors.onPrimary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = if (selecting) Module.x2 else 0.dp),
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
                    text = "from $folder · trashed ${dateOf(entry.trashedAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Format.size(entry.size),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                )
                if (!selecting) {
                    TextButton(
                        onClick = onRestore,
                        enabled = !working,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("PUT BACK", style = type.stencil, color = Crimson)
                    }
                }
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
    }
}

@Composable
private fun Action(
    label: String,
    enabled: Boolean,
    color: androidx.compose.ui.graphics.Color = Crimson,
    onClick: () -> Unit,
) {
    val type = LocalStationType.current
    TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(0.dp)) {
        Text(label, style = type.stencil, color = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val formatter = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

private fun dateOf(millis: Long): String = formatter.format(Date(millis))
