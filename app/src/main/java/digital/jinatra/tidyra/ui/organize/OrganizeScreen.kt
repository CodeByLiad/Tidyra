package digital.jinatra.tidyra.ui.organize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import digital.jinatra.tidyra.ui.format.Format
import digital.jinatra.tidyra.ui.theme.LocalStationType
import digital.jinatra.tidyra.ui.theme.Module
import java.io.File

/**
 * Filing, with the whole plan shown before anything moves.
 *
 * This screen never deletes. The worst it can do is put a file in a folder you did not
 * expect, and one tap puts it back — the plan is its own undo record.
 */
@Composable
fun OrganizeScreen(
    state: OrganizeState,
    onSelect: (File) -> Unit,
    onApply: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalStationType.current
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = Module.x4),
    ) {
        item {
            Column(
                Modifier.padding(start = Module.x3, end = Module.x3, top = Module.x2, bottom = Module.u),
            ) {
                Text(
                    text = "Loose files get filed by type. Nothing is deleted here, and every move can be undone in one tap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = colors.outline)
        }

        item {
            // A row of five folder names does not fit a phone: it scrolls rather than
            // wrapping a chip onto two lines or dropping the last one off the edge.
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Module.x3, vertical = Module.x2),
                horizontalArrangement = Arrangement.spacedBy(Module.u),
            ) {
                items(state.folders) { folder ->
                    FilterChip(
                        selected = state.selected == folder,
                        onClick = { onSelect(folder) },
                        label = { Text(folder.name, maxLines = 1) },
                        shape = RoundedCornerShape(Module.half),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = colors.onPrimary,
                        ),
                    )
                }
            }
        }

        if (state.layout.isNotEmpty()) {
            item {
                Text(
                    text = "In this folder",
                    style = type.section,
                    color = colors.onSurface,
                    modifier = Modifier.padding(start = Module.x3, end = Module.x3, top = Module.x2, bottom = Module.u),
                )
            }
            items(state.layout) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Module.x3, vertical = Module.u),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (entry.loose) entry.name else entry.name + "/",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (entry.loose) MaterialTheme.colorScheme.primary else colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = Format.count(entry.files, "file"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = Format.size(entry.bytes),
                        style = type.amount,
                        color = colors.onSurfaceVariant,
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Module.x3),
                    color = colors.outlineVariant,
                )
            }
        }

        val plan = state.plan
        when {
            state.selected == null -> item {
                Footnote("Pick a folder to see what Tidyra would do with it.")
            }

            state.working -> item { Footnote("Reading ${state.selected.name}…") }

            plan == null || plan.moves.isEmpty() -> item {
                // "Nothing to do" and "everything here was deliberately skipped" are
                // different answers, and the second one owes the user its reasons.
                Footnote(
                    buildString {
                        append("Nothing to file in ${state.selected.name}.")
                        val skipped = plan?.skipped.orEmpty()
                        if (skipped.isNotEmpty()) {
                            append(" ${Format.count(skipped.size, "file")} left alone: ")
                            append(skipped.take(3).joinToString("; ") { (file, reason) -> "${file.name} — $reason" })
                            if (skipped.size > 3) append("; and ${skipped.size - 3} more")
                        } else {
                            append(" Every file here is already in a folder of its own.")
                        }
                    },
                )
            }

            else -> {
                item {
                    Column(Modifier.padding(horizontal = Module.x3, vertical = Module.x2)) {
                        Text(
                            text = "Planned layout",
                            style = type.section,
                            color = colors.onSurface,
                        )
                        Text(
                            text = "${Format.count(plan.fileCount, "file")} into ${Format.count(plan.byBucket.size, "folder")}. Shown in full before anything moves.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = Module.half),
                        )
                    }
                }

                plan.byBucket.forEach { (bucket, moves) ->
                    item {
                        Column(Modifier.padding(horizontal = Module.x3, vertical = Module.u)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${state.selected.name}/$bucket/",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = colors.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = Format.count(moves.size, "file"),
                                    style = type.amount,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            moves.take(6).forEach { move ->
                                Text(
                                    text = move.from.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = Module.x2, top = 2.dp),
                                )
                            }
                            if (moves.size > 6) {
                                Text(
                                    text = "and ${moves.size - 6} more",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(start = Module.x2, top = 2.dp),
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = Module.x3),
                            color = colors.outlineVariant,
                        )
                    }
                }

                if (plan.skipped.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = Module.x3, vertical = Module.x2)) {
                            Text(
                                text = "Left alone",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.onSurface,
                            )
                            plan.skipped.take(5).forEach { (file, reason) ->
                                Text(
                                    text = "${file.name} — $reason",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = onApply,
                        enabled = !state.working,
                        shape = RoundedCornerShape(Module.half),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = colors.onPrimary,
                        ),
                        modifier = Modifier
                            .padding(horizontal = Module.x3, vertical = Module.x2)
                            .fillMaxWidth(),
                    ) {
                        Text("MOVE ${plan.fileCount} FILES", style = type.stencil)
                    }
                }
            }
        }

        state.lastResult?.let { result ->
            item {
                Column(Modifier.padding(horizontal = Module.x3, vertical = Module.x2)) {
                    Text(
                        text = "${Format.count(result.moved.size, "file")} filed" +
                            if (result.failed.isEmpty()) "" else ", ${result.failed.size} could not be moved",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface,
                    )
                    TextButton(onClick = onUndo, contentPadding = PaddingValues(0.dp)) {
                        Text("PUT THEM BACK", style = type.stencil, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun Footnote(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = Module.x3, vertical = Module.x2),
)
