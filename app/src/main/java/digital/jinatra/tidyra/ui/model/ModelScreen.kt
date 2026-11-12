package digital.jinatra.tidyra.ui.model

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import digital.jinatra.tidyra.ai.ModelCatalog
import digital.jinatra.tidyra.ui.format.Format
import digital.jinatra.tidyra.ui.theme.GlassGreen
import digital.jinatra.tidyra.ui.theme.HazardRed
import digital.jinatra.tidyra.ui.theme.HiVis
import digital.jinatra.tidyra.ui.theme.Ink
import digital.jinatra.tidyra.ui.theme.LocalStationType
import digital.jinatra.tidyra.ui.theme.MetalGrey
import digital.jinatra.tidyra.ui.theme.Module

/**
 * The model depot.
 *
 * Nothing here is required: every screen in Tidyra works with no model installed. What a
 * model adds is explanation — why a finding is what it is, in plain language, computed on
 * this phone and never sent anywhere.
 */
@Composable
fun ModelScreen(
    state: ModelState,
    onDownload: (ModelCatalog.Model) -> Unit,
    onCancel: () -> Unit,
    onDelete: (ModelCatalog.Model) -> Unit,
    onLoad: (ModelCatalog.Model) -> Unit,
    onUnload: () -> Unit,
    onToken: (String) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalStationType.current

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Module.u),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Ink)
                    .padding(Module.x2),
            ) {
                Text("ON-DEVICE AI", style = type.wordmark, color = androidx.compose.ui.graphics.Color(0xFFF4F1EA))
                Text(
                    text = "Gemma runs on this phone. Your file names, sizes and folders are never uploaded — the download below is the only network request Tidyra ever makes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.ui.graphics.Color(0xFFB9B4AA),
                    modifier = Modifier.padding(top = Module.u),
                )
            }
        }

        items(state.entries, key = { it.model.id }) { entry ->
            ModelRow(
                entry = entry,
                onDownload = { onDownload(entry.model) },
                onCancel = onCancel,
                onDelete = { onDelete(entry.model) },
                onLoad = { onLoad(entry.model) },
                onUnload = onUnload,
                isLoaded = state.loadedModelPath?.endsWith(entry.model.fileName) == true,
            )
        }

        item {
            Column(Modifier.padding(horizontal = Module.x2)) {
                Text("HUGGING FACE TOKEN", style = type.stencil, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "Google serves Gemma behind a licence you accept on huggingface.co. Paste a read token here; it is stored on this device and sent only to huggingface.co.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Module.half, bottom = Module.u),
                )
                OutlinedTextField(
                    value = state.token,
                    onValueChange = onToken,
                    label = { Text("hf_…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onImport, modifier = Modifier.padding(top = Module.half)) {
                    Text("Or import a .task file you already have")
                }
            }
        }

        state.message?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Module.x2),
                )
            }
        }
    }
}

@Composable
private fun ModelRow(
    entry: ModelState.Entry,
    isLoaded: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
) {
    val type = LocalStationType.current
    val incompatible = entry.compatibility != ModelCatalog.Compatibility.SUPPORTED

    Column(
        Modifier
            .padding(horizontal = Module.x2)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    when {
                        isLoaded -> GlassGreen
                        incompatible -> MetalGrey
                        entry.installed -> HiVis
                        else -> MaterialTheme.colorScheme.outline
                    },
                ),
        )
        Column(Modifier.padding(Module.x2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.model.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${entry.model.parameters} · ${Format.size(entry.model.downloadBytes)}" +
                            if (entry.model.multimodal) " · can read images" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isLoaded) {
                    Text("RUNNING", style = type.stencil, color = GlassGreen)
                }
            }

            Text(
                text = entry.model.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Module.half),
            )

            when (entry.compatibility) {
                ModelCatalog.Compatibility.LOW_MEMORY -> Reason(
                    "This phone has less RAM than this model needs (${entry.model.requiredRamGb} GB). It would load, then be killed.",
                )

                ModelCatalog.Compatibility.UNSUPPORTED_ABI -> Reason(
                    "This model needs a 64-bit processor.",
                )

                ModelCatalog.Compatibility.SUPPORTED -> Unit
            }

            if (entry.downloading) {
                LinearProgressIndicator(
                    progress = { entry.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Module.u),
                    color = HiVis,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Module.half),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${Format.size(entry.downloaded)} of ${Format.size(entry.model.downloadBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel) { Text("Stop") }
                }
                Text(
                    text = "Stopping keeps what has arrived; it resumes from there.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier.padding(top = Module.u),
                    horizontalArrangement = Arrangement.spacedBy(Module.u),
                ) {
                    when {
                        entry.installed && isLoaded ->
                            OutlinedButton(onClick = onUnload) { Text("Unload") }

                        entry.installed ->
                            Button(
                                onClick = onLoad,
                                colors = ButtonDefaults.buttonColors(containerColor = HiVis, contentColor = Ink),
                            ) { Text("Load") }

                        else ->
                            Button(
                                onClick = onDownload,
                                enabled = !incompatible,
                                colors = ButtonDefaults.buttonColors(containerColor = HiVis, contentColor = Ink),
                            ) { Text("Download") }
                    }
                    if (entry.installed) {
                        OutlinedButton(onClick = onDelete) { Text("Delete") }
                    }
                }
                if (!entry.installed && entry.downloaded.value > 0) {
                    Text(
                        text = "${Format.size(entry.downloaded)} already downloaded — it will resume.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Module.half),
                    )
                }
            }

            entry.error?.let { Reason(it, HazardRed) }

            Text(
                text = "Licence: ${entry.model.licence}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Module.u),
            )
        }
    }
}

@Composable
private fun Reason(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(top = Module.half),
    )
}
