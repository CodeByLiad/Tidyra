package digital.jinatra.tidyra.ui.station

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import digital.jinatra.tidyra.core.model.Bytes
import digital.jinatra.tidyra.core.model.Finding
import digital.jinatra.tidyra.core.model.Risk
import digital.jinatra.tidyra.core.model.StorageCategory
import digital.jinatra.tidyra.core.shizuku.ShizukuBridge
import digital.jinatra.tidyra.ui.format.Format
import digital.jinatra.tidyra.ui.format.SizeScale
import digital.jinatra.tidyra.ui.theme.Amber
import digital.jinatra.tidyra.ui.theme.Crimson
import digital.jinatra.tidyra.ui.theme.LocalStationType
import digital.jinatra.tidyra.ui.theme.Module
import digital.jinatra.tidyra.ui.theme.Olive

/**
 * The storage page.
 *
 * Set like a printed page: a masthead, one figure the page is about, then sections divided
 * by hairline rules rather than enclosed in cards. Every bar on this screen — and in the
 * rest of the app — is drawn against the device's whole capacity, so a megabyte is the
 * same length wherever it appears.
 */
@Composable
fun StationScreen(
    state: StationState,
    onScan: () -> Unit,
    onToggleFinding: (String, Boolean) -> Unit,
    onApprove: () -> Unit,
    onGrantAllFiles: () -> Unit,
    onGrantUsage: () -> Unit,
    onOpenFinding: (String) -> Unit,
    onOpenCategory: (StorageCategory) -> Unit = {},
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val type = LocalStationType.current
    val scale = SizeScale(state.snapshot?.totalBytes?.value ?: 0L)
    val plan = state.plan
    val colors = MaterialTheme.colorScheme

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + Module.x4),
        ) {
            item { Hero(state) }

            // Below the figure, not above it: the headline number is what the page is
            // about, and progress is a note on it rather than something that displaces it.
            if (state.scanning) item { ScanProgress(state) }

            if (!state.hasAllFilesAccess || !state.hasUsageAccess ||
                state.shizuku != ShizukuBridge.State.READY
            ) {
                item { Rule() }
                if (!state.hasAllFilesAccess) {
                    item {
                        Notice(
                            "Tidyra cannot see your files yet",
                            "All-files access lets it read folder sizes, prove duplicates are identical, and file loose files. It is the one permission the app genuinely needs.",
                            "Grant access", onGrantAllFiles, Crimson,
                        )
                    }
                }
                if (!state.hasUsageAccess) {
                    item {
                        Notice(
                            "App sizes are hidden",
                            "Usage access reveals how much each app holds and when you last opened it. Without it, Tidyra shows nothing here rather than guessing.",
                            "Grant access", onGrantUsage, Amber,
                        )
                    }
                }
                if (state.shizuku != ShizukuBridge.State.READY && state.hasAllFilesAccess) {
                    item {
                        Notice(
                            "Cache clearing needs Shizuku",
                            "Android does not let an ordinary app clear another app's cache. With Shizuku paired, Tidyra does it in one tap; without it, it sends you to each app's storage screen.",
                            null, {}, colors.outline,
                        )
                    }
                }
            }

            if (state.containers.isNotEmpty()) {
                item { Rule() }
                item { StatRow(state) }
                item { Rule() }
                item { SectionHeading("Storage by category") }
                itemsIndexed(state.containers.filter { it.bytes.value > 0 }) { index, container ->
                    RankedBar(
                        onClick = { onOpenCategory(container.category) },
                        rank = index + 1,
                        label = container.category.label,
                        bytes = container.bytes,
                        proposed = container.proposed,
                        share = scale.fraction(container.bytes),
                        drawn = scale.drawnFraction(container.bytes),
                        largest = state.containers.maxOf { scale.drawnFraction(it.bytes) },
                    )
                }
                item {
                    Footnote("Bars are drawn against the whole ${Format.size(state.snapshot?.totalBytes ?: Bytes.ZERO)}, so sizes compare honestly across every screen.")
                }
            }

            if (state.findings.isNotEmpty()) {
                item { Rule() }
                item { SectionHeading("Suggested cleanups") }
                items(state.findings, key = { it.id }) { finding ->
                    FindingRow(
                        finding = finding,
                        selection = state.selectionOf(finding),
                        pickedCount = state.selectedCount(finding),
                        pickedBytes = state.selectedBytes(finding),
                        onToggle = { onToggleFinding(finding.id, it) },
                        onOpen = { onOpenFinding(finding.id) },
                    )
                }
            } else if (!state.scanning && state.containers.isNotEmpty()) {
                item { Footnote("Nothing worth proposing. The phone is already tidy.") }
            }

            state.lastResult?.let { result ->
                item { Rule() }
                item {
                    Notice(
                        "${Format.size(result.freed)} moved to trash",
                        "${Format.count(result.itemsRemoved, "item")} are recoverable until the trash is emptied — the space returns when it is." +
                            if (result.itemsFailed.isEmpty()) "" else " ${result.itemsFailed.size} could not be moved.",
                        null, {}, Olive,
                    )
                }
            }

            if (state.trashBytes.value > 0) {
                item { Footnote("Trash holds ${Format.size(state.trashBytes)}. That space is not free until it is emptied.") }
            }
        }
    }
}

