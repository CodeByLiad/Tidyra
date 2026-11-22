package digital.jinatra.tidyra.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import digital.jinatra.tidyra.core.model.OperationProgress

@Composable
fun OperationStatus(progress: OperationProgress?, modifier: Modifier = Modifier) {
    if (progress == null) return
    Surface(modifier = modifier, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(progress.label + (progress.total?.let { " · ${progress.completed.coerceAtMost(it)} / $it" } ?: ""), style = MaterialTheme.typography.labelLarge)
            progress.current?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
            val fraction = progress.fraction
            if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}
