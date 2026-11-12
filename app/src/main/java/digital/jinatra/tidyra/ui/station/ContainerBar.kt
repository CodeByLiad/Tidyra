package digital.jinatra.tidyra.ui.station

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import digital.jinatra.tidyra.core.model.Bytes
import digital.jinatra.tidyra.ui.format.Format
import digital.jinatra.tidyra.ui.format.SizeScale
import digital.jinatra.tidyra.ui.theme.LocalStationType
import digital.jinatra.tidyra.ui.theme.Module

/**
 * One enamel container.
 *
 * The signature of the whole app: a flat plate filled to this category's true share of the
 * device, at the shared scale, with a hatched span marking anything currently proposed for
 * removal. The bar is also the progress indicator — when a cleanup runs, the hatched span
 * retracts into the new fill rather than handing the job to a spinner.
 */
@Composable
fun ContainerBar(
    label: String,
    bytes: Bytes,
    color: Color,
    scale: SizeScale,
    modifier: Modifier = Modifier,
    proposedBytes: Bytes = Bytes.ZERO,
    animate: Boolean = true,
) {
    val fill = scale.drawnFraction(bytes)
    val proposed = scale.drawnFraction(proposedBytes).coerceAtMost(fill)

    val animatedFill by animateFloatAsState(
        targetValue = fill,
        label = "container-fill",
    )
    val animatedProposed by animateFloatAsState(
        targetValue = if (proposedBytes.value > 0) proposed else 0f,
        label = "container-proposed",
    )

    val fillFraction = if (animate) animatedFill else fill
    val proposedFraction = if (animate) animatedProposed else proposed

    val outline = MaterialTheme.colorScheme.outline
    val plate = MaterialTheme.colorScheme.surfaceContainer
    val onPlate = MaterialTheme.colorScheme.onSurface
    val type = LocalStationType.current

    // The label sits on whichever material is actually behind it: the fill when the bar
    // reaches under the text, the plate when it does not.
    val labelOnFill = fillFraction > 0.42f
    val labelColor = if (labelOnFill) {
        if (color.luminance() > 0.5f) Color(0xFF171614) else Color(0xFFF4F1EA)
    } else {
        onPlate
    }

    val description = buildString {
        append(label)
        append(", ")
        append(Format.spokenSize(bytes))
        if (proposedBytes.value > 0) {
            append(", ")
            append(Format.spokenSize(proposedBytes))
            append(" marked for removal")
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(plate)
            .border(1.dp, outline)
            .clipToBounds()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val fillWidth = size.width * fillFraction
            drawRect(color = color, size = androidx.compose.ui.geometry.Size(fillWidth, size.height))

            if (proposedFraction > 0f) {
                val hatchWidth = size.width * proposedFraction
                val hatchStart = (fillWidth - hatchWidth).coerceAtLeast(0f)

                // Diagonal hatching, drawn only inside the proposed span.
                clipRect(left = hatchStart, top = 0f, right = fillWidth, bottom = size.height) {
                    val step = 7.dp.toPx()
                    var x = hatchStart - size.height
                    while (x < fillWidth + size.height) {
                        drawLine(
                            color = Color(0x6B000000),
                            start = androidx.compose.ui.geometry.Offset(x, size.height),
                            end = androidx.compose.ui.geometry.Offset(x + size.height, 0f),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Butt,
                        )
                        x += step
                    }
                }
                drawLine(
                    color = Color(0xFF171614),
                    start = androidx.compose.ui.geometry.Offset(hatchStart, 0f),
                    end = androidx.compose.ui.geometry.Offset(hatchStart, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Module.x2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label.uppercase(),
                style = type.containerLabel,
                color = labelColor,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Format.size(bytes),
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor,
            )
        }
    }
}