// -- Page furniture --------------------------------------------------------------------

@Composable
private fun Masthead(state: StationState, onScan: () -> Unit) {
    val type = LocalStationType.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = Module.x3, end = Module.x3, top = Module.x2, bottom = Module.u),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tidyra",
                style = type.wordmark,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            TextButton(onClick = onScan, enabled = !state.scanning) {
                Text(
                    text = if (state.snapshot?.byCategory.isNullOrEmpty()) "SCAN" else "RESCAN",
                    style = type.stencil,
                    color = Crimson,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun Hero(state: StationState) {
    val type = LocalStationType.current
    val free = state.snapshot?.freeBytes ?: Bytes.ZERO
    val total = state.snapshot?.totalBytes ?: Bytes.ZERO

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Module.x3, vertical = Module.x3)
            .clearAndSetSemantics {
                contentDescription = "${Format.spokenSize(free)} free of ${Format.spokenSize(total)}"
                heading()
            },
    ) {
        Text(Format.size(free), style = type.figure, color = Crimson)
        Text(
            text = "free of ${Format.size(total)}",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Module.half),
        )
    }
}

/** Three figures across, divided by hairlines. The reference's Movies / Series / Anime row. */
@Composable
private fun StatRow(state: StationState) {
    val type = LocalStationType.current
    val apps = state.containers.firstOrNull { it.category == StorageCategory.APPS }?.bytes ?: Bytes.ZERO
    val data = state.containers.firstOrNull { it.category == StorageCategory.APP_DATA }?.bytes ?: Bytes.ZERO
    val media = state.containers
        .filter { it.category in listOf(StorageCategory.PHOTOS, StorageCategory.VIDEO, StorageCategory.AUDIO) }
        .sumOf { it.bytes.value }

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = Module.x2),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Stat("Apps", Format.size(apps), Modifier.weight(1f))
        VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outline)
        Stat("App data", Format.size(data), Modifier.weight(1f))
        VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outline)
        Stat("Media", Format.size(Bytes(media)), Modifier.weight(1f))
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    val type = LocalStationType.current
    Column(modifier.padding(horizontal = Module.x2)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = type.containerLabel,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * A ranked bar. The hatched tail marks what the current selection would remove, so a
 * proposal is always measured against the whole device before it is approved.
 */
@Composable
private fun RankedBar(
    onClick: () -> Unit,
    rank: Int,
    label: String,
    bytes: Bytes,
    proposed: Bytes,
    share: Float,
    drawn: Float,
    largest: Float,
) {
    val type = LocalStationType.current
    val colors = MaterialTheme.colorScheme
    // Bar lengths stay proportional to the device, then scale as a group so the largest
    // category fills the track. The ratio between any two bars is still the true ratio.
    val relative = if (largest > 0f) (drawn / largest) else 0f
    val width by animateFloatAsState(relative, label = "bar")
    val proposedWidth = if (bytes.value > 0) (proposed.value.toFloat() / bytes.value) * width else 0f

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Module.x3, vertical = Module.u + 2.dp)
            .clearAndSetSemantics {
                contentDescription = "$rank. $label, ${Format.spokenSize(bytes)}, ${(share * 100).toInt()} percent"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(112.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = Module.u)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surfaceContainerHighest),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(width)
                    .fillMaxHeight()
                    .background(Crimson),
            )
            if (proposedWidth > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(proposedWidth)
                        .fillMaxHeight()
                        .background(colors.onSurface.copy(alpha = 0.55f)),
                )
            }
        }
        Text(
            text = "${Format.size(bytes)}  (${Format.percent(share)})",
            style = type.amount,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun FindingRow(
    finding: Finding,
    selection: StationState.Selection,
    pickedCount: Int,
    pickedBytes: Bytes,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    val selected = selection != StationState.Selection.NONE
    val type = LocalStationType.current
    val colors = MaterialTheme.colorScheme
    val riskColor = when (finding.risk) {
        Risk.SAFE -> Olive
        Risk.REVIEW -> Amber
        Risk.PERSONAL -> Crimson
    }
    val riskWord = when (finding.risk) {
        Risk.SAFE -> "SAFE"
        Risk.REVIEW -> "YOUR CALL"
        Risk.PERSONAL -> "PERSONAL — BACK UP FIRST"
    }

    Column(Modifier.clickable(onClick = onOpen)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Module.x3, vertical = Module.x2),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.clickable { onToggle(selection != StationState.Selection.ALL) }) {
                SelectionDot(selection)
            }
            Column(Modifier.weight(1f).padding(start = Module.x2)) {
                Text(finding.title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Text(
                    text = finding.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = Module.half),
                )
                Row(
                    Modifier.padding(top = Module.u),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(riskColor))
                    Text(
                        text = riskWord,
                        style = type.stencil,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = Module.u),
                    )
                    if (finding.overlaps.isNotEmpty()) {
                        Text(
                            text = "· SHARED ITEMS COUNTED ONCE",
                            style = type.stencil,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = Module.u),
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (finding.frees.value > 0) Format.size(finding.frees) else "tidies",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (selected) Crimson else colors.onSurface,
                )
                Text(
                    text = if (pickedCount > 0 && pickedCount < finding.items.size) {
                        "${'$'}pickedCount of ${'$'}{finding.items.size} picked · ${'$'}{Format.size(pickedBytes)}"
                    } else {
                        Format.count(finding.items.size, "item")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
    }
}

@Composable
private fun SelectionDot(selection: StationState.Selection) {
    val colors = MaterialTheme.colorScheme
    val selected = selection != StationState.Selection.NONE
    Box(
        Modifier
            .padding(top = 6.dp)
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) Crimson else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (!selected) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHighest),
            )
        } else {
            Text(
                text = if (selection == StationState.Selection.ALL) "✓" else "–",
                color = colors.onPrimary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    val type = LocalStationType.current
    Text(
        text = text,
        style = type.section,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .padding(start = Module.x3, end = Module.x3, top = Module.x3, bottom = Module.u)
            .semantics { heading() },
    )
}

@Composable
private fun Rule() = HorizontalDivider(
    modifier = Modifier.padding(horizontal = Module.x3),
    color = MaterialTheme.colorScheme.outline,
)

@Composable
private fun Footnote(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = Module.x3, vertical = Module.u),
)

