package digital.jinatra.tidyra.ui.model

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import digital.jinatra.tidyra.ai.ModelCatalog
import digital.jinatra.tidyra.ui.format.Format
import digital.jinatra.tidyra.ui.theme.Crimson
import digital.jinatra.tidyra.ui.theme.LocalStationType
import digital.jinatra.tidyra.ui.theme.Module
import digital.jinatra.tidyra.ui.theme.Olive

/**
 * The model depot, set like the rest of the paper: serif names, hairline rules, one
 * crimson accent, no boxes.
 *
 * Nothing here is required — every screen in Tidyra works with no model installed. What a
 * model adds is explanation, computed on this phone and sent nowhere.
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
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = Module.x4),
    ) {
        item {
            Column(Modifier.padding(start = Module.x3, end = Module.x3, top = Module.x2, bottom = Module.x2)) {
                Text(
                    text = "Gemma runs on this phone. Your file names, sizes and folders are never uploaded — the download below is the only network request Tidyra ever makes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = colors.outline)
        }

        items(state.entries, key = { it.model.id }) { entry ->
            ModelRow(
                entry = entry,
                isLoaded = state.loadedModelPath?.endsWith(entry.model.fileName) == true,
                loading = state.loading,
                onDownload = { onDownload(entry.model) },
                onCancel = onCancel,
                onDelete = { onDelete(entry.model) },
                onLoad = { onLoad(entry.model) },
                onUnload = onUnload,
            )
        }

        item {
            Column(Modifier.padding(horizontal = Module.x3, vertical = Module.x2)) {
                Text(
                    text = "Hugging Face token",
                    style = type.section,
                    color = colors.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "Google serves Gemma behind a licence you accept on huggingface.co. Paste a read token; it stays on this device and is sent only to huggingface.co.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = Module.half, bottom = Module.x2),
                )
                OutlinedTextField(
                    value = state.token,
                    onValueChange = onToken,
                    placeholder = { Text("hf_...", color = colors.onSurfaceVariant) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = colors.outline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = onImport,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.padding(top = Module.u),
                ) {
                    Text("IMPORT A .TASK FILE INSTEAD", style = type.stencil, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    text = "Models live in ${ModelCatalog.SHARED_DIR} when Tidyra has all-files access, so one download serves every Jinatra app on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = Module.x2),
                )
            }
        }

        state.message?.let { message ->
            item {
                HorizontalDivider(color = colors.outline)
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    modifier = Modifier.padding(horizontal = Module.x3, vertical = Module.x2),
                )
            }
        }
    }
}

@Composable
private fun ModelRow(
    entry: ModelState.Entry,
    isLoaded: Boolean,
    loading: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
) {
    val type = LocalStationType.current
    val colors = MaterialTheme.colorScheme
    val incompatible = entry.compatibility != ModelCatalog.Compatibility.SUPPORTED

    Column {
        Column(Modifier.padding(horizontal = Module.x3, vertical = Module.x2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.model.name,
                    style = type.section,
                    color = if (incompatible) colors.onSurfaceVariant else colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (isLoaded) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Olive))
                    Text(
                        text = "RUNNING",
                        style = type.stencil,
                        color = Olive,
                        modifier = Modifier.padding(start = Module.u),
                    )
                }
            }

            Text(
                text = buildString {
                    append(entry.model.parameters)
                    append(" | ")
                    append(Format.size(entry.model.downloadBytes))
                    if (entry.model.multimodal) append(" | reads images")
                    if (entry.installed) append(" | installed")
                },
                style = type.stencil,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = Module.half),
            )

            Text(
                text = entry.model.description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = Module.u),
            )

            when (entry.compatibility) {
                ModelCatalog.Compatibility.LOW_MEMORY -> Reason(
                    "This phone has less memory than this model needs (${entry.model.requiredRamGb} GB). It would load, then be killed.",
                )

                ModelCatalog.Compatibility.UNSUPPORTED_ABI -> Reason("This model needs a 64-bit processor.")
                ModelCatalog.Compatibility.SUPPORTED -> Unit
            }

            if (entry.downloading) {
                LinearProgressIndicator(
                    progress = { entry.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Module.x2)
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = colors.surfaceContainerHighest,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Module.u),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${Format.size(entry.downloaded)} of ${Format.size(entry.model.downloadBytes)} — stopping keeps what arrived",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel, contentPadding = PaddingValues(0.dp)) {
                        Text("STOP", style = type.stencil, color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Module.x2),
                    horizontalArrangement = Arrangement.spacedBy(Module.x2),
                ) {
                    val primaryLabel = when {
                        entry.installed && isLoaded -> "UNLOAD"
                        entry.installed -> if (loading) "LOADING" else "LOAD"
                        incompatible -> "NOT SUPPORTED"
                        else -> "DOWNLOAD"
                    }
                    val primaryAction = when {
                        entry.installed && isLoaded -> onUnload
                        entry.installed -> onLoad
                        incompatible -> ({})
                        else -> onDownload
                    }
                    Button(
                        onClick = primaryAction,
                        enabled = !incompatible && !loading,
                        shape = RoundedCornerShape(Module.half),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLoaded) colors.surfaceContainerHigh else MaterialTheme.colorScheme.primary,
                            contentColor = if (isLoaded) colors.onSurface else colors.onPrimary,
                            disabledContainerColor = colors.surfaceContainerHigh,
                            disabledContentColor = colors.onSurfaceVariant,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) {
                        Text(primaryLabel, style = type.amount)
                    }
                    if (entry.installed) {
                        OutlinedButton(
                            onClick = onDelete,
                            shape = RoundedCornerShape(Module.half),
                            border = BorderStroke(1.dp, colors.outline),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurfaceVariant),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                        ) {
                            Text("DELETE", style = type.amount)
                        }
                    }
                }
                if (!entry.installed && entry.downloaded.value > 0) {
                    Text(
                        text = "${Format.size(entry.downloaded)} already downloaded — it resumes from there.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = Module.u),
                    )
                }
            }

            entry.error?.let { Reason(it, MaterialTheme.colorScheme.primary) }

            Text(
                text = "Licence: ${entry.model.licence}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = Module.x2),
            )
        }
        HorizontalDivider(color = colors.outline)
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit, color: Color) {
    val type = LocalStationType.current
    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Text(label, style = type.stencil, color = color)
    }
}

@Composable
private fun Reason(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(top = Module.u),
    )
}
