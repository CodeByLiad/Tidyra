package digital.jinatra.tidyra.ui.organize

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import digital.jinatra.tidyra.core.organize.Organizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

data class FolderEntry(val name: String, val files: Int, val bytes: digital.jinatra.tidyra.core.model.Bytes, val loose: Boolean = false)

data class OrganizeState(
    val folders: List<File> = emptyList(),
    val layout: List<FolderEntry> = emptyList(),
    val selected: File? = null,
    val plan: Organizer.Plan? = null,
    val lastResult: Organizer.Result? = null,
    val working: Boolean = false,
    val includeOther: Boolean = false,
    val message: String? = null,
)

class OrganizeViewModel(app: Application) : AndroidViewModel(app) {
    private val organizer = Organizer(File(app.filesDir, "organiser/history.json"))
    private val _state = MutableStateFlow(OrganizeState())
    val state = _state.asStateFlow()

    init {
        val root = Environment.getExternalStorageDirectory()
        _state.update { it.copy(folders = listOf("Download", "Documents", "Pictures", "Movies", "Music")
            .map { name -> File(root, name) }.filter { folder -> folder.isDirectory }) }
        operate { _state.update { it.copy(lastResult = organizer.pending()) } }
    }

    fun select(folder: File) {
        if (_state.value.working || folder !in _state.value.folders) return
        _state.update { it.copy(selected = folder, plan = null, message = null) }
        operate { refreshLayout() }
    }

    fun setIncludeOther(include: Boolean) {
        if (_state.value.working) return
        _state.update { it.copy(includeOther = include) }
        operate { refreshLayout() }
    }

    private suspend fun refreshLayout() {
        val folder = _state.value.selected ?: return
        val includeOther = _state.value.includeOther
        val (plan, layout) = withContext(Dispatchers.IO) {
            organizer.plan(folder, includeOther) to readLayout(folder)
        }
        _state.update { it.copy(plan = plan, layout = layout) }
    }

    private suspend fun readLayout(folder: File): List<FolderEntry> {
        val children = folder.listFiles() ?: return emptyList()
        val rows = mutableListOf<FolderEntry>()
        children.filter { it.isDirectory && !it.isHidden && !Files.isSymbolicLink(it.toPath()) }.forEach { dir ->
            var count = 0
            var bytes = 0L
            dir.walkTopDown().onEnter { !Files.isSymbolicLink(it.toPath()) }.forEach { file ->
                currentCoroutineContext().ensureActive()
                if (file.isFile) { count++; bytes += file.length() }
            }
            rows += FolderEntry(dir.name, count, digital.jinatra.tidyra.core.model.Bytes(bytes))
        }
        val loose = children.filter { it.isFile && !it.name.startsWith(".") }
        if (loose.isNotEmpty()) rows += FolderEntry("Loose files", loose.size,
            digital.jinatra.tidyra.core.model.Bytes(loose.sumOf { it.length() }), true)
        return rows.sortedWith(compareByDescending<FolderEntry> { it.loose }.thenByDescending { it.bytes.value })
    }

    fun apply() {
        val plan = _state.value.plan ?: return
        if (plan.moves.isEmpty()) return
        operate {
            val result = organizer.apply(plan)
            val pending = organizer.pending()
            _state.update { it.copy(lastResult = pending,
                message = "${result.moved.size} filed." + if (result.failed.isEmpty()) "" else
                    " ${result.failed.size} left alone: " + result.failed.take(3).joinToString("; ") { failure ->
                        "${failure.first.from.name}: ${failure.second}"
                    }) }
            refreshLayout()
        }
    }

    fun undo() {
        val result = _state.value.lastResult ?: return
        operate {
            val restored = organizer.undo(result)
            val pending = organizer.pending()
            _state.update { it.copy(lastResult = pending,
                message = "$restored put back. Existing files were kept; restored copies get a new name when needed." +
                    if (restored < result.moved.size) " Changed or inaccessible files were left alone; the undo record is kept." else "") }
            refreshLayout()
        }
    }

    private fun operate(action: suspend () -> Unit) {
        if (_state.value.working) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _state.update { it.copy(message = "Could not finish organising. Files and recovery records were kept. ${error.message.orEmpty()}") }
            } finally { _state.update { it.copy(working = false) } }
        }
    }
}
