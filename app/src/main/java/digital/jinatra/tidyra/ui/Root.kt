package digital.jinatra.tidyra.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import digital.jinatra.tidyra.core.model.StorageCategory
import digital.jinatra.tidyra.ui.apps.AppsScreen
import digital.jinatra.tidyra.ui.detail.CategoryDetailScreen
import digital.jinatra.tidyra.ui.detail.FindingDetailScreen
import digital.jinatra.tidyra.ui.model.ModelScreen
import digital.jinatra.tidyra.ui.model.ModelState
import digital.jinatra.tidyra.ui.organize.OrganizeScreen
import digital.jinatra.tidyra.ui.organize.OrganizeState
import digital.jinatra.tidyra.ui.station.ApprovalBar
import digital.jinatra.tidyra.ui.station.StationScreen
import digital.jinatra.tidyra.ui.station.StationState
import digital.jinatra.tidyra.ui.trash.TrashScreen
import digital.jinatra.tidyra.ui.theme.Crimson
import digital.jinatra.tidyra.ui.theme.LocalStationType
import digital.jinatra.tidyra.ui.theme.Module

enum class Section(val label: String) {
    STORAGE("Storage"),
    APPS("Apps"),
    ORGANISE("Organise"),
    TRASH("Trash"),
    AI("AI"),
}

/**
 * The masthead, the tabs under it, and whatever section is open.
 *
 * Text tabs rather than an icon bar: this is a reading surface before it is a control
 * panel, and four words carry the sections better than four pictograms would.
 */