@Composable
private fun ScanProgress(state: StationState) {
    Column(Modifier.padding(horizontal = Module.x3, vertical = Module.u)) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = Crimson,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Text(
            text = "${state.scanLabel.orEmpty()} · ${Format.count(state.filesSeen, "file")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Module.half),
        )
    }
}

@Composable
private fun Notice(
    title: String,
    body: String,
    action: String?,
    onAction: () -> Unit,
    accent: Color,
) {
    val type = LocalStationType.current
    Row(Modifier.padding(horizontal = Module.x3, vertical = Module.x2)) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Column(Modifier.padding(start = Module.x2)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Module.half),
            )
            if (action != null) {
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.padding(top = Module.half),
                ) {
                    Text(action.uppercase(), style = type.stencil, color = Crimson)
                }
            }
        }
    }
}

@Composable
internal fun ApprovalBar(frees: Bytes, items: Int, working: Boolean, onApprove: () -> Unit) {
    val type = LocalStationType.current
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLowest)
            .navigationBarsPadding()
            .padding(horizontal = Module.x3, vertical = Module.x2),
    ) {
        HorizontalDivider(color = colors.outline)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Module.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("APPROVED", style = type.stencil, color = colors.onSurfaceVariant)
                Text(
                    text = "${Format.size(frees)} · ${Format.count(items, "item")}",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
            }
            Button(
                onClick = onApprove,
                enabled = !working,
                shape = RoundedCornerShape(Module.half),
                colors = ButtonDefaults.buttonColors(containerColor = Crimson, contentColor = colors.onPrimary),
            ) {
                Text(if (working) "WORKING" else "MOVE TO TRASH", style = type.stencil)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    items: List<StationState.Container>,
    itemContent: @Composable (Int, StationState.Container) -> Unit,
) = items(items.size) { index -> itemContent(index, items[index]) }

@Composable
private fun Spacer8() = Spacer(Modifier.height(Module.u))
