package digital.jinatra.tidyra.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import digital.jinatra.tidyra.core.preview.LocalImageDecoder
import java.io.File

@Composable
fun FilePreview(file: File, modifier: Modifier = Modifier, large: Boolean = false, version: Long = 0) {
    val image by produceState<Pair<Boolean, android.graphics.Bitmap?>>(true to null, file.path, large, version) {
        value = true to null
        value = false to LocalImageDecoder.load(file, if (large) 1600 else 240)
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
        val bitmap = image.second
        if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "Image preview of ${file.name}",
            modifier = Modifier.fillMaxSize(), contentScale = if (large) ContentScale.Fit else ContentScale.Crop)
        else Text(if (!LocalImageDecoder.isImage(file)) file.extension.uppercase().ifEmpty { "FILE" }
            else if (image.first) "Loading preview" else "Preview unavailable",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
