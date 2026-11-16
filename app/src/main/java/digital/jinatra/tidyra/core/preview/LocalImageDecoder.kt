package digital.jinatra.tidyra.core.preview

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.LruCache
import digital.jinatra.tidyra.core.organize.Organizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Bounded local decoding only; no image URI or filename is sent over a network. */
object LocalImageDecoder {
    private val decodes = Semaphore(2)
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    fun isImage(file: File) = file.extension.lowercase() in Organizer.Bucket.IMAGES.extensions

    suspend fun load(file: File, maxSide: Int): Bitmap? = decodes.withPermit {
        withContext(Dispatchers.IO) {
            if (!isImage(file)) return@withContext null
            val bound = maxSide.coerceIn(48, 1600)
            val key = "${file.path}:${file.length()}:${file.lastModified()}:$bound"
            cache.get(key)?.let { return@withContext it }
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    val ratio = (bound.toDouble() / max(info.size.width, info.size.height)).coerceAtMost(1.0)
                    decoder.setTargetSize(max(1, (info.size.width * ratio).roundToInt()), max(1, (info.size.height * ratio).roundToInt()))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }.also { cache.put(key, it) }
            }.getOrNull()
        }
    }
}
