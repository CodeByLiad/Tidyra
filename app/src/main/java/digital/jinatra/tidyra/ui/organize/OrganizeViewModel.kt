package digital.jinatra.tidyra.ui.organize

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import digital.jinatra.tidyra.core.organize.Organizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class OrganizeState(
    val folders: List<File> = emptyList(),
    val selected: File? = null,
    val plan: Organizer.Plan? = null,
    val lastResult: Organizer.Result? = null,
    val working: Boolean = false,
    val includeOther: Boolean = false,
)

class OrganizeViewModel(app: Application) : AndroidViewModel(app) {

    private val organizer = Organizer()
    private val _state = MutableStateFlow(OrganizeState())
    val state: StateFlow<OrganizeState> = _state.asStateFlow()

    init {
        val root = Environment.getExternalStorageDirectory()
        // Only folders people actually accumulate loose files in. DCIM is deliberately
        // absent: the camera roll is not clutter and Tidyra does not reorganise it.
        val candidates = listOf("Download", "Documents", "Pictures", "Movies", "Music")
            .map { File(root, it) }
            .filter { it.isDirectory }
        _state.update { it.copy(folders = candidates) }
    }

    fun select(folder: File) {
        _state.update { it.copy(selected = folder, lastResult = null) }
        buildPlan()
    }

    fun setIncludeOther(include: Boolean) {
        _state.update { it.copy(includeOther = include) }
        buildPlan()
    }

    private fun buildPlan() {
        val folder = _state.value.selected ?: return
        viewModelScope.launch {
            _state.update { it.copy(working = true) }
            val plan = withContext(Dispatchers.IO) {
                organizer.plan(folder, includeOther = _state.value.includeOther)
            }
            _state.update { it.copy(plan = plan, working = false) }
        }
    }

    /** Nothing moves until this is called, and the result is its own undo record. */
    fun apply() {
        val plan = _state.value.plan ?: return
        if (plan.moves.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(working = true) }
            val result = organizer.apply(plan)
            _state.update { it.copy(working = false, lastResult = result, plan = null) }
            buildPlan()
        }
    }

    fun undo() {
        val result = _state.value.lastResult ?: return
        viewModelScope.launch {
            _state.update { it.copy(working = true) }
            organizer.undo(result)
            _state.update { it.copy(working = false, lastResult = null) }
            buildPlan()
        }
    }
}
