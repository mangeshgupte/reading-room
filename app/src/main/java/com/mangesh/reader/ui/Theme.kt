package com.mangesh.reader.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.mangesh.reader.R

@Composable
fun currentPalette(vm: QueueViewModel): Palette = Palettes.resolve(vm.theme, isSystemInDarkTheme())

/** The palette in force, for the tokens Material's scheme has no slot for (secondary text, tints, sheet surface). */
val LocalPalette = staticCompositionLocalOf { Palettes.light }

fun Palette.color(v: Long) = Color(0xFF000000L or v)
val Palette.bgColor get() = color(bg)
val Palette.surfaceColor get() = color(surface)
val Palette.fgColor get() = color(fg)
val Palette.fg2Color get() = color(fg2)
val Palette.mutedColor get() = color(muted)
val Palette.ruleColor get() = color(rule)
val Palette.accentColor get() = color(accent)
val Palette.highlight get() = accentColor.copy(alpha = hlAlpha)
val Palette.pressTint get() = accentColor.copy(alpha = pressAlpha)

// Variable fonts (wght axis); the same files are served to the reading page. Headings are never bold.
@OptIn(ExperimentalTextApi::class)
private fun wght(w: Int) = FontVariation.Settings(FontVariation.weight(w))

@OptIn(ExperimentalTextApi::class)
val Playfair = FontFamily(
    Font(R.font.playfair_display, FontWeight.Normal, FontStyle.Normal, variationSettings = wght(400)),
    Font(R.font.playfair_display_italic, FontWeight.Normal, FontStyle.Italic, variationSettings = wght(400)),
)

@OptIn(ExperimentalTextApi::class)
val WorkSans = FontFamily(
    Font(R.font.work_sans, FontWeight.Normal, FontStyle.Normal, variationSettings = wght(400)),
    Font(R.font.work_sans, FontWeight.Medium, FontStyle.Normal, variationSettings = wght(500)),
    Font(R.font.work_sans_italic, FontWeight.Normal, FontStyle.Italic, variationSettings = wght(400)),
)

/** The handoff's named styles. Figures (percent, counts, dates) are tabular everywhere they stand alone. */
object Type {
    private const val TNUM = "tnum"
    val screenTitle = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 34.sp)
    val rowTitle = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.Normal, fontSize = 19.sp, lineHeight = 22.8.sp)
    val sheetTitle = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 27.sp)
    val scrubTitle = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 20.sp)
    val glyph = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.Normal, fontSize = 18.sp)
    val body = TextStyle(fontFamily = WorkSans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val excerpt = TextStyle(fontFamily = WorkSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp)
    val segment = TextStyle(fontFamily = WorkSans, fontWeight = FontWeight.Normal, fontSize = 14.sp)
    val figure = TextStyle(fontFamily = WorkSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp,
                           fontFeatureSettings = TNUM)
    /** Meta label: 12px, tracked, uppercase (callers uppercase the text), tabular. */
    val meta = figure.copy(letterSpacing = 0.06.em)
}

private val typography = Typography().let { t ->
    fun TextStyle.ui() = copy(fontFamily = WorkSans, fontWeight = FontWeight.Normal)
    fun TextStyle.heading() = copy(fontFamily = Playfair, fontWeight = FontWeight.Normal)
    Typography(
        displayLarge = t.displayLarge.heading(), displayMedium = t.displayMedium.heading(), displaySmall = t.displaySmall.heading(),
        headlineLarge = t.headlineLarge.heading(), headlineMedium = t.headlineMedium.heading(), headlineSmall = t.headlineSmall.heading(),
        titleLarge = t.titleLarge.heading(), titleMedium = Type.rowTitle, titleSmall = t.titleSmall.heading(),
        bodyLarge = Type.body, bodyMedium = Type.excerpt, bodySmall = Type.figure,
        labelLarge = t.labelLarge.ui(), labelMedium = t.labelMedium.ui(), labelSmall = Type.figure,
    )
}

/** The app's chrome takes the reading palette's colours so bars and tabs never clash with the page. */
@Composable
fun ReaderTheme(vm: QueueViewModel, content: @Composable () -> Unit) {
    val p = currentPalette(vm)
    val scheme = if (p.dark) darkColorScheme(
        background = p.bgColor, surface = p.bgColor, onBackground = p.fgColor, onSurface = p.fgColor,
        primary = p.accentColor, onPrimary = p.bgColor, error = p.accentColor, onSurfaceVariant = p.mutedColor,
        outline = p.ruleColor, outlineVariant = p.ruleColor, scrim = p.bgColor,
        surfaceVariant = p.surfaceColor, surfaceContainer = p.surfaceColor, surfaceContainerLow = p.surfaceColor,
        surfaceContainerHigh = p.surfaceColor, surfaceContainerHighest = p.surfaceColor, surfaceContainerLowest = p.surfaceColor,
        inverseSurface = p.fgColor, inverseOnSurface = p.bgColor, inversePrimary = p.accentColor,
    ) else lightColorScheme(
        background = p.bgColor, surface = p.bgColor, onBackground = p.fgColor, onSurface = p.fgColor,
        primary = p.accentColor, onPrimary = p.bgColor, error = p.accentColor, onSurfaceVariant = p.mutedColor,
        outline = p.ruleColor, outlineVariant = p.ruleColor, scrim = p.bgColor,
        surfaceVariant = p.surfaceColor, surfaceContainer = p.surfaceColor, surfaceContainerLow = p.surfaceColor,
        surfaceContainerHigh = p.surfaceColor, surfaceContainerHighest = p.surfaceColor, surfaceContainerLowest = p.surfaceColor,
        inverseSurface = p.fgColor, inverseOnSurface = p.bgColor, inversePrimary = p.accentColor,
    )

    // The theme can differ from the system's, so the bars' icons follow the palette, not the phone.
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as Activity).window
        @Suppress("DEPRECATION")   // no-ops where the system draws the bars edge to edge
        run { window.statusBarColor = p.bgArgb; window.navigationBarColor = p.bgArgb }
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !p.dark
            isAppearanceLightNavigationBars = !p.dark
        }
    }

    // selection handles and the caret take the accent, never the platform's blue
    val selection = TextSelectionColors(handleColor = p.accentColor, backgroundColor = p.highlight)
    CompositionLocalProvider(LocalPalette provides p, LocalTextSelectionColors provides selection) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