@Composable
fun Root(
    station: StationState,
    organize: OrganizeState,
    model: ModelState,
    darkTheme: Boolean,
    callbacks: RootCallbacks,
) {
    var section by remember { mutableStateOf(Section.STORAGE) }
    var openFinding by remember { mutableStateOf<String?>(null) }
    // Hoisted so returning from a detail screen lands where the user left, not at the top.
    val storageScroll = rememberLazyListState()
    var openCategory by remember { mutableStateOf<StorageCategory?>(null) }
    val colors = MaterialTheme.colorScheme

    val finding = station.findings.firstOrNull { it.id == openFinding }
    val detailOpen = finding != null || openCategory != null

    BackHandler(enabled = detailOpen) {
        openFinding = null
        openCategory = null
    }

    // Surface, not a bare Column: it sets LocalContentColor, and without it every Text
    // that does not name a colour falls back to black — invisible in dark mode.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background,
        contentColor = colors.onSurface,
    ) {
    Column(Modifier.fillMaxSize()) {
        if (detailOpen) {
            // A detail screen replaces the masthead, so nothing above it is holding the
            // status bar clear. The system bars are the system's: the app paints its
            // background under them and puts nothing else there.
            Column(
                Modifier
                    .weight(1f)
                    .statusBarsPadding(),
            ) {
            if (finding != null) {
                FindingDetailScreen(
                    finding = finding,
                    selectedItems = station.selectedItems,
                    onToggleItem = callbacks.onToggleItem,
                    onSelectAll = { all -> callbacks.onToggleFinding(finding.id, all) },
                    onAppAction = callbacks.onUninstall,
                    // Read live: the model may have been loaded after this activity resumed,
                    // and a stale flag would hide the button that uses it.
                    aiReady = digital.jinatra.tidyra.ai.EngineHolder.isReady,
                    explaining = station.explaining == finding.id,
                    onExplain = { callbacks.onExplain(finding.id) },
                    onBack = { openFinding = null },
                    modifier = Modifier.weight(1f),
                )
            } else {
                val category = openCategory!!
                CategoryDetailScreen(
                    title = category.label,
                    total = station.containers.firstOrNull { it.category == category }?.bytes
                        ?: digital.jinatra.tidyra.core.model.Bytes.ZERO,
                    // Apps and app data are not files on disk Tidyra can walk — Android
                    // reports them per package. So the detail for those is the app list.
                    items = when (category) {
                        StorageCategory.APPS -> station.apps.asItems { it.appBytes }
                        StorageCategory.APP_DATA -> station.apps.asItems { app ->
                            digital.jinatra.tidyra.core.model.Bytes(app.dataBytes.value + app.cacheBytes.value)
                        }
                        else -> station.topFilesByCategory[category].orEmpty()
                    },
                    onBack = { openCategory = null },
                    modifier = Modifier.weight(1f),
                )
            }
            }
            ApprovalFooter(station, callbacks)
            return@Column
        }

        Masthead(
            scanning = station.scanning,
            scanned = !station.snapshot?.byCategory.isNullOrEmpty(),
            darkTheme = darkTheme,
            onScan = callbacks.onScan,
            onToggleTheme = callbacks.onToggleTheme,
        )
        SectionTabs(section) { section = it }

        // Lists end above the gesture bar rather than running under it.
        Box(
            Modifier
                .weight(1f)
                .navigationBarsPadding(),
        ) {
            when (section) {
                Section.STORAGE -> StationScreen(
                    state = station,
                    onScan = callbacks.onScan,
                    onToggleFinding = callbacks.onToggleFinding,
                    onApprove = callbacks.onApprove,
                    onGrantAllFiles = callbacks.onGrantAllFiles,
                    onGrantUsage = callbacks.onGrantUsage,
                    onRequestShizuku = callbacks.onRequestShizuku,
                    onOpenFinding = { openFinding = it },
                    onOpenCategory = { openCategory = it },
                    listState = storageScroll,
                )

                Section.APPS -> AppsScreen(
                    apps = station.apps,
                    hasUsageAccess = station.hasUsageAccess,
                    shizuku = station.shizuku,
                    onUninstall = callbacks.onUninstall,
                    onOpenAppSettings = callbacks.onOpenAppSettings,
                    onClearAllCaches = callbacks.onClearAllCaches,
                    onGrantUsage = callbacks.onGrantUsage,
                )

                Section.ORGANISE -> OrganizeScreen(
                    state = organize,
                    onSelect = callbacks.onSelectFolder,
                    onApply = callbacks.onApplyOrganise,
                    onUndo = callbacks.onUndoOrganise,
                )

                Section.TRASH -> TrashScreen(
                    entries = station.trashEntries,
                    totalBytes = station.trashBytes,
                    working = station.working,
                    onRestore = callbacks.onRestore,
                    onRestoreMany = callbacks.onRestoreMany,
                    onRestoreAll = callbacks.onRestoreAll,
                    onPurgeMany = callbacks.onPurgeMany,
                    onEmpty = callbacks.onEmptyTrash,
                )

                Section.AI -> ModelScreen(
                    state = model,
                    onDownload = callbacks.onDownloadModel,
                    onCancel = callbacks.onCancelDownload,
                    onDelete = callbacks.onDeleteModel,
                    onLoad = callbacks.onLoadModel,
                    onUnload = callbacks.onUnloadModel,
                    onToken = callbacks.onToken,
                    onImport = callbacks.onImportModel,
                )
            }
        }

        ApprovalFooter(station, callbacks)
    }
    }
}

/**
 * Apps as list items. The path carries `package/label` so the row reads as the app's name
 * over its package, which is what identifies it when two apps share a name.
 */
private fun List<digital.jinatra.tidyra.core.scan.AppInventory.AppEntry>.asItems(
    size: (digital.jinatra.tidyra.core.scan.AppInventory.AppEntry) -> digital.jinatra.tidyra.core.model.Bytes,
): List<digital.jinatra.tidyra.core.model.Finding.Item> =
    filterNot { it.isSystem }
        .map { app ->
            digital.jinatra.tidyra.core.model.Finding.Item(
                path = app.packageName,
                size = size(app),
                lastModifiedMillis = app.lastUsedMillis,
                packageName = app.packageName,
                label = app.label,
            )
        }
        .sortedByDescending { it.size.value }
        .take(100)

@Composable
private fun ApprovalFooter(station: StationState, callbacks: RootCallbacks) {
    AnimatedVisibility(
        visible = station.plan.itemCount > 0,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        ApprovalBar(
            frees = station.plan.frees,
            items = station.plan.itemCount,
            working = station.working,
            onApprove = callbacks.onApprove,
        )
    }
}

