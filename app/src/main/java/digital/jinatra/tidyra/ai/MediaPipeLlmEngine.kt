package digital.jinatra.tidyra.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gemma on the phone, through MediaPipe's LLM Inference API.
 *
 * Loading takes several seconds and holds real memory, so it is explicit: the app loads
 * the model when the user opens an AI-backed screen and unloads it when they leave.
 */
class MediaPipeLlmEngine(private val context: Context) : LlmEngine {

    private var inference: LlmInference? = null
    private var template: PromptTemplate = PromptTemplate.PLAIN

    override val isLoaded: Boolean get() = inference != null

    override suspend fun load(modelPath: String, template: PromptTemplate): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(modelPath)
            require(file.exists() && file.length() > 0) { "Model file is missing" }

            unload()
            this@MediaPipeLlmEngine.template = template
            inference = LlmInference.createFromOptions(
                context,
                LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(MAX_TOKENS)
                    .build(),
            )
        }
    }

    override fun unload() {
        runCatching { inference?.close() }
        inference = null
    }

    override fun generate(prompt: String): Flow<String> = callbackFlow {
        val engine = inference
        if (engine == null) {
            close(IllegalStateException("No model loaded"))
            return@callbackFlow
        }
        runCatching {
            engine.generateResponseAsync(withRules(prompt)) { partial, done ->
                trySend(partial)
                if (done) close()
            }
        }.onFailure { close(it) }

        awaitClose { }
    }.flowOn(Dispatchers.IO)

    override suspend fun complete(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val engine = inference ?: error("No model loaded")
            // Trim at the model's own stop marker: left to itself it runs past the end of
            // its turn and starts inventing the next one.
            template.trim(engine.generateResponse(withRules(prompt)))
        }
    }

    /** Frames the turn the way this model expects. Bare text gets continued, not answered. */
    private fun withRules(prompt: String) = template.wrap(LlmEngine.SYSTEM_RULES.trim(), prompt)

    private companion object {
        const val MAX_TOKENS = 1024
    }
}
