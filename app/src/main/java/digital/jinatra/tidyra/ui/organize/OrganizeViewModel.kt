package digital.jinatra.tidyra.ui.organize

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import digital.jinatra.tidyra.core.organize.Organizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class OrganizeState(
    val root: File = Environment.getExternalStorageDirectory(),
    val folders: List<File> = emptyList(),
    val selected: File? = null,
    val plan: Organizer.Plan? = null,
    val selectedPaths: Set<String> = emptySet(),
    val issues: Map<String, String> = emptyMap(),
    val reviewPlan: Organizer.Plan? = null,
    val lastResult: Organizer.Result? = null,
    val working: Boolean = false,
    val actionLabel: String = "Reading files",
    val message: String? = null,
    val pickingFor: Set<String> = emptySet(),
    val destinationDirectory: File? = null,
    val destinationFolders: List<File> = emptyList(),
    val choosingSource: Boolean = false,
)

class OrganizeViewModel(app: Application) : AndroidViewModel(app) {
    private val organizer = Organizer(File(app.filesDir, "organiser/history.json"))
    private val root = Environment.getExternalStorageDirectory()
    private val _state = MutableStateFlow(OrganizeState(root = root,
        folders = listOf("Download", "Documents", "Pictures", "Movies", "Music").map { File(root, it) }))
    val state = _state.asStateFlow()

    init { operate("Reading undo history") { _state.update { it.copy(lastResult = organizer.pending()) } } }

    fun select(folder: File) {
        if (_state.value.working) return
        _state.update { it.copy(selected = folder, plan = null, selectedPaths = emptySet(), issues = emptyMap(),
            reviewPlan = null, destinationDirectory = null, message = null) }
        operate("Reading ${folder.name}") { refreshPlan() }
    }

    private suspend fun refreshPlan() {
        val folder = _state.value.selected ?: return
        val plan = withContext(Dispatchers.IO) {
            check(Organizer.destinationAllowed(root, folder)) { "Choose a source folder in internal storage" }
            check(folder.isDirectory && folder.listFiles() != null) { "This folder cannot be read. Check all-files access and try again." }
            organizer.plan(folder, includeConflicts = true)
        }
        val issues = issuesFor(plan)
        _state.update { it.copy(plan = plan, issues = issues, selectedPaths = emptySet(), reviewPlan = null) }
    }

    private suspend fun issuesFor(plan: Organizer.Plan): Map<String, String> = withContext(Dispatchers.IO) {
        plan.moves.mapNotNull { move ->
            val reason = when {
                !move.from.isFile -> "Source is unavailable; refresh the folder"
                move.to.exists() -> "An item with this name already exists. Choose another folder."
                !Organizer.destinationAllowed(root, move.to.parentFile!!) -> "Choose a safe destination folder"
                else -> null
            }
            reason?.let { move.from.path to it }
        }.toMap()
    }

    fun toggle(path: String, selected: Boolean) {
        if (_state.value.working || _state.value.reviewPlan != null) return
        if (_state.value.plan?.moves?.none { it.from.path == path } != false) return
        _state.update { it.copy(selectedPaths = if (selected) it.selectedPaths + path else it.selectedPaths - path) }
    }

    fun selectAll(selected: Boolean) {
        if (_state.value.working) return
        _state.update { it.copy(selectedPaths = if (selected) it.plan?.moves.orEmpty().map { move -> move.from.path }.toSet() else emptySet()) }
    }

    fun review() {
        val current = _state.value
        if (current.working) return
        val moves = current.plan?.moves.orEmpty().filter { it.from.path in current.selectedPaths }
        if (moves.isEmpty() || moves.any { it.from.path in current.issues }) return
        _state.update { it.copy(reviewPlan = Organizer.Plan(moves, emptyList()), message = null) }
    }

    fun editReview() { if (!_state.value.working) _state.update { it.copy(reviewPlan = null) } }

    fun openDestination(paths: Set<String>) {
        if (_state.value.working) return
        val moves = _state.value.plan?.moves.orEmpty().filter { it.from.path in paths }
        if (moves.isEmpty()) return
        _state.update { it.copy(pickingFor = moves.map { move -> move.from.path }.toSet(), reviewPlan = null, message = null, choosingSource = false) }
        operate("Reading destination folders") {
            val start = withContext(Dispatchers.IO) {
                var dir = moves.first().to.parentFile!!
                while (!dir.isDirectory && dir != root) dir = dir.parentFile ?: root
                if (Organizer.destinationAllowed(root, dir)) dir else root
            }
            loadDestination(start)
        }
    }

