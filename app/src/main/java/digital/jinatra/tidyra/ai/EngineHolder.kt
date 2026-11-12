package digital.jinatra.tidyra.ai

import android.content.Context

/**
 * One model, one copy in memory.
 *
 * A loaded Gemma holds hundreds of megabytes of RAM, so the model screen and the findings
 * screen share a single instance rather than each loading their own. Loading and unloading
 * stay explicit and user-driven: the app never quietly holds a model it is not using.
 */
object EngineHolder {

    @Volatile
    private var instance: LlmEngine? = null

    fun engine(context: Context): LlmEngine =
        instance ?: MediaPipeLlmEngine(context.applicationContext).also { instance = it }

    val isReady: Boolean get() = instance?.isLoaded == true

    fun unload() {
        instance?.unload()
    }
}
