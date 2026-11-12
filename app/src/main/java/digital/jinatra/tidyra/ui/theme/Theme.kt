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
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * The Sorting Station world.
 *
 * Municipal waste-sorting signage: hi-vis yellow on poured concrete, flat enamel plates
 * with hairline outlines, stencil-condensed caps for labels, and a fixed container
 * palette that codes category the same way on every screen.
 *
 * Dynamic colour is deliberately not used. The container colours are the data encoding —
 * paper-blue means app data wherever it appears — and a wallpaper-derived scheme would
 * recolour the one thing in this app that has to stay constant.
 */

// -- The world's materials -------------------------------------------------------------

val HiVis = Color(0xFFFFC400)
val HiVisDim = Color(0xFFE0AC00)
val Ink = Color(0xFF171614)
val InkSoft = Color(0xFF3A3733)
val Concrete = Color(0xFFE8E3D9)
val Plate = Color(0xFFF4F1EA)
val PlateEdge = Color(0xFFCFC9BC)
val Asphalt = Color(0xFF121110)
val PlateNight = Color(0xFF1F1D1A)
val PlateEdgeNight = Color(0xFF3A3733)
val Bone = Color(0xFFF4F1EA)
val Graphite = Color(0xFF5C574E)
val GraphiteNight = Color(0xFFA9A299)

/** Container colours. Fixed in both themes: this is the data encoding, not decoration. */
val PaperBlue = Color(0xFF1C5FA8)
val GlassGreen = Color(0xFF1E7A4B)
val MetalGrey = Color(0xFF6E7378)
val OrganicBrown = Color(0xFF7A5230)
val HazardRed = Color(0xFFC8321E)
val SignalAmber = Color(0xFFB7791F)

private val LightScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Bone,
    primaryContainer = HiVis,
    onPrimaryContainer = Ink,
    secondary = PaperBlue,
    onSecondary = Bone,
    secondaryContainer = Color(0xFFD3E0F0),
    onSecondaryContainer = Color(0xFF0E2E52),
    tertiary = GlassGreen,
    onTertiary = Bone,
    background = Concrete,
    onBackground = Ink,
    surface = Concrete,
    onSurface = Ink,
    surfaceContainerLowest = Plate,
    surfaceContainerLow = Plate,
    surfaceContainer = Plate,
    surfaceContainerHigh = Color(0xFFEDE9E1),
    surfaceContainerHighest = Color(0xFFE2DDD2),
    surfaceVariant = Color(0xFFDDD7CA),
    onSurfaceVariant = Graphite,
    outline = PlateEdge,
    outlineVariant = Color(0xFFDCD6C8),
    error = HazardRed,
    onError = Bone,
    errorContainer = Color(0xFFF7DAD5),
    onErrorContainer = Color(0xFF5E1407),
    inverseSurface = Ink,
    inverseOnSurface = Bone,
    inversePrimary = HiVis,
)

private val DarkScheme = darkColorScheme(
    primary = HiVis,
    onPrimary = Ink,
    primaryContainer = HiVisDim,
    onPrimaryContainer = Ink,
    secondary = Color(0xFF7FB1E8),
    onSecondary = Color(0xFF0E2E52),
    secondaryContainer = Color(0xFF1B3C60),
    onSecondaryContainer = Color(0xFFD3E0F0),
    tertiary = Color(0xFF6ECB99),
    onTertiary = Color(0xFF07301B),
    background = Asphalt,
    onBackground = Bone,
    surface = Asphalt,
    onSurface = Bone,
    surfaceContainerLowest = Color(0xFF0C0B0A),
    surfaceContainerLow = PlateNight,
    surfaceContainer = PlateNight,
    surfaceContainerHigh = Color(0xFF282521),
    surfaceContainerHighest = Color(0xFF322E29),
    surfaceVariant = Color(0xFF2A2724),
    onSurfaceVariant = GraphiteNight,
    outline = PlateEdgeNight,
    outlineVariant = Color(0xFF2A2724),
    error = Color(0xFFE8705C),
    onError = Color(0xFF3B0C03),
    errorContainer = Color(0xFF5E1407),
    onErrorContainer = Color(0xFFF7DAD5),
    inverseSurface = Bone,
    inverseOnSurface = Ink,
    inversePrimary = Ink,
)

// -- Type ------------------------------------------------------------------------------

/**
 * Signage caps. The condensed width of the system face, so the app carries no font files
 * and still gets the compression that makes a label read as signage rather than as prose.
 */
val Signage = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Medium),
    Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Bold),
)

private val base = Typography()

private val TidyraTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Signage, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
    displayMedium = base.displayMedium.copy(fontFamily = Signage, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
    displaySmall = base.displaySmall.copy(fontFamily = Signage, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Signage, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Signage, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
    labelLarge = base.labelLarge.copy(fontFamily = Signage, fontWeight = FontWeight.Bold, letterSpacing = 0.1.em),
    labelMedium = base.labelMedium.copy(fontFamily = Signage, fontWeight = FontWeight.Bold, letterSpacing = 0.12.em),
    labelSmall = base.labelSmall.copy(fontFamily = Signage, fontWeight = FontWeight.Bold, letterSpacing = 0.14.em),
)

/** The station's own styles, for the places the Material scale has no role for. */
data class StationType(
    val wordmark: TextStyle = TextStyle(
        fontFamily = Signage,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 0.22.em,
    ),
    val figure: TextStyle = TextStyle(
        fontFamily = Signage,
        fontWeight = FontWeight.Bold,
        fontSize = 46.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.01).em,
    ),
    val containerLabel: TextStyle = TextStyle(
        fontFamily = Signage,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 0.1.em,
    ),
    val stencil: TextStyle = TextStyle(
        fontFamily = Signage,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.14.em,
    ),
    val amount: TextStyle = TextStyle(
        fontFamily = Signage,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        textAlign = TextAlign.End,
    ),
)

val LocalStationType = staticCompositionLocalOf { StationType() }

/** The single 8dp module. Every gap in the app is a multiple of this and nothing is off-grid. */
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
