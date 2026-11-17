package digital.jinatra.tidyra.ui.station

import android.app.Application
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import digital.jinatra.tidyra.ai.EngineHolder
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
import digital.jinatra.tidyra.core.scan.StorageAccounting
import digital.jinatra.tidyra.core.shizuku.ShizukuBridge
import digital.jinatra.tidyra.core.trash.TrashStore
import digital.jinatra.tidyra.core.trash.TrashPreferences
import digital.jinatra.tidyra.ui.format.Format
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import digital.jinatra.tidyra.core.cache.CacheBatch
import digital.jinatra.tidyra.core.cache.CacheAutomation
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach

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
    val trashEntries: List<digital.jinatra.tidyra.core.trash.TrashStore.Entry> = emptyList(),
    val lastResult: CleanupResult? = null,
    val working: Boolean = false,
    val explaining: String? = null,
    val aiReady: Boolean = false,
    val retentionDays: Int = 14,
    val message: String? = null,
    val settingsPackage: String? = null,
    val canClearCache: Boolean = false,
    val selectedCachePackages: Set<String> = emptySet(),
    val cacheProgress: CacheBatch.Progress? = null,
    val cacheResults: List<CacheBatch.Item> = emptyList(),
    val cacheQueue: List<String> = emptyList(),
    val cacheQueueIndex: Int = 0,
    val cacheNote: String? = null,
    val useCacheAutomation: Boolean = false,
    val cacheAutomationAvailable: Boolean = false,
    val cacheAutomationProblem: String? = null,
    val restrictedFolders: List<digital.jinatra.tidyra.core.shizuku.RestrictedFolder> = emptyList(),
    val restrictedNote: String? = null,
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
    private val trashPreferences = TrashPreferences(app)
    val shizuku = ShizukuBridge(app)

    private val _state = MutableStateFlow(StationState(retentionDays = trashPreferences.retentionDays))
    val state: StateFlow<StationState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var cacheJob: Job? = null

    init {
        viewModelScope.launch {
            CacheAutomation.state.collect { status ->
                val problem = CacheAutomation.unavailableReason(getApplication())
                _state.update { it.copy(cacheAutomationAvailable = status.connected, cacheAutomationProblem = problem,
                    useCacheAutomation = it.useCacheAutomation && problem == null) }
            }
        }
        refreshPermissions()
        // Capacity and free space come from StatFs, which needs no permission at all.
        // Showing "0 B free of 0 B" until a scan runs would be a lie the app can avoid.
        _state.update { it.copy(snapshot = capacityOnly()) }
        refreshTrash()
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

    /**
     * Asks the on-device model to explain one finding in plain language.
     *
     * It is given facts and nothing else, and its answer is advisory: it never changes
     * what is proposed, what is selected, or what gets removed.
     */
    fun explain(findingId: String) {
        val finding = _state.value.findings.firstOrNull { it.id == findingId } ?: return
        if (!EngineHolder.isReady) return

        viewModelScope.launch {
            _state.update { it.copy(explaining = findingId) }
            val answer = EngineHolder.engine(getApplication()).complete(promptFor(finding))
            val note = answer
                .getOrElse { error -> "The model could not answer: " + (error.message ?: "unknown error") }
                .ifBlank {
                    // An empty reply is a real outcome worth naming, not a blank panel.
                    "The model returned nothing usable. Try another compatible model; the finding and your selection have not changed."
                }
            _state.update { current ->
                current.copy(
                    explaining = null,
                    findings = current.findings.map {
                        if (it.id == findingId) it.copy(aiNote = note.trim()) else it
                    },
                )
            }
        }
    }

    private fun promptFor(finding: Finding): String {
        val sample = finding.items.take(8).joinToString("\n") { item ->
            "- " + (item.label ?: item.path) + " (" + item.size.value + " bytes)"
        }
        return buildString {
            appendLine("A storage scan on this phone produced this finding.")
            appendLine("Title: " + finding.title)
            appendLine("Description: " + finding.detail)
            appendLine("Items: " + finding.items.size)
            appendLine("Total size: " + finding.frees.value + " bytes")
            appendLine("Risk level recorded by the app: " + finding.risk.name)
            appendLine("A sample of the items:")
            appendLine(sample)
            appendLine()
            append("In two sentences: what are these, and what would the person lose by removing them?")
        }
    }

    fun refreshTrash() {
        viewModelScope.launch {
            runCatching { trash.contents() }.onSuccess { entries ->
                _state.update { it.copy(trashBytes = Bytes(entries.sumOf { entry -> entry.size.value }), trashEntries = entries) }
            }.onFailure { error ->
                _state.update { it.copy(message = "Trash could not be read. Saved files were left alone. ${error.message.orEmpty()}") }
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null, settingsPackage = null) }

    fun setRetentionDays(days: Int) = perform {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { trashPreferences.retentionDays = days }
        _state.update { it.copy(retentionDays = days) }
        "Saved. This applies on the next launch; existing files are kept until then."
    }

    fun restore(id: String) = restoreMany(listOf(id))

    fun restoreMany(ids: Collection<String>) = perform {
        val restored = ids.count { trash.restore(it) }
        "$restored of ${ids.size} put back. Existing files were kept; restored copies use a new name when needed." +
            if (restored < ids.size) " Unrestored items remain in trash; check folder access and free space, then retry." else ""
    }

    fun restoreAllFromTrash() = perform {
        val total = trash.contents().size
        val restored = trash.restoreAll()
        "$restored of $total put back. Existing files were kept." +
            if (restored < total) " Unrestored items remain in trash; check folder access and free space, then retry." else ""
    }

    fun purgeMany(ids: Collection<String>) = perform { purgeMessage(trash.purge(ids)) }
    fun emptyTrash() = perform { purgeMessage(trash.purgeAll()) }

    private fun purgeMessage(result: TrashStore.BatchResult) =
        "${result.count} removed from trash; ${Format.size(result.bytes)} freed." +
            if (result.failures.isEmpty()) "" else " ${result.failures.size} failed and remain listed. ${result.failures.first()}"

    /** Lock the controls before launching so rapid taps cannot queue duplicate mutations. */
    private fun perform(action: suspend () -> String) {
        if (_state.value.working || _state.value.scanning) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                val message = action()
                _state.update { it.copy(message = message) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(message = "Could not finish. Saved files were kept. ${error.message.orEmpty()}") }
            } finally {
                _state.update { it.copy(working = false) }
                refreshTrash()
            }
        }
    }

    fun refreshPermissions() {
        _state.update {
            it.copy(
                hasAllFilesAccess = hasAllFilesAccess(),
                hasUsageAccess = inventory.hasUsageAccess(),
                shizuku = shizuku.state(),
                aiReady = EngineHolder.isReady,
                canClearCache = shizuku.canClearCache(),
                cacheAutomationProblem = CacheAutomation.unavailableReason(getApplication()),
            )
        }
    }

    private fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

    fun scan(preserveResult: Boolean = false) {
        if (_state.value.scanning || _state.value.working) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.update { it.copy(scanning = true, scanLabel = "Reading shared storage", filesSeen = 0,
                selectedItems = emptySet(), lastResult = if (preserveResult) it.lastResult else null) }

            try {

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

            val restricted = if (shizuku.state() == ShizukuBridge.State.READY) {
                _state.update { it.copy(scanLabel = "Measuring restricted folders") }
                shizuku.restrictedFolders()
            } else null

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
                    restrictedFolders = restricted?.folders.orEmpty(),
                    restrictedNote = restricted?.note,
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
                    containers = buildContainers(result.snapshot, apps, findings, emptySet()),
                )
            }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(message = "Scan could not finish. ${error.message.orEmpty()} Try scanning again.") }
            } finally {
                _state.update { it.copy(scanning = false, scanLabel = null) }
            }
        }
    }

    /** Selecting a finding selects every item in it; the user can then deselect individually. */
    fun toggleFinding(id: String, selected: Boolean) {
        if (_state.value.working || _state.value.scanning) return
        _state.update { current ->
            val finding = current.findings.firstOrNull { it.id == id } ?: return@update current
            if (!finding.kind.canTrash) return@update current
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
        if (_state.value.working || _state.value.scanning) return
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
        if (plan.itemCount == 0 || _state.value.working || _state.value.scanning) return

        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            try {

            // App-level findings act through their own flows (system uninstall dialog,
            // Shizuku cache clear), never by deleting files behind an app's back.
            val filePaths = plan.fileItems.map { it.path }

            val result = trash.trash(filePaths, plan.fileItems.associateBy { it.path })
            _state.update {
                it.copy(
                    working = false,
                    lastResult = result,
                    selectedItems = emptySet(),
                )
            }
            refreshTrash()
            scan(preserveResult = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(message = "Cleanup stopped. Saved files were kept. ${error.message.orEmpty()}") }
            } finally {
                _state.update { it.copy(working = false) }
                refreshTrash()
            }
        }
    }

    fun toggleCache(packageName: String, selected: Boolean) {
        if (_state.value.working || _state.value.scanning) return
        val app = _state.value.apps.firstOrNull { it.packageName == packageName } ?: return
        if (app.cacheBlockedReason != null) return
        _state.update { it.copy(selectedCachePackages = if (selected) it.selectedCachePackages + packageName else it.selectedCachePackages - packageName) }
    }

    fun selectAllCache(selected: Boolean) {
        if (_state.value.working || _state.value.scanning) return
        val visible = _state.value.findings.firstOrNull { it.kind == FindingKind.APP_CACHE }?.items.orEmpty().map { it.path }.toSet()
        _state.update { it.copy(selectedCachePackages = if (!selected) emptySet() else
            it.apps.filter { app -> app.packageName in visible && app.cacheBlockedReason == null }.map { app -> app.packageName }.toSet()) }
    }

    private fun eligibleCachePackages(packages: List<String>): List<String> = packages.distinct().filter { pkg ->
        _state.value.apps.any { it.packageName == pkg && it.cacheBlockedReason == null }
    }

    fun clearCaches(packages: List<String>) {
        if (_state.value.working || _state.value.scanning) return
        val approved = eligibleCachePackages(packages)
        if (approved.isEmpty()) return
        _state.update { it.copy(working = true, cacheResults = emptyList(), cacheNote = null,
            cacheProgress = CacheBatch.Progress(null, approved.size, emptyList())) }
        cacheJob = viewModelScope.launch {
            try {
                val results = CacheBatch.run(approved, shizuku::canClearCache, { pkg ->
                    val result = shizuku.clearCache(pkg)
                    CacheBatch.Item(pkg, if (result.ok) CacheBatch.Status.CONFIRMED else CacheBatch.Status.FAILED,
                        if (result.ok) "Cache clearing confirmed" else result.stderr.ifBlank { result.stdout }.take(240))
                }) { progress -> _state.update { it.copy(cacheProgress = progress, cacheResults = progress.results) } }
                _state.update { it.copy(cacheResults = results, cacheNote = "${results.count { r -> r.status == CacheBatch.Status.CONFIRMED }} of ${approved.size} confirmed by Android.") }
            } catch (cancelled: CancellationException) {
                _state.update { current ->
                    val completed = current.cacheResults.map { it.packageName }.toSet()
                    current.copy(cacheResults = current.cacheResults + approved.filterNot { it in completed }.map {
                        CacheBatch.Item(it, CacheBatch.Status.NOT_ATTEMPTED, "Not confirmed after cancellation") },
                        cacheNote = "Stopped requesting apps. An operation already sent to Android may still finish.")
                }
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(cacheNote = "Stopped: ${error.message.orEmpty()}. Unconfirmed apps can be reviewed in Settings.") }
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    refreshCacheMeasurements(approved.toSet())
                    _state.update { it.copy(working = false, cacheProgress = null, canClearCache = shizuku.canClearCache()) }
                }
            }
        }
    }

    fun cancelCache() { cacheJob?.cancel() }

    fun useCacheAutomation(enabled: Boolean) {
        if (!_state.value.working && !_state.value.scanning) _state.update { it.copy(useCacheAutomation = enabled,
            cacheAutomationProblem = CacheAutomation.unavailableReason(getApplication())) }
    }

    fun clearCachesViaAccessibility(packages: List<String>) {
        if (_state.value.working || _state.value.scanning) return
        val approved = eligibleCachePackages(packages)
        if (approved.isEmpty()) return
        _state.update { it.copy(working = true, cacheResults = emptyList(), cacheNote = null,
            cacheProgress = CacheBatch.Progress(null, approved.size, emptyList())) }
        cacheJob = viewModelScope.launch {
            var runId: Long? = null
            try {
                runId = CacheAutomation.start(approved) ?: error("Enable Tidyra cache assistant in Accessibility first")
                CacheAutomation.state.filter { it.runId == runId }.onEach { status ->
                    _state.update { it.copy(cacheProgress = CacheBatch.Progress(status.current, status.total, status.results),
                        cacheResults = status.results, cacheNote = status.note) }
                }.first { !it.running }
            } catch (cancelled: CancellationException) {
                _state.update { current -> current.copy(cacheNote = "Cancelled. A request already sent may still finish.",
                    cacheResults = current.cacheResults + approved.filterNot { pkg -> current.cacheResults.any { it.packageName == pkg } }
                        .map { CacheBatch.Item(it, CacheBatch.Status.NOT_ATTEMPTED, "Not confirmed after cancellation") }) }
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(cacheNote = "Automation stopped: ${error.message.orEmpty()}. Use the manual Settings queue.") }
            } finally {
                runId?.let(CacheAutomation::cancel)
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    refreshCacheMeasurements(approved.toSet())
                    _state.update { it.copy(working = false, cacheProgress = null) }
                }
            }
        }
    }

    /** A queue opens Settings only on explicit Next; visiting Settings never means cache was cleared. */
    fun startCacheSettings(packages: List<String>): String? {
        if (_state.value.working || _state.value.scanning) return null
        val approved = eligibleCachePackages(packages)
        if (approved.isEmpty()) return null
        _state.update { it.copy(working = true, cacheQueue = approved, cacheQueueIndex = 0,
            cacheResults = emptyList(), cacheNote = null) }
        return approved.first()
    }

    fun nextCacheSettings(): String? {
        val current = _state.value
        if (current.cacheQueue.isEmpty()) return null
        val next = current.cacheQueueIndex + 1
        if (next >= current.cacheQueue.size) { finishCacheSettings(); return null }
        _state.update { it.copy(cacheQueueIndex = next) }
        return current.cacheQueue[next]
    }

    fun finishCacheSettings() {
        val packages = _state.value.cacheQueue
        if (packages.isEmpty()) return
        _state.update { it.copy(cacheQueue = emptyList(), cacheNote = "Settings review ended. Tidyra cannot verify which caches you cleared.") }
        cacheJob = viewModelScope.launch {
            try { refreshCacheMeasurements(packages.toSet()) }
            finally { _state.update { it.copy(working = false) } }
        }
    }

    private suspend fun refreshCacheMeasurements(packages: Set<String>) {
        runCatching { inventory.load(packages) }.onSuccess { measured ->
            val updated = measured.associateBy { it.packageName }
            _state.update { current ->
                val apps = current.apps.map { updated[it.packageName] ?: it }
                val findings = current.findings.filterNot { it.kind == FindingKind.APP_CACHE }.toMutableList()
                inventory.cacheFinding(apps)?.let(findings::add)
                val visible = findings.firstOrNull { it.kind == FindingKind.APP_CACHE }?.items.orEmpty().map { it.path }.toSet()
                current.copy(apps = apps, findings = findings.sortedByDescending { it.frees.value },
                    selectedCachePackages = current.selectedCachePackages.intersect(visible),
                    containers = buildContainers(current.snapshot, apps, findings, current.selectedItems))
            }
        }
    }

    fun uninstallWithShizuku(packageName: String) = perform {
        val result = shizuku.uninstall(packageName)
        if (result.ok) "Uninstalled $packageName. Rescan to update sizes."
        else "App was not uninstalled. ${result.stderr.ifBlank { result.stdout }}"
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

        val measured = StorageAccounting.categories(apps, snapshot)
        val appBytes = measured[StorageCategory.APPS] ?: Bytes.ZERO
        val appDataBytes = measured[StorageCategory.APP_DATA] ?: Bytes.ZERO
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
                if (!finding.kind.canTrash || item.path !in selectedItems) return@forEach
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
