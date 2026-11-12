package digital.jinatra.tidyra.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import digital.jinatra.tidyra.core.model.Bytes
import digital.jinatra.tidyra.core.model.Finding
import digital.jinatra.tidyra.core.model.FindingKind
import digital.jinatra.tidyra.core.model.Risk
import digital.jinatra.tidyra.ui.common.AppIcon
import digital.jinatra.tidyra.ui.common.CheckMark
import digital.jinatra.tidyra.ui.common.ChevronLeftMark
import digital.jinatra.tidyra.ui.format.Format
import digital.jinatra.tidyra.ui.theme.Amber
import digital.jinatra.tidyra.ui.theme.LocalStationType
import digital.jinatra.tidyra.ui.theme.Module
import digital.jinatra.tidyra.ui.theme.Olive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What is actually in a finding, item by item.
 *
 * This is where approval really happens. The overview proposes a group; here the user
 * sees every file it would remove, with its size, its age and — for a duplicate — the
 * copy that is being kept, and picks exactly which ones go. Selecting the finding on the
 * overview is shorthand for "all of these"; this screen is how that gets narrowed.
 */
@Composable
fun FindingDetailScreen(
    finding: Finding,
    selectedItems: Set<String>,
    onToggleItem: (String, Boolean) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onAppAction: (String) -> Unit = {},
    aiReady: Boolean = false,
    explaining: Boolean = false,
    onExplain: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalStationType.current
    val colors = MaterialTheme.colorScheme
    // Apps cannot be moved to a trash folder: Android uninstalls them through its own
    // dialog. A tick here would promise an action the approval bar cannot carry out.
    val appFinding = finding.kind == FindingKind.UNUSED_APP || finding.kind == FindingKind.APP_CACHE
    val chosen = finding.items.filter { it.path in selectedItems }
    val chosenBytes = Bytes(chosen.sumOf { it.size.value })
    val allSelected = chosen.size == finding.items.size

    val riskColor = when (finding.risk) {
        Risk.SAFE -> Olive
        Risk.REVIEW -> Amber
        Risk.PERSONAL -> MaterialTheme.colorScheme.primary
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = Module.x4),
    ) {
        item {
            Column(
                Modifier.padding(start = Module.x3, end = Module.x3, top = Module.x2),
            ) {
                BackButton(onBack)
                Text(
                    text = finding.title,
                    style = type.section,
                    color = colors.onSurface,
                    modifier = Modifier
                        .padding(top = Module.u)
                        .semantics { heading() },
                )
                Text(
                    text = finding.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = Module.half),
                )
                Row(
                    Modifier.padding(top = Module.x2, bottom = Module.x2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(riskColor))
                    Text(
                        text = when (finding.risk) {
                            Risk.SAFE -> "SAFE TO REMOVE"
                            Risk.REVIEW -> "YOUR CALL"
                            Risk.PERSONAL -> "PERSONAL — BACK UP FIRST"
                        },
                        style = type.stencil,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = Module.u),
                    )
                }
            }
            HorizontalDivider(color = colors.outline)
        }

        if (aiReady || finding.aiNote != null) {
            item {
                Column(Modifier.padding(horizontal = Module.x3, vertical = Module.x2)) {
                    if (finding.aiNote != null) {
                        Text(
                            text = finding.aiNote,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurface,
                        )
                        Text(
                            text = "GEMMA, ON THIS PHONE - ADVISORY ONLY",
                            style = type.stencil,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = Module.u),
                        )
                    } else {
                        TextButton(
                            onClick = onExplain,
                            enabled = !explaining,
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(
                                text = if (explaining) "ASKING GEMMA" else "ASK GEMMA WHAT THESE ARE",
                                style = type.stencil,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                HorizontalDivider(color = colors.outline)
            }
        }

        if (appFinding) {
            item {
                Text(
                    text = "These are apps, not files. Android uninstalls them through its own dialog, one at a time, so each row below acts on its own.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Module.x3, vertical = Module.x2),
                )
                HorizontalDivider(color = colors.outline)
            }
        }

        // The running total, the thing the user is actually deciding about.
        if (!appFinding) item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Module.x3, vertical = Module.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("SELECTED", style = type.stencil, color = colors.onSurfaceVariant)
                    Text(
                        text = "${Format.size(chosenBytes)} · ${chosen.size} of ${finding.items.size}",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (chosen.isEmpty()) colors.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    )
                }
                TextButton(onClick = { onSelectAll(!allSelected) }) {
                    Text(
                        text = if (allSelected) "CLEAR ALL" else "SELECT ALL",
                        style = type.stencil,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            HorizontalDivider(color = colors.outline)
        }

        items(finding.items, key = { it.path }) { item ->
            ItemRow(
                item = item,
                selected = item.path in selectedItems,
                onToggle = if (appFinding) null else ({ picked: Boolean -> onToggleItem(item.path, picked) }),
                action = if (finding.kind == FindingKind.UNUSED_APP) {
                    "UNINSTALL" to { onAppAction(item.packageName ?: item.path) }
                } else {
                    null
                },
            )
        }
    }
}

/** Read-only: what a storage category is actually made of, biggest first. */
@Composable
fun CategoryDetailScreen(
    title: String,
    total: Bytes,
    items: List<Finding.Item>,
    onBack: () -> Unit,
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
                Modifier.padding(start = Module.x3, end = Module.x3, top = Module.x2, bottom = Module.x2),
            ) {
                BackButton(onBack)
                Text(
                    text = title,
                    style = type.section,
                    color = colors.onSurface,
                    modifier = Modifier
                        .padding(top = Module.u)
                        .semantics { heading() },
                )
                Text(
                    text = if (items.isEmpty()) {
                        "Nothing here that Tidyra can read. App code and app data are measured by Android, not listed as files."
                    } else {
                        "${Format.size(total)} in total. The ${items.size} largest are listed; nothing here is selected or proposed."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = Module.half),
                )
            }
            HorizontalDivider(color = colors.outline)
        }

        items(items, key = { it.path }) { item ->
            ItemRow(item = item, selected = false, onToggle = null)
        }
    }
}

