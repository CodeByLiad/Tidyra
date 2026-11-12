package digital.jinatra.tidyra.ai

import kotlinx.coroutines.flow.Flow

/**
 * The boundary between Tidyra and whatever runs the model.
 *
 * It exists so the runtime can be swapped without touching a line of UI. The first
 * implementation is MediaPipe's LLM Inference API, which ships prebuilt native libraries —
 * fine for a direct APK, possibly a problem for F-Droid's build-from-source policy.
 * A llama.cpp backend compiled from source can replace it behind this interface.
 *
 * The model is never in the decision path. It explains, names and groups; it does not
 * choose what gets deleted, and nothing it returns is acted on without the user approving it.
 */
interface LlmEngine {

    val isLoaded: Boolean

    suspend fun load(modelPath: String): Result<Unit>

    fun unload()

    /** Streams tokens as they are produced, so the UI can show thinking rather than a spinner. */
    fun generate(prompt: String): Flow<String>

    suspend fun complete(prompt: String): Result<String>

    companion object {
        /**
         * The one instruction every prompt carries. Tidyra's first principle is that nothing
         * happens without approval, and a model that believes it can delete things would
         * write copy that implies it can.
         */
        const val SYSTEM_RULES = """
You are the assistant inside Tidyra, an Android storage app. You are given facts about
files and apps on this phone. You never delete, move or change anything: you explain what
something is, how risky removing it would be, and what the person would lose.

Rules:
- Use only the facts given. Never invent a file, a size or a date.
- Sizes are exact. Repeat them as given; never round a size into vagueness.
- If something looks personal (photos, documents, business records, backups, wallet files),
  say so plainly and recommend keeping or backing it up first.
- Be brief. Two sentences unless asked for more.
"""
    }
}

/** Used when no model is installed. Every screen works without one. */
class NoModelEngine : LlmEngine {
    override val isLoaded = false
    override suspend fun load(modelPath: String) = Result.failure<Unit>(IllegalStateException("No model installed"))
    override fun unload() = Unit
    override fun generate(prompt: String): Flow<String> = kotlinx.coroutines.flow.emptyFlow()
    override suspend fun complete(prompt: String) = Result.failure<String>(IllegalStateException("No model installed"))
}
