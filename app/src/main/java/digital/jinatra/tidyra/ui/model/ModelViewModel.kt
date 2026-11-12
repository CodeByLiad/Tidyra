package digital.jinatra.tidyra.ui.model

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    private val engine: LlmEngine = MediaPipeLlmEngine(app)

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
                entries = ModelCatalog.models.map { model ->
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
        viewModelScope.launch {
            if (_state.value.loadedModelPath == ModelCatalog.fileFor(getApplication(), model).absolutePath) {
                engine.unload()
                _state.update { it.copy(loadedModelPath = null) }
            }
            downloader.delete(model)
            refresh()
        }
    }

    /** A model file the user already has. No network, no token, no licence gate. */
    fun import(uri: Uri, displayName: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val target = File(ModelCatalog.modelDir(context), displayName)
            val ok = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)

            _state.update {
                it.copy(message = if (ok) "Imported ${target.name}" else "That file could not be read")
            }
            refresh()
        }
    }

    fun load(model: ModelCatalog.Model) {
        viewModelScope.launch {
            val path = ModelCatalog.fileFor(getApplication(), model).absolutePath
            _state.update { it.copy(loading = true, message = null) }
            val result = engine.load(path)
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
        engine.unload()
        _state.update { it.copy(loadedModelPath = null, message = "Model unloaded. Memory returned.") }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        engine.unload()
        super.onCleared()
    }

    private fun updateEntry(id: String, transform: (ModelState.Entry) -> ModelState.Entry) {
        _state.update { current ->
            current.copy(entries = current.entries.map { if (it.model.id == id) transform(it) else it })
        }
    }
}
