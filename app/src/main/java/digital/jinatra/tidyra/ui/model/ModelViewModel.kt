package digital.jinatra.tidyra.ui.model

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import digital.jinatra.tidyra.ai.EngineHolder
import digital.jinatra.tidyra.ai.LlmEngine
import digital.jinatra.tidyra.ai.MediaPipeLlmEngine
import digital.jinatra.tidyra.ai.ModelCatalog
import digital.jinatra.tidyra.ai.ModelDownloader
import digital.jinatra.tidyra.core.model.Bytes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ModelState(
    val entries: List<Entry> = emptyList(),
    val token: String = "",
    val loadedModelPath: String? = null,
    val loading: Boolean = false,
    val operation: digital.jinatra.tidyra.core.model.OperationProgress? = null,
    val message: String? = null,
) {
    data class Entry(
        val model: ModelCatalog.Model,
        val compatibility: ModelCatalog.Compatibility,
        val installed: Boolean,
        val installedBytes: Bytes,
        val downloading: Boolean = false,
        val progress: Float = 0f,
        val downloaded: Bytes = Bytes.ZERO,
        val error: String? = null,
    )
}

class ModelViewModel(app: Application) : AndroidViewModel(app) {

    private val downloader = ModelDownloader(app)
    private val engine: LlmEngine get() = EngineHolder.engine(getApplication())

    private val _state = MutableStateFlow(ModelState())
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        val context = getApplication<Application>()
        _state.update { current ->
            current.copy(
                entries = (ModelCatalog.models + ModelCatalog.installed(context)
                    .filter { it.extension == "task" && it.name !in ModelCatalog.models.map { model -> model.fileName } }
                    .map { file -> ModelCatalog.Model(
                        id = "imported-${file.name}", name = file.name.substringAfter('_', file.name), parameters = "Imported .task",
                        downloadBytes = Bytes(file.length()), requiredRamGb = if (file.length() > 300L * 1024 * 1024) 4 else 2,
                        multimodal = false, url = "", fileName = file.name,
                        description = "Imported on this phone. Loading depends on the model's task format and available memory.",
                        needsHuggingFaceToken = false,
                        template = if (file.name.contains("smol", true) || file.name.contains("qwen", true))
                            digital.jinatra.tidyra.ai.PromptTemplate.CHATML else digital.jinatra.tidyra.ai.PromptTemplate.GEMMA,
                        licence = "Your imported model's licence") }).map { model ->
                    val file = ModelCatalog.fileFor(context, model)
                    val existing = current.entries.firstOrNull { it.model.id == model.id }
                    ModelState.Entry(
                        model = model,
                        compatibility = ModelCatalog.compatibility(context, model),
                        installed = file.exists() && file.length() > 0,
                        installedBytes = Bytes(if (file.exists()) file.length() else 0L),
                        downloading = existing?.downloading ?: false,
                        progress = existing?.progress ?: 0f,
                        downloaded = downloader.partialBytes(model),
                        error = existing?.error,
                    )
                },
            )
        }
    }

    fun setToken(token: String) = _state.update { it.copy(token = token) }

    fun download(model: ModelCatalog.Model) {
        if (model.needsHuggingFaceToken && _state.value.token.isBlank()) {
            updateEntry(model.id) { it.copy(error = "Accept this model's licence on Hugging Face and add a read token below, or choose SmolLM without an account.") }
            return
        }
        if (downloadJob?.isActive == true) {
            _state.update { it.copy(message = "One download at a time — a model is already coming down.") }
            return
        }
        downloadJob = viewModelScope.launch {
            updateEntry(model.id) { it.copy(downloading = true, error = null) }

            downloader.download(model, _state.value.token.takeIf { it.isNotBlank() }).collect { progress ->
                when (progress) {
                    is ModelDownloader.Progress.Downloading ->
                        updateEntry(model.id) {
                            it.copy(progress = progress.fraction, downloaded = progress.downloaded)
                        }

                    is ModelDownloader.Progress.Done -> {
                        updateEntry(model.id) { it.copy(downloading = false, installed = true, progress = 1f) }
                        refresh()
                    }

                    is ModelDownloader.Progress.Failed ->
                        updateEntry(model.id) { it.copy(downloading = false, error = progress.reason) }
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.update { current ->
            current.copy(entries = current.entries.map { it.copy(downloading = false) })
        }
    }

    fun delete(model: ModelCatalog.Model) {
        if (_state.value.loading || _state.value.operation != null) return
        _state.update { it.copy(loading = true, operation = digital.jinatra.tidyra.core.model.OperationProgress("Removing model")) }
        viewModelScope.launch {
            try {
            if (_state.value.loadedModelPath == ModelCatalog.fileFor(getApplication(), model).absolutePath) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { engine.unload() }
                _state.update { it.copy(loadedModelPath = null) }
            }
            downloader.delete(model)
            refresh()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { _state.update { it.copy(message = "Could not remove the model. Check storage access and try again.") } }
            finally { _state.update { it.copy(loading = false, operation = null) } }
        }
    }

    /** A model file the user already has. No network, no token, no licence gate. */
    fun import(uri: Uri, displayName: String) {
        if (_state.value.loading || _state.value.operation != null) return
        _state.update { it.copy(operation = digital.jinatra.tidyra.core.model.OperationProgress("Importing model", current = displayName)) }
        viewModelScope.launch {
            try {
            val context = getApplication<Application>()
            val target = File(ModelCatalog.modelDir(context), java.util.UUID.randomUUID().toString() + "_" + File(displayName).name)
            val partial = File(target.path + ".part")
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    require(target.extension == "task") { "Choose a .task model file" }
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        java.io.FileOutputStream(partial).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var copied = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                copied += count
                                _state.update { it.copy(operation = digital.jinatra.tidyra.core.model.OperationProgress("Importing model", current = displayName, bytesDone = copied)) }
                            }
                            output.fd.sync()
                        }
                    } ?: error("File could not be opened")
                    check(partial.length() > 0 && partial.renameTo(target)) { "Model file was empty or could not be saved" }
                    true
                }.getOrDefault(false)
            }
            if (!ok) partial.delete()

            _state.update {
                it.copy(operation = null, message = if (ok) "Imported ${target.name}" else "That file could not be read")
            }
            refresh()
            } finally { _state.update { it.copy(operation = null) } }
        }
    }

    fun load(model: ModelCatalog.Model) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            val path = ModelCatalog.fileFor(getApplication(), model).absolutePath
            _state.update { it.copy(loading = true, message = null) }
            val result = engine.load(path, model.template)
            _state.update {
                it.copy(
                    loading = false,
                    loadedModelPath = if (result.isSuccess) path else null,
                    message = result.exceptionOrNull()?.message?.let { error -> "Could not load the model: $error" }
                        ?: "${model.name} is loaded and running on this phone.",
                )
            }
        }
    }

    fun unload() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { engine.unload() }
            _state.update { it.copy(loading = false, loadedModelPath = null, message = "Model unloaded. Memory returned.") }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    // The engine is shared, so it is not unloaded here: the user unloads it, or the
    // process ends. Dropping it on every screen change would mean reloading 555 MB.


    private fun updateEntry(id: String, transform: (ModelState.Entry) -> ModelState.Entry) {
        _state.update { current ->
            current.copy(entries = current.entries.map { if (it.model.id == id) transform(it) else it })
        }
    }
}