@Composable
private fun Masthead(
    scanning: Boolean,
    scanned: Boolean,
    darkTheme: Boolean,
    onScan: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    val type = LocalStationType.current
    val colors = MaterialTheme.colorScheme
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
            color = colors.onSurface,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        TextButton(onClick = onToggleTheme) {
            Text(
                text = if (darkTheme) "LIGHT" else "DARK",
                style = type.stencil,
                color = colors.onSurfaceVariant,
            )
        }
        TextButton(onClick = onScan, enabled = !scanning) {
            Text(
                text = if (scanning) "SCANNING" else if (scanned) "RESCAN" else "SCAN",
                style = type.stencil,
                color = if (scanning) colors.onSurfaceVariant else Crimson,
            )
        }
    }
}

@Composable
private fun SectionTabs(current: Section, onSelect: (Section) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column {
        // Five sections do not fit a phone row at a readable size, so the strip scrolls
        // rather than squeezing the labels or wrapping one onto a second line.
        // Spread across the full width: the first tab sits on the masthead's left margin
        // and the last ends on the same margin at the right. Packing them left instead
        // leaves a band of dead space that reads as a mistake.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Module.x3),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Section.entries.forEach { entry ->
                val active = entry == current
                Column(
                    modifier = Modifier
                        // Width comes from the label, so the underline below can fill it
                        // without the column stretching into the row's spare space.
                        .width(IntrinsicSize.Min)
                        // No ripple: a grey box flashing behind a word is the stock
                        // affordance, and the colour and rule already answer the tap.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(entry) }
                        .padding(vertical = Module.u + 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The weight never changes with state: bold text is wider, so an
                    // active tab would resize and shift every other tab along the row.
                    // Colour and the underline carry the state, and both ease in.
                    val tint by animateColorAsState(
                        targetValue = if (active) Crimson else colors.onSurfaceVariant,
                        animationSpec = tween(220),
                        label = "tab-tint",
                    )
                    val ruleAlpha by animateFloatAsState(
                        targetValue = if (active) 1f else 0f,
                        animationSpec = tween(220),
                        label = "tab-rule",
                    )
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = tint,
                    )
                    // Always drawn, exactly as wide as its label, and only coloured when
                    // active. A rule that appears and disappears changes the column's
                    // width — which is what was shoving the other tabs sideways.
                    Box(
                        Modifier
                            .padding(top = Module.u)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Crimson.copy(alpha = ruleAlpha)),
                    )
                }
            }
        }
        HorizontalDivider(color = colors.outline)
    }
}

/** One bag of callbacks so the root signature stays readable as sections are added. */
data class RootCallbacks(
    val onScan: () -> Unit,
    val onToggleFinding: (String, Boolean) -> Unit,
    val onToggleItem: (String, Boolean) -> Unit,
    val onRestore: (String) -> Unit,
    val onEmptyTrash: () -> Unit,
    val onRestoreMany: (Set<String>) -> Unit,
    val onRestoreAll: () -> Unit,
    val onPurgeMany: (Set<String>) -> Unit,
    val onToggleTheme: () -> Unit,
    val onRequestShizuku: () -> Unit,
    val onExplain: (String) -> Unit,
    val onApprove: () -> Unit,
    val onGrantAllFiles: () -> Unit,
    val onGrantUsage: () -> Unit,
    val onUninstall: (String) -> Unit,
    val onOpenAppSettings: (String) -> Unit,
    val onClearAllCaches: () -> Unit,
    val onSelectFolder: (java.io.File) -> Unit,
    val onApplyOrganise: () -> Unit,
    val onUndoOrganise: () -> Unit,
    val onDownloadModel: (digital.jinatra.tidyra.ai.ModelCatalog.Model) -> Unit,
    val onCancelDownload: () -> Unit,
    val onDeleteModel: (digital.jinatra.tidyra.ai.ModelCatalog.Model) -> Unit,
    val onLoadModel: (digital.jinatra.tidyra.ai.ModelCatalog.Model) -> Unit,
    val onUnloadModel: () -> Unit,
    val onToken: (String) -> Unit,
    val onImportModel: () -> Unit,
)