@Composable
private fun ItemRow(
    item: Finding.Item,
    selected: Boolean,
    onToggle: ((Boolean) -> Unit)?,
    action: Pair<String, () -> Unit>? = null,
) {
    val type = LocalStationType.current
    val colors = MaterialTheme.colorScheme
    val name = item.label ?: item.path.substringAfterLast('/')
    val folder = if (item.label != null) item.path
    else item.path.substringBeforeLast('/').removePrefix("/storage/emulated/0/")

    Column(
        Modifier
            .then(if (onToggle != null) Modifier.clickable { onToggle(!selected) } else Modifier),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Module.x3, vertical = Module.u + 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onToggle != null) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else colors.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        CheckMark(colors.onPrimary, 12.dp)
                    }
                }
            }
            item.packageName?.let {
                Box(Modifier.padding(start = if (onToggle != null) Module.x2 else 0.dp)) {
                    AppIcon(it, size = 36.dp)
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = Module.x2),
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
                    text = folder,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.keptCounterpart?.let { kept ->
                    Text(
                        text = "identical to ${kept.substringAfterLast('/')}, which is kept",
                        style = MaterialTheme.typography.bodySmall,
                        color = Olive,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Format.size(item.size),
                    style = type.amount,
                    color = colors.onSurface,
                )
                if (action != null) {
                    TextButton(onClick = action.second, contentPadding = PaddingValues(0.dp)) {
                        Text(action.first, style = type.stencil, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Text(
                        text = dateOf(item.lastModifiedMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
    }
}

private val formatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

private fun dateOf(millis: Long): String =
    if (millis <= 0) "never opened" else formatter.format(Date(millis))

/**
 * The way back. The arrow is drawn, not typed: a Unicode arrow set in the label's font
 * arrives at the font's weight, not the mark set's, and reads as a character beside caps.
 */
@Composable
private fun BackButton(onBack: () -> Unit) {
    val type = LocalStationType.current
    val color = MaterialTheme.colorScheme.primary
    TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChevronLeftMark(color, 14.dp, Modifier.padding(end = Module.half))
            Text("BACK", style = type.stencil, color = color)
        }
    }
}
