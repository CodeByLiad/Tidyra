package digital.jinatra.tidyra.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import digital.jinatra.tidyra.core.scan.AppInventory
import digital.jinatra.tidyra.core.shizuku.ShizukuBridge
import digital.jinatra.tidyra.ui.common.AppIcon
import digital.jinatra.tidyra.ui.format.Format
import digital.jinatra.tidyra.ui.theme.LocalStationType
import digital.jinatra.tidyra.ui.theme.Module

/**
 * Every app, largest first, with what it holds and when it was last opened.
 *
 * No checkboxes and no bulk actions: uninstalling goes through Android's own dialog, one
 * app at a time, which is both the platform's rule and the right amount of friction for
 * something irreversible.
 */
@Composable
fun AppsScreen(
    apps: List<AppInventory.AppEntry>,
    hasUsageAccess: Boolean,
    shizuku: ShizukuBridge.State,
    onUninstall: (String) -> Unit,
    onOpenAppSettings: (String) -> Unit,
    onClearAllCaches: () -> Unit,
    onGrantUsage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalStationType.current
    val colors = MaterialTheme.colorScheme
    val thirdParty = apps.filterNot { it.isSystem }
    // Count what the list shows. A headline measuring every package on the phone,
    // over a list of the ones you installed, is two numbers the reader cannot reconcile.
    val cacheTotal = thirdParty.sumOf { it.cacheBytes.value }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = Module.x4),
    ) {
        item {
            Column(
                Modifier.padding(start = Module.x3, end = Module.x3, top = Module.x2, bottom = Module.u),
            ) {
                Text(
                    // Code, data and cache in one figure. The Storage tab splits the
                    // same bytes into two categories, so without this line the reader is
                    // handed two different totals for "apps" and no way to reconcile them.
                    text = if (hasUsageAccess) {
                        "${Format.count(thirdParty.size, "app")} installed, holding ${Format.size(digital.jinatra.tidyra.core.model.Bytes(thirdParty.sumOf { it.totalBytes.value }))}" +
                            " \u2014 app code, data and cache together. Storage lists code and data separately."
                    } else {
                        "Usage access is off, so sizes are hidden"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = colors.outline)
        }

        if (!hasUsageAccess) {
            item {
                TextButton(onClick = onGrantUsage, modifier = Modifier.padding(Module.x3)) {
                    Text("GRANT USAGE ACCESS", style = type.stencil, color = MaterialTheme.colorScheme.primary)
                }
            }
            return@LazyColumn
        }

        if (cacheTotal > 0) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Module.x3, vertical = Module.x2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "${Format.size(digital.jinatra.tidyra.core.model.Bytes(cacheTotal))} of cache",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.onSurface,
                        )
                        Text(
                            text = if (shizuku == ShizukuBridge.State.READY) {
                                "Shizuku is paired, so this clears in one tap. Nothing is lost — not logins, not downloads."
                            } else {
                                "Android blocks this without Shizuku. Tap an app below to open its storage screen instead."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = Module.half),
                        )
                    }
                    if (shizuku == ShizukuBridge.State.READY) {
                        TextButton(onClick = onClearAllCaches) {
                            Text("CLEAR", style = type.stencil, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                HorizontalDivider(color = colors.outline)
            }
        }

        items(thirdParty, key = { it.packageName }) { app ->
            AppRow(app, onOpenAppSettings, onUninstall)
        }
    }
}

@Composable
private fun AppRow(
    app: AppInventory.AppEntry,
    onOpenAppSettings: (String) -> Unit,
    onUninstall: (String) -> Unit,
) {
    val type = LocalStationType.current
    val colors = MaterialTheme.colorScheme
    val stale = app.daysSinceUse >= 90

    Column(Modifier.clickable { onOpenAppSettings(app.packageName) }) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Module.x3, vertical = Module.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.packageName)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = Module.x2),
            ) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(Format.days(app.daysSinceUse))
                        if (app.cacheBytes.value > 0) {
                            append(" · ")
                            append(Format.size(app.cacheBytes))
                            append(" cache")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (stale) MaterialTheme.colorScheme.primary else colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = Format.size(app.totalBytes),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
                TextButton(
                    onClick = { onUninstall(app.packageName) },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("UNINSTALL", style = type.stencil, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
    }
}
