package digital.jinatra.tidyra.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import digital.jinatra.tidyra.R

/*
 * Editorial-minimal.
 *
 * Warm paper ground, a high-contrast serif carrying the numbers, one crimson accent, and
 * hairline rules doing the work that boxes and shadows usually do. Nothing is enclosed in
 * a card: sections are separated by a 1px rule and generous space, the way a printed page
 * separates them.
 *
 * Dynamic colour is deliberately not used — crimson is the only accent, and a
 * wallpaper-derived scheme would replace it with something arbitrary.
 */

// -- Materials -------------------------------------------------------------------------

val Paper = Color(0xFFF7F4EF)
val PaperNight = Color(0xFF12100E)
val Ink = Color(0xFF15110E)
val InkNight = Color(0xFFF2EDE6)
val Muted = Color(0xFF6B635A)
val MutedNight = Color(0xFF9A9188)
val Crimson = Color(0xFFB3122E)
val CrimsonNight = Color(0xFFE2546B)
val Rule = Color(0xFFE2DCD2)
val RuleNight = Color(0xFF2A2724)
val Olive = Color(0xFF4C6B4F)
val Amber = Color(0xFF9A6B14)

private val LightScheme = lightColorScheme(
    primary = Crimson,
    onPrimary = Paper,
    primaryContainer = Color(0xFFF6DCE0),
    onPrimaryContainer = Color(0xFF5C0917),
    secondary = Ink,
    onSecondary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceContainerLowest = Color(0xFFFFFDFA),
    surfaceContainerLow = Paper,
    surfaceContainer = Color(0xFFF2EEE7),
    surfaceContainerHigh = Color(0xFFEDE8E0),
    surfaceContainerHighest = Rule,
    surfaceVariant = Color(0xFFEDE8E0),
    onSurfaceVariant = Muted,
    outline = Rule,
    outlineVariant = Color(0xFFEDE8E0),
    error = Crimson,
    onError = Paper,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
)

private val DarkScheme = darkColorScheme(
    primary = CrimsonNight,
    onPrimary = Color(0xFF2B0309),
    primaryContainer = Color(0xFF4A0A16),
    onPrimaryContainer = Color(0xFFF6DCE0),
    secondary = InkNight,
    onSecondary = PaperNight,
    background = PaperNight,
    onBackground = InkNight,
    surface = PaperNight,
    onSurface = InkNight,
    surfaceContainerLowest = Color(0xFF0B0A09),
    surfaceContainerLow = Color(0xFF17150F),
    surfaceContainer = Color(0xFF1B1916),
    surfaceContainerHigh = Color(0xFF23201C),
    surfaceContainerHighest = RuleNight,
    surfaceVariant = Color(0xFF23201C),
    onSurfaceVariant = MutedNight,
    outline = RuleNight,
    outlineVariant = Color(0xFF23201C),
    error = CrimsonNight,
    onError = Color(0xFF2B0309),
    inverseSurface = InkNight,
    inverseOnSurface = PaperNight,
)

// -- Type ------------------------------------------------------------------------------

/** Playfair Display, bundled under the OFL. The display voice: headlines and every figure. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Editorial = FontFamily(
    Font(
        R.font.playfair_display,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.playfair_display,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.playfair_display,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.playfair_display,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

private val base = Typography()

private val TidyraTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Editorial, fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em),
    displayMedium = base.displayMedium.copy(fontFamily = Editorial, fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em),
    displaySmall = base.displaySmall.copy(fontFamily = Editorial, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Editorial, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Editorial, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Editorial, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Editorial, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
)

/** Styles the Material scale has no role for. Field names kept stable across redesigns. */
data class StationType(
    /** The masthead. Serif, tight, like a paper's name. */
    val wordmark: TextStyle = TextStyle(
        fontFamily = Editorial,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.02).em,
    ),
    /** The hero figure: the one number the screen is about. */
    val figure: TextStyle = TextStyle(
        fontFamily = Editorial,
        fontWeight = FontWeight.Bold,
        fontSize = 64.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.04).em,
    ),
    /** A statistic in a column of statistics. */
    val containerLabel: TextStyle = TextStyle(
        fontFamily = Editorial,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = (-0.02).em,
    ),
    /** Small tracked caps: the only sans voice that shouts. */
    val stencil: TextStyle = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.14.em,
    ),
    /** A value at the end of a row. */
    val amount: TextStyle = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    /** Section headings, serif, set like a magazine's. */
    val section: TextStyle = TextStyle(
        fontFamily = Editorial,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = (-0.02).em,
    ),
)

val LocalStationType = staticCompositionLocalOf { StationType() }

/** The single 8dp module. Every gap is a multiple of it; nothing sits off-grid. */
object Module {
    val u = 8.dp
    val half = 4.dp
    val x2 = 16.dp
    val x3 = 24.dp
    val x4 = 32.dp
}

@Composable
fun TidyraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalStationType provides StationType()) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = TidyraTypography,
            content = content,
        )
    }
}
