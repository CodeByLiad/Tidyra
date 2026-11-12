package digital.jinatra.tidyra.ai

import android.content.Context
import digital.jinatra.tidyra.core.model.Bytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Downloads a model file, resumably.
 *
 * A 555 MB download on a phone will be interrupted — by a tunnel, a call, a screen lock —
 * so this resumes from the bytes already on disk with a Range request instead of starting
 * over. This is the only outbound network request Tidyra ever makes.
 */
class ModelDownloader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    sealed interface Progress {
        data class Downloading(val downloaded: Bytes, val total: Bytes) : Progress {
            val fraction: Float
                get() = if (total.value <= 0) 0f else downloaded.value.toFloat() / total.value
        }

        data class Done(val file: File) : Progress
        data class Failed(val reason: String, val resumable: Boolean) : Progress
    }

    fun download(model: ModelCatalog.Model, huggingFaceToken: String?): Flow<Progress> = callbackFlow {
        val target = ModelCatalog.fileFor(context, model)
        val partial = File(target.absolutePath + ".part")
        val already = if (partial.exists()) partial.length() else 0L

        if (target.exists() && target.length() > 0) {
            trySend(Progress.Done(target))
            close()
            return@callbackFlow
        }

        val request = Request.Builder()
            .url(model.url)
            .apply {
                if (already > 0) header("Range", "bytes=$already-")
                // Only ever to Hugging Face. A credential follows its host, not a URL
                // that happens to be in a list the app could one day read from elsewhere.
                val host = model.url.toHttpUrlOrNull()?.host.orEmpty()
                val trusted = host == "huggingface.co" || host.endsWith(".huggingface.co") || host.endsWith(".hf.co")
                if (!huggingFaceToken.isNullOrBlank() && trusted) {
                    header("Authorization", "Bearer " + huggingFaceToken)
                }
            }
            .build()

        val call = client.newCall(request)
        val response = runCatching { call.execute() }.getOrElse { error ->
            trySend(Progress.Failed(error.message ?: "Network error", resumable = true))
            close()
            return@callbackFlow
        }

        response.use { result ->
            if (!result.isSuccessful) {
                val reason = when (result.code) {
                    401, 403 -> "Hugging Face refused the download. This model needs you to accept its licence and paste an access token."
                    404 -> "The model file is no longer at that address."
                    else -> "Download failed with HTTP ${result.code}"
                }
                trySend(Progress.Failed(reason, resumable = result.code !in listOf(401, 403, 404)))
                close()
                return@use
            }

            val body = result.body
            if (body == null) {
                trySend(Progress.Failed("The server sent no file", resumable = true))
                close()
                return@use
            }
            val contentLength = body.contentLength()
            val total = if (contentLength > 0) contentLength + already else model.downloadBytes.value
            // A server that ignores Range sends 200 and the whole file: start over rather than
            // appending to what is already there and producing a corrupt model.
            val append = result.code == 206 && already > 0
            if (!append && partial.exists()) partial.delete()

            var written = if (append) already else 0L

            runCatching {
                body.byteStream().use { input ->
                    java.io.FileOutputStream(partial, append).use { output ->
                        val buffer = ByteArray(1 shl 16)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            trySend(Progress.Downloading(Bytes(written), Bytes(total)))
                        }
                        output.flush()
                    }
                }
            }.onFailure { error ->
                trySend(Progress.Failed(error.message ?: "Download interrupted", resumable = true))
                close()
                return@use
            }

            if (partial.renameTo(target)) {
                trySend(Progress.Done(target))
            } else {
                trySend(Progress.Failed("Downloaded, but could not be saved", resumable = false))
            }
            close()
        }

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    fun delete(model: ModelCatalog.Model): Boolean {
        val target = ModelCatalog.fileFor(context, model)
        File(target.absolutePath + ".part").delete()
        return target.delete()
    }

    fun partialBytes(model: ModelCatalog.Model): Bytes {
        val partial = File(ModelCatalog.fileFor(context, model).absolutePath + ".part")
        return Bytes(if (partial.exists()) partial.length() else 0L)
    }
}
