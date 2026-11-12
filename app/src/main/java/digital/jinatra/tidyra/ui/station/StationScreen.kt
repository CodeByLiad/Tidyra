package digital.jinatra.tidyra.ui.station

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import digital.jinatra.tidyra.core.model.Bytes
import digital.jinatra.tidyra.core.model.StorageCategory
import digital.jinatra.tidyra.core.shizuku.ShizukuBridge
import digital.jinatra.tidyra.ui.format.Format
import digital.jinatra.tidyra.ui.format.SizeScale
import digital.jinatra.tidyra.ui.theme.GlassGreen
import digital.jinatra.tidyra.ui.theme.HazardRed
import digital.jinatra.tidyra.ui.theme.HiVis
import digital.jinatra.tidyra.ui.theme.Ink
import digital.jinatra.tidyra.ui.theme.LocalStationType
import digital.jinatra.tidyra.ui.theme.MetalGrey
import digital.jinatra.tidyra.ui.theme.Module
import digital.jinatra.tidyra.ui.theme.OrganicBrown
import digital.jinatra.tidyra.ui.theme.PaperBlue
import digital.jinatra.tidyra.ui.theme.SignalAmber

@Composable
fun StationScreen(
    state: StationState,
    onScan: () -> Unit,
    onToggleFinding: (String, Boolean) -> Unit,
    onApprove: () -> Unit,
    onGrantAllFiles: () -> Unit,
    onGrantUsage: () -> Unit,
    onOpenFinding: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalStationType.current
    val scale = SizeScale(state.snapshot?.totalBytes?.value ?: 0L)
    val plan = state.plan

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (plan.itemCount == 0) {
                ExtendedFloatingActionButton(
                    onClick = onScan,
                    containerColor = HiVis,
                    contentColor = Ink,
                ) {
                    Text(
                        text = if (state.snapshot == null) "SCAN" else "SCAN AGAIN",
                        style = type.containerLabel,
                    )
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = plan.itemCount > 0,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                ApprovalBar(
                    frees = plan.frees,
                    items = plan.itemCount,
                    working = state.working,
                    onApprove = onApprove,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Module.u),
        ) {
            item { StationHeader(state) }

            if (state.scanning) {
                item { ScanProgress(state) }
            }

            if (!state.hasAllFilesAccess) {
                item {
                    Notice(
                        title = "Tidyra cannot see your files yet",
                        body = "All-files access lets it read folder sizes, prove duplicates are identical, and file loose files. It is the one permission the app genuinely needs.",
                        action = "Grant access",
                        onAction = onGrantAllFiles,
                        accent = HazardRed,
                    )
                }
            }
            if (!state.hasUsageAccess) {
                item {
                    Notice(
                        title = "App sizes are hidden",
                        body = "Usage access is what reveals how much each app holds and when you last opened it. Without it, Tidyra shows nothing here rather than guessing.",
                        action = "Grant access",
                        onAction = onGrantUsage,
                        accent = SignalAmber,
                    )
                }
            }
            if (state.shizuku != ShizukuBridge.State.READY && state.hasAllFilesAccess) {
                item {
                    Notice(
                        title = "Cache clearing needs Shizuku",
                        body = "Android does not let an ordinary app clear another app's cache. With Shizuku paired, Tidyra can do it in one tap; without it, it will send you to each app's storage screen.",
                        action = null,
                        onAction = {},
                        accent = MetalGrey,
                    )
                }
            }

            if (state.containers.isNotEmpty()) {
                item { SectionLabel("Sorting station") }
                items(state.containers, key = { it.category.name }) { container ->
                    ContainerBar(
                        label = container.category.label,
                        bytes = container.bytes,
                        color = colorFor(container.category),
                        scale = scale,
                        proposedBytes = container.proposed,
                        modifier = Modifier.padding(horizontal = Module.x2),
                    )
                }
                item {
                    Text(
                        text = "Every bar is drawn against the whole ${Format.size(state.snapshot?.totalBytes ?: Bytes.ZERO)}, so sizes compare honestly across the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Module.x2),
                    )
                }
            }

            if (state.findings.isNotEmpty()) {
                item {
                    SectionLabel(
                        "Collection slips · ${Format.count(state.findings.size, "proposal")}",
                    )
                }
                items(state.findings, key = { it.id }) { finding ->
                    CollectionSlip(
                        finding = finding,
                        selected = finding.id in state.selectedFindingIds,
                        onToggle = { onToggleFinding(finding.id, it) },
                        modifier = Modifier.padding(horizontal = Module.x2),
                    )
                }
            } else if (!state.scanning && state.snapshot != null) {
                item {
                    Text(
                        text = "Nothing worth proposing. The phone is already tidy.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Module.x2),
                    )
                }
            }

            state.lastResult?.let { result ->
                item {
                    Notice(
                        title = "${Format.size(result.freed)} moved to trash",
                        body = "${Format.count(result.itemsRemoved, "item")} are recoverable until the trash is emptied. The space returns when it is." +
                            if (result.itemsFailed.isEmpty()) "" else " ${result.itemsFailed.size} could not be moved.",
                        action = null,
                        onAction = {},
                        accent = GlassGreen,
                    )
                }
            }

            if (state.trashBytes.value > 0) {
                item {
                    Text(
                        text = "Trash holds ${Format.size(state.trashBytes)}. That space is not free until it is emptied.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Module.x2),
                    )
                }
            }
        }
    }
}

