package digital.jinatra.tidyra.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import digital.jinatra.tidyra.core.model.Bytes
import java.io.File

/**
 * The models Tidyra offers to download, and whether this phone can actually run them.
 *
 * Nothing is bundled in the APK: the app ships at a few megabytes and the user chooses
 * whether to spend 555 MB on a model at all. Everything here works without one.
 */
object ModelCatalog {

    data class Model(
        val id: String,
        val name: String,
        val parameters: String,
        val downloadBytes: Bytes,
        val requiredRamGb: Int,
        val multimodal: Boolean,
        val url: String,
        val fileName: String,
        val description: String,
        /** Hugging Face serves this behind a licence acceptance, so a token is needed. */
        val needsHuggingFaceToken: Boolean = true,
        val licence: String = "Gemma Terms of Use",
    )

    val models = listOf(
        Model(
            id = "gemma3-1b-it-int4",
            name = "Gemma 3 1B",
            parameters = "1B, int4",
            downloadBytes = Bytes(555L * 1024 * 1024),
            requiredRamGb = 4,
            multimodal = false,
            url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
            fileName = "gemma3-1b-it-int4.task",
            description = "Explains findings, groups files and drafts the cleanup plan. Fast on most phones. Text only.",
        ),
        Model(
            id = "gemma-3n-e2b-it",
            name = "Gemma 3n E2B",
            parameters = "2B effective, int4",
            downloadBytes = Bytes(3100L * 1024 * 1024),
            requiredRamGb = 8,
            multimodal = true,
            url = "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-E2B-it-int4.task",
            fileName = "gemma-3n-E2B-it-int4.task",
            description = "Can look at the photos themselves — spots blurry shots and near-identical bursts. Large download, slower to load.",
        ),
    )

    /** A model file the user already has. No download, no token, no network at all. */
    const val IMPORTED_MODEL_ID = "imported"

    enum class Compatibility { SUPPORTED, LOW_MEMORY, UNSUPPORTED_ABI }

    fun compatibility(context: Context, model: Model): Compatibility {
        val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        if (!is64Bit) return Compatibility.UNSUPPORTED_ABI

        val activityManager = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { activityManager?.getMemoryInfo(it) }
        val totalRamGb = info.totalMem / (1024.0 * 1024 * 1024)

        return if (totalRamGb + 0.5 < model.requiredRamGb) Compatibility.LOW_MEMORY
        else Compatibility.SUPPORTED
    }

    /** The shared location other Jinatra apps read from. Stable: do not move it. */
    const val SHARED_DIR = "Documents/Jinatra/models"

    /**
     * Where a downloaded model lives.
     *
     * Shared storage when Tidyra has all-files access, so one 555 MB download can serve
     * every Jinatra app on the phone instead of each shipping its own copy. It also
     * survives uninstalling Tidyra, and the user can see and delete it in any file
     * manager — a file this large has no business being invisible.
     *
     * Falls back to app-private storage when all-files access has not been granted.
     */
    fun modelDir(context: Context): File {
        val shared = File(Environment.getExternalStorageDirectory(), SHARED_DIR)
        if (Environment.isExternalStorageManager() && (shared.isDirectory || shared.mkdirs())) {
            return shared
        }
        return File(context.filesDir, "models").apply { mkdirs() }
    }

    fun fileFor(context: Context, model: Model): File = File(modelDir(context), model.fileName)

    fun installed(context: Context): List<File> =
        modelDir(context).listFiles()?.filter { it.isFile && it.length() > 0 }.orEmpty()
}
