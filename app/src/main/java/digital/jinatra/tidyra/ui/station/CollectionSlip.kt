package digital.jinatra.tidyra.ui.station

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import digital.jinatra.tidyra.core.model.Finding
import digital.jinatra.tidyra.core.model.Risk
import digital.jinatra.tidyra.ui.format.Format
import digital.jinatra.tidyra.ui.theme.HazardRed
import digital.jinatra.tidyra.ui.theme.HiVis
import digital.jinatra.tidyra.ui.theme.LocalStationType
import digital.jinatra.tidyra.ui.theme.Module
import digital.jinatra.tidyra.ui.theme.SignalAmber

/**
 * One proposal, as a collection slip.
 *
 * Carries what it frees and what it costs in the same glance. The top rule is colour-coded
 * by risk, and because colour alone is never the signal, the risk is also spelled out in
 * the stencil line.
 */
@Composable
fun CollectionSlip(
    finding: Finding,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalStationType.current
    val riskColor = when (finding.risk) {
        Risk.SAFE -> HiVis
        Risk.REVIEW -> SignalAmber
        Risk.PERSONAL -> HazardRed
    }
    val riskWord = when (finding.risk) {
        Risk.SAFE -> "SAFE"
        Risk.REVIEW -> "YOUR CALL"
        Risk.PERSONAL -> "PERSONAL — BACK UP FIRST"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                onValueChange = onToggle,
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(riskColor),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Module.u, end = Module.x2, top = Module.u + 2.dp, bottom = Module.u + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = null)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Module.u),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = finding.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (finding.overlaps.isNotEmpty()) {
                        InterchangeMark()
                        Box(Modifier.width(Module.half))
                    }
                    Text(
                        text = finding.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = riskWord,
                    style = type.stencil,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (finding.frees.value > 0) Format.size(finding.frees) else "TIDIES",
                    style = type.amount,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = Format.count(finding.items.size, "item"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The interchange mark: this finding shares items with another one, so the bytes are
 * counted once across the whole plan. Borrowed discipline from a transit diagram's
 * insistence that every crossing is drawn as one deliberate junction.
 */
@Composable
private fun InterchangeMark(modifier: Modifier = Modifier) {
    val stroke = MaterialTheme.colorScheme.onSurface
    val fill = MaterialTheme.colorScheme.surfaceContainer
    Box(
        modifier
            .size(10.dp)
            .drawBehind {
                drawCircle(color = fill, radius = size.minDimension / 2)
                drawCircle(
                    color = stroke,
                    radius = size.minDimension / 2 - 1.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx()),
                    center = Offset(size.width / 2, size.height / 2),
                )
            },
    )
}