@Composable
private fun StationHeader(state: StationState) {
    val type = LocalStationType.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(Ink)
            .statusBarsPadding()
            .padding(start = Module.x2, end = Module.x2, top = Module.u + 4.dp, bottom = Module.x2),
    ) {
        Text("TIDYRA", style = type.wordmark, color = Color(0xFFF4F1EA))
        Row(
            modifier = Modifier.padding(top = Module.u + 2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = Format.size(state.snapshot?.freeBytes ?: Bytes.ZERO),
                style = type.figure,
                color = HiVis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "FREE OF ${Format.size(state.snapshot?.totalBytes ?: Bytes.ZERO)}",
                style = type.stencil,
                color = Color(0xFFB9B4AA),
                modifier = Modifier.padding(start = Module.u, bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun ScanProgress(state: StationState) {
    Column(Modifier.padding(horizontal = Module.x2)) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = HiVis,
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
private fun SectionLabel(text: String) {
    val type = LocalStationType.current
    Text(
        text = text.uppercase(),
        style = type.stencil,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = Module.x2, end = Module.x2, top = Module.u)
            .semantics { heading() },
    )
}

@Composable
private fun Notice(
    title: String,
    body: String,
    action: String?,
    onAction: () -> Unit,
    accent: Color,
) {
    Column(
        Modifier
            .padding(horizontal = Module.x2)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(accent)
                .padding(top = 4.dp),
        )
        Column(Modifier.padding(Module.x2)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Module.half),
            )
            if (action != null) {
                TextButton(onClick = onAction, modifier = Modifier.padding(top = Module.half)) {
                    Text(action)
                }
            }
        }
    }
}

@Composable
private fun ApprovalBar(
    frees: Bytes,
    items: Int,
    working: Boolean,
    onApprove: () -> Unit,
) {
    val type = LocalStationType.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(Ink)
            .navigationBarsPadding()
            .padding(horizontal = Module.x2, vertical = Module.u + 2.dp),
    ) {
        HorizontalDivider(color = HiVis, thickness = 2.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Module.u + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("APPROVED", style = type.stencil, color = Color(0xFFB9B4AA))
                Text(
                    text = "${Format.size(frees)} · ${Format.count(items, "item")}",
                    style = type.amount.copy(textAlign = TextAlign.Start),
                    color = Color(0xFFF4F1EA),
                )
            }
            Button(
                onClick = onApprove,
                enabled = !working,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = HiVis,
                    contentColor = Ink,
                ),
            ) {
                Text(if (working) "WORKING" else "MOVE TO TRASH", style = type.containerLabel)
            }
        }
    }
}

private fun colorFor(category: StorageCategory): Color = when (category) {
    StorageCategory.APPS -> MetalGrey
    StorageCategory.APP_DATA -> PaperBlue
    StorageCategory.PHOTOS -> GlassGreen
    StorageCategory.VIDEO -> OrganicBrown
    StorageCategory.AUDIO -> HazardRed
    StorageCategory.DOCUMENTS -> SignalAmber
    StorageCategory.OTHER -> MetalGrey
    StorageCategory.FREE -> Color.Transparent
}
