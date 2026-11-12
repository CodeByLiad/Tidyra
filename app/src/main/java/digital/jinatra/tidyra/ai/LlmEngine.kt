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

    suspend fun load(modelPath: String, template: PromptTemplate = PromptTemplate.PLAIN): Result<Unit>

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

/**
 * How a model expects a turn to be framed.
 *
 * An instruct model handed bare text does not answer it — it continues it. Ask SmolLM a
 * question without ChatML markers and it emits an end-of-turn token and then invents a
 * fresh conversation, which is exactly what it did the first time this shipped. The
 * markers are not decoration; they are the difference between an answer and a monologue.
 */
enum class PromptTemplate {
    /** ChatML: SmolLM, Qwen, and most community instruct builds. */
    CHATML {
        override fun wrap(system: String, user: String) = buildString {
            append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
            append("<|im_start|>user\n").append(user).append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }

        override val stops = listOf("<|im_end|>", "<|im_start|>")
    },

    /** Gemma's own turn markers. */
    GEMMA {
        override fun wrap(system: String, user: String) = buildString {
            append("<start_of_turn>user\n").append(system).append("\n\n").append(user)
            append("<end_of_turn>\n<start_of_turn>model\n")
        }

        override val stops = listOf("<end_of_turn>", "<start_of_turn>")
    },

    /** No framing. Only correct for a base completion model. */
    PLAIN {
        override fun wrap(system: String, user: String) = system + "\n\n" + user
        override val stops = emptyList<String>()
    };

    abstract fun wrap(system: String, user: String): String
    abstract val stops: List<String>

    /**
     * Pulls the answer out of a raw completion.
     *
     * Cutting at the first stop marker is not enough: a model often opens with one,
     * closing a turn it thinks it is already in, which would leave nothing at all. So the
     * reply is split on every marker and the first segment with real content wins, with
     * any leading role word ("assistant", "model") dropped.
     */
    fun trim(raw: String): String {
        if (stops.isEmpty()) return raw.trim()

        var text = raw
        stops.forEach { stop -> text = text.replace(stop, SPLIT) }

        return text.split(SPLIT)
            .asSequence()
            .map { segment ->
                segment.trim()
                    .removePrefix("assistant").removePrefix("model")
                    .removePrefix("system").removePrefix("user")
                    .trim()
            }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    private companion object {
        const val SPLIT = "\u0000"
    }
}

/** Used when no model is installed. Every screen works without one. */
class NoModelEngine : LlmEngine {
    override val isLoaded = false
    override suspend fun load(modelPath: String, template: PromptTemplate) =
        Result.failure<Unit>(IllegalStateException("No model installed"))
    override fun unload() = Unit
    override fun generate(prompt: String): Flow<String> = kotlinx.coroutines.flow.emptyFlow()
    override suspend fun complete(prompt: String) = Result.failure<String>(IllegalStateException("No model installed"))
}
