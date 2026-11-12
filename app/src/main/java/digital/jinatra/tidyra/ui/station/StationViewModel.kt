package digital.jinatra.tidyra.ui.station

import android.app.Application
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import digital.jinatra.tidyra.core.model.ActionPlan
import digital.jinatra.tidyra.core.model.Bytes
import digital.jinatra.tidyra.core.model.CleanupResult
import digital.jinatra.tidyra.core.model.Finding
import digital.jinatra.tidyra.core.model.FindingKind
import digital.jinatra.tidyra.core.model.StorageCategory
import digital.jinatra.tidyra.core.model.StorageSnapshot
import digital.jinatra.tidyra.core.scan.AppInventory
import digital.jinatra.tidyra.core.scan.DuplicateFinder
import digital.jinatra.tidyra.core.scan.SharedStorageScanner
import digital.jinatra.tidyra.core.shizuku.ShizukuBridge
import digital.jinatra.tidyra.core.trash.TrashStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class StationState(
    val scanning: Boolean = false,
    val scanLabel: String? = null,
    val filesSeen: Int = 0,
    val snapshot: StorageSnapshot? = null,
    val containers: List<Container> = emptyList(),
    val findings: List<Finding> = emptyList(),
    /** Approval is per item, not per finding. A finding is a container, never a commitment. */
    val selectedItems: Set<String> = emptySet(),
    val apps: List<AppInventory.AppEntry> = emptyList(),
    /** The biggest files per category, so a category row can answer "what is in here?". */
    val topFilesByCategory: Map<StorageCategory, List<Finding.Item>> = emptyMap(),
    val hasAllFilesAccess: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val shizuku: ShizukuBridge.State = ShizukuBridge.State.UNAVAILABLE,
    val trashBytes: Bytes = Bytes.ZERO,
    val lastResult: CleanupResult? = null,
    val working: Boolean = false,
) {
    val plan: ActionPlan
        get() {
            val approved = findings.filter { finding -> finding.items.any { it.path in selectedItems } }
            return ActionPlan(approved, selectedItems)
        }

    enum class Selection { NONE, SOME, ALL }

    fun selectionOf(finding: Finding): Selection {
        val picked = finding.items.count { it.path in selectedItems }
        return when (picked) {
            0 -> Selection.NONE
            finding.items.size -> Selection.ALL
            else -> Selection.SOME
        }
    }

    fun selectedCount(finding: Finding): Int = finding.items.count { it.path in selectedItems }

    fun selectedBytes(finding: Finding): Bytes =
        Bytes(finding.items.filter { it.path in selectedItems }.sumOf { it.size.value })

    data class Container(
        val category: StorageCategory,
        val bytes: Bytes,
        val proposed: Bytes,
    )
}

class StationViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = SharedStorageScanner()
    private val duplicates = DuplicateFinder()
    private val inventory = AppInventory(app)
    private val trash = TrashStore(app)
    val shizuku = ShizukuBridge()

    private val _state = MutableStateFlow(StationState())
    val state: StateFlow<StationState> = _state.asStateFlow()

    private var scanJob: Job? = null

    init {
        refreshPermissions()
        // Capacity and free space come from StatFs, which needs no permission at all.
        // Showing "0 B free of 0 B" until a scan runs would be a lie the app can avoid.
        _state.update { it.copy(snapshot = capacityOnly()) }
        viewModelScope.launch { _state.update { it.copy(trashBytes = trash.size()) } }
    }

    private fun capacityOnly(): StorageSnapshot {
        val stat = android.os.StatFs(Environment.getExternalStorageDirectory().absolutePath)
        return StorageSnapshot(
            totalBytes = Bytes(stat.blockCountLong * stat.blockSizeLong),
            freeBytes = Bytes(stat.availableBlocksLong * stat.blockSizeLong),
            byCategory = emptyMap(),
            takenAtMillis = System.currentTimeMillis(),
        )
    }

    fun refreshPermissions() {
        _state.update {
            it.copy(
                hasAllFilesAccess = hasAllFilesAccess(),
                hasUsageAccess = inventory.hasUsageAccess(),
                shizuku = shizuku.state(),
            )
        }
    }

    private fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

    fun scan() {
        if (_state.value.scanning) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.update { it.copy(scanning = true, scanLabel = "Reading shared storage", lastResult = null) }

            var scanResult: SharedStorageScanner.ScanResult? = null
            scanner.scan().collect { progress ->
                when (progress) {
                    is SharedStorageScanner.Progress.Walking ->
                        _state.update {
                            it.copy(filesSeen = progress.filesSeen, scanLabel = "Reading shared storage")
                        }

                    is SharedStorageScanner.Progress.Done -> scanResult = progress.result
                }
            }
            val result = scanResult ?: return@launch

            _state.update { it.copy(scanLabel = "Checking for identical files") }
            val duplicateSets = duplicates.find(result.files)

            _state.update { it.copy(scanLabel = "Measuring apps") }
            val apps = inventory.load()

            val findings = buildList {
                duplicates.toFinding(duplicateSets)?.let(::add)
                addAll(result.findings)
                inventory.cacheFinding(apps)?.let(::add)
                inventory.unusedAppsFinding(apps)?.let(::add)
            }.let(::markOverlaps).sortedByDescending { it.frees.value }

            _state.update {
                it.copy(
                    scanning = false,
                    scanLabel = null,
                    snapshot = result.snapshot,
                    findings = findings,
                    apps = apps,
                    topFilesByCategory = result.files
                        .groupBy { entry -> entry.category }
                        .mapValues { (_, entries) ->
                            entries.sortedByDescending { entry -> entry.size }
                                .take(100)
                                .map { entry ->
                                    Finding.Item(
                                        path = entry.file.absolutePath,
                                        size = Bytes(entry.size),
                                        lastModifiedMillis = entry.lastModified,
                                    )
                                }
                        },
                    containers = buildContainers(result.snapshot, apps, findings, it.selectedItems),
                    trashBytes = trash.size(),
                )
            }
        }
    }

    /** Selecting a finding selects every item in it; the user can then deselect individually. */
    fun toggleFinding(id: String, selected: Boolean) {
        _state.update { current ->
            val finding = current.findings.firstOrNull { it.id == id } ?: return@update current
            val paths = finding.items.map { it.path }
            val items = current.selectedItems.toMutableSet().apply {
                if (selected) addAll(paths) else removeAll(paths.toSet())
            }
            current.copy(
                selectedItems = items,
                containers = buildContainers(current.snapshot, current.apps, current.findings, items),
            )
        }
    }

    fun toggleItem(path: String, selected: Boolean) {
        _state.update { current ->
            val items = current.selectedItems.toMutableSet().apply {
                if (selected) add(path) else remove(path)
            }
            current.copy(
                selectedItems = items,
                containers = buildContainers(current.snapshot, current.apps, current.findings, items),
            )
        }
    }

    /** Moves everything approved into the trash. Reversible until the trash is purged. */
    fun applyPlan() {
        val plan = _state.value.plan
        if (plan.itemCount == 0 || _state.value.working) return

        viewModelScope.launch {
            _state.update { it.copy(working = true) }

            // App-level findings act through their own flows (system uninstall dialog,
            // Shizuku cache clear), never by deleting files behind an app's back.
            val filePaths = plan.approved
                .filter { it.kind != FindingKind.UNUSED_APP && it.kind != FindingKind.APP_CACHE }
                .flatMap { finding -> finding.items.map { it.path } }
                .filter { it in plan.itemPaths }
                .distinct()

            val result = trash.trash(filePaths)
            _state.update {
                it.copy(
                    working = false,
                    lastResult = result,
                    selectedItems = emptySet(),
                    trashBytes = trash.size(),
                )
            }
            scan()
        }
    }

    fun clearCachesWithShizuku(onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(working = true) }
            val result = shizuku.trimAllCaches()
            _state.update { it.copy(working = false) }
            onDone(result.ok, if (result.ok) "Caches cleared" else result.stderr.ifBlank { "Shizuku refused the command" })
            if (result.ok) scan()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            _state.update { it.copy(working = true) }
            trash.purgeAll()
            _state.update { it.copy(working = false, trashBytes = trash.size()) }
        }
    }

    /**
     * Marks findings that claim the same items. The plan already counts a shared item once;
     * this is what lets the UI say so instead of silently disagreeing with its own arithmetic.
     */
    private fun markOverlaps(findings: List<Finding>): List<Finding> {
        val owners = HashMap<String, MutableSet<String>>()
        findings.forEach { finding ->
            finding.items.forEach { item ->
                owners.getOrPut(item.path) { mutableSetOf() }.add(finding.id)
            }
        }
        return findings.map { finding ->
            val overlapping = finding.items
                .flatMap { owners[it.path].orEmpty() }
                .filterTo(mutableSetOf()) { it != finding.id }
            finding.copy(overlaps = overlapping)
        }
    }

    private fun buildContainers(
        snapshot: StorageSnapshot?,
        apps: List<AppInventory.AppEntry>,
        findings: List<Finding>,
        selectedItems: Set<String>,
    ): List<StationState.Container> {
        if (snapshot == null) return emptyList()

        val appBytes = Bytes(apps.sumOf { it.appBytes.value })
        val appDataBytes = Bytes(apps.sumOf { it.dataBytes.value + it.cacheBytes.value })
        val proposed = proposedByCategory(findings, selectedItems)

        val order = listOf(
            StorageCategory.APPS to appBytes,
            StorageCategory.APP_DATA to appDataBytes,
            StorageCategory.PHOTOS to (snapshot.byCategory[StorageCategory.PHOTOS] ?: Bytes.ZERO),
            StorageCategory.VIDEO to (snapshot.byCategory[StorageCategory.VIDEO] ?: Bytes.ZERO),
            StorageCategory.AUDIO to (snapshot.byCategory[StorageCategory.AUDIO] ?: Bytes.ZERO),
            StorageCategory.DOCUMENTS to (snapshot.byCategory[StorageCategory.DOCUMENTS] ?: Bytes.ZERO),
            StorageCategory.OTHER to (snapshot.byCategory[StorageCategory.OTHER] ?: Bytes.ZERO),
        )

        return order.map { (category, bytes) ->
            StationState.Container(category, bytes, proposed[category] ?: Bytes.ZERO)
        }
    }

    private fun proposedByCategory(
        findings: List<Finding>,
        selectedItems: Set<String>,
    ): Map<StorageCategory, Bytes> {
        val totals = HashMap<StorageCategory, Long>()
        val counted = HashSet<String>()

        findings.forEach { finding ->
            finding.items.forEach { item ->
                if (item.path !in selectedItems) return@forEach
                if (!counted.add(item.path)) return@forEach
                val category = when (finding.kind) {
                    FindingKind.UNUSED_APP -> StorageCategory.APPS
                    FindingKind.APP_CACHE -> StorageCategory.APP_DATA
                    else -> categoryOfPath(item.path)
                }
                totals[category] = (totals[category] ?: 0L) + item.size.value
            }
        }
        return totals.mapValues { Bytes(it.value) }
    }

    private fun categoryOfPath(path: String): StorageCategory =
        when (File(path).extension.lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng" -> StorageCategory.PHOTOS
            "mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v" -> StorageCategory.VIDEO
            "mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "amr" -> StorageCategory.AUDIO
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "epub" -> StorageCategory.DOCUMENTS
            "apk", "apks", "xapk", "apkm", "obb" -> StorageCategory.APPS
            else -> StorageCategory.OTHER
        }
}