    fun browseSource() {
        if (_state.value.working) return
        _state.update { it.copy(choosingSource = true, pickingFor = emptySet(), reviewPlan = null, message = null) }
        operate("Reading source folders") {
            val start = withContext(Dispatchers.IO) {
                _state.value.selected?.takeIf { it.isDirectory && Organizer.destinationAllowed(root, it) } ?: root
            }
            loadDestination(start)
        }
    }

    fun navigateDestination(directory: File) {
        if (_state.value.destinationDirectory == null) return
        operate("Reading destination folders") { loadDestination(directory) }
    }

    private suspend fun loadDestination(directory: File) {
        val folders = withContext(Dispatchers.IO) {
            check(Organizer.destinationAllowed(root, directory) && directory.isDirectory) { "Choose a folder in internal storage, outside protected folders." }
            val children = directory.listFiles() ?: error("This folder cannot be read")
            children.filter { it.isDirectory && Organizer.destinationAllowed(root, it) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
        _state.update { it.copy(destinationDirectory = directory, destinationFolders = folders, message = null) }
    }

    fun cancelDestination() {
        if (!_state.value.working) _state.update { it.copy(destinationDirectory = null, pickingFor = emptySet(), message = null, choosingSource = false) }
    }

    fun chooseDestination() {
        val current = _state.value
        val directory = current.destinationDirectory ?: return
        if (current.choosingSource) {
            operate("Reading ${directory.name}") {
                _state.update { it.copy(selected = directory, destinationDirectory = null, choosingSource = false,
                    plan = null, selectedPaths = emptySet(), issues = emptyMap(), message = null) }
                refreshPlan()
            }
            return
        }
        val plan = current.plan ?: return
        operate("Updating destinations") {
            val updated = withContext(Dispatchers.IO) {
                plan.copy(moves = plan.moves.map { if (it.from.path in current.pickingFor) Organizer.retarget(it, directory, root) else it })
            }
            val issues = issuesFor(updated)
            _state.update { it.copy(plan = updated, issues = issues, destinationDirectory = null, pickingFor = emptySet(), message = null) }
        }
    }

    fun createFolder(name: String) {
        val directory = _state.value.destinationDirectory ?: return
        operate("Creating folder") {
            val created = withContext(Dispatchers.IO) {
                val clean = name.trim()
                require(clean.isNotEmpty() && clean.length <= 128 && !clean.startsWith('.') && clean.none { it == '/' || it == '\\' || it.isISOControl() }) { "Use a short folder name without slashes, line breaks, or leading dots" }
                val target = File(directory, clean)
                check(Organizer.destinationAllowed(root, target)) { "Choose a different folder name" }
                check(!target.exists()) { "That folder already exists; choose it from the list" }
                check(target.mkdir()) { "Could not create this folder" }
                target
            }
            loadDestination(created)
        }
    }

    fun apply() {
        val plan = _state.value.reviewPlan ?: return
        if (plan.moves.isEmpty()) return
        operate("Moving files") {
            val result = organizer.apply(plan)
            val pending = organizer.pending()
            _state.update { it.copy(lastResult = pending, reviewPlan = null,
                message = "${result.moved.size} moved." + if (result.failed.isEmpty()) "" else
                    " ${result.failed.size} left alone: " + result.failed.take(3).joinToString("; ") { "${it.first.from.name}: ${it.second}" }) }
            refreshPlan()
        }
    }

    fun undo() {
        val result = _state.value.lastResult ?: return
        operate("Putting files back") {
            val restored = organizer.undo(result)
            val pending = organizer.pending()
            _state.update { it.copy(lastResult = pending, reviewPlan = null,
                message = "$restored restored." +
                    if (restored < result.moved.size) " Changed or inaccessible files were left alone; undo is kept." else "") }
            refreshPlan()
        }
    }

    private fun operate(label: String, action: suspend () -> Unit) {
        if (_state.value.working) return
        _state.update { it.copy(working = true, actionLabel = label) }
        viewModelScope.launch {
            try { action() } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _state.update { it.copy(message = error.message ?: "Could not finish. Files and undo records were kept.") } }
            finally { _state.update { it.copy(working = false) } }
        }
    }
}
