package digital.jinatra.tidyra.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's few marks, drawn rather than typed.
 *
 * A glyph borrowed from the text font is not an icon: set in Playfair, an arrow arrives as
 * a thin double chevron that matches nothing else on the page. These are authored at one
 * stroke weight so a tick, a dash and a chevron look like siblings.
 */
private const val STROKE_RATIO = 0.12f

@Composable
fun CheckMark(color: Color, size: Dp = 12.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val s = w * STROKE_RATIO * 1.6f
        drawLine(
            color = color,
            start = Offset(w * 0.20f, w * 0.52f),
            end = Offset(w * 0.42f, w * 0.75f),
            strokeWidth = s,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.42f, w * 0.75f),
            end = Offset(w * 0.82f, w * 0.26f),
            strokeWidth = s,
            cap = StrokeCap.Round,
        )
    }
}

/** Partial selection: some of this group is picked, not all of it. */
@Composable
fun DashMark(color: Color, size: Dp = 12.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        drawLine(
            color = color,
            start = Offset(w * 0.24f, w * 0.5f),
            end = Offset(w * 0.76f, w * 0.5f),
            strokeWidth = w * STROKE_RATIO * 1.6f,
            cap = StrokeCap.Round,
        )
    }
}

/** "There is more behind this row." */
@Composable
fun ChevronMark(color: Color, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val s = w * STROKE_RATIO
        drawLine(
            color = color,
            start = Offset(w * 0.38f, w * 0.26f),
            end = Offset(w * 0.64f, w * 0.5f),
            strokeWidth = s,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.64f, w * 0.5f),
            end = Offset(w * 0.38f, w * 0.74f),
            strokeWidth = s,
            cap = StrokeCap.Round,
        )
    }
}

/** An empty selection state must read as empty: an outline, never a filled grey disc. */
@Composable
fun SelectionRing(
    selected: Boolean,
    partial: Boolean,
    ringColor: Color,
    fillColor: Color,
    onFillColor: Color,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2
            if (selected) {
                drawCircle(color = fillColor, radius = r)
            } else {
                drawCircle(
                    color = ringColor,
                    radius = r - 1.dp.toPx(),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
        if (selected) {
            if (partial) {
                DashMark(onFillColor, size * 0.7f, Modifier.align(androidx.compose.ui.Alignment.Center))
            } else {
                CheckMark(onFillColor, size * 0.7f, Modifier.align(androidx.compose.ui.Alignment.Center))
            }
        }
    }
}

/** "Back to where this came from." The mirror of [ChevronMark], at the same weight. */
@Composable
fun ChevronLeftMark(color: Color, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val s = w * STROKE_RATIO
        drawLine(
            color = color,
            start = Offset(w * 0.62f, w * 0.26f),
            end = Offset(w * 0.36f, w * 0.5f),
            strokeWidth = s,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.36f, w * 0.5f),
            end = Offset(w * 0.62f, w * 0.74f),
            strokeWidth = s,
            cap = StrokeCap.Round,
        )
    }
}
