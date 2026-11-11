package app.memi.ui

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import app.memi.R

/** hue == null → Material You (colours from the wallpaper). */
data class AppTheme(@StringRes val name: Int, val hue: Float?)

val THEMES = listOf(
    AppTheme(R.string.theme_system, null),
    AppTheme(R.string.theme_lavender, 250f),
    AppTheme(R.string.theme_ocean, 205f),
    AppTheme(R.string.theme_mint, 165f),
    AppTheme(R.string.theme_forest, 110f),
    AppTheme(R.string.theme_sand, 38f),
    AppTheme(R.string.theme_coral, 12f),
    AppTheme(R.string.theme_raspberry, 335f),
)

fun schemeFor(ctx: Context, themeIndex: Int, dark: Boolean): ColorScheme {
    val theme = THEMES.getOrElse(themeIndex) { THEMES[0] }
    if (theme.hue == null && Build.VERSION.SDK_INT >= 31) {
        return if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    }
    return toneScheme(theme.hue ?: 250f, dark)
}

/**
 * Vivid ("acid") note colours: four hues derived from the theme's primary colour,
 * at full saturation, with dark text on top. Shared by the app and the widget.
 */
fun notePalette(primary: Color, dark: Boolean): List<Pair<Color, Color>> {
    val out = FloatArray(3)
    ColorUtils.colorToHSL(primary.toArgb(), out)
    val h = out[0]
    return listOf(h, h + 60f, h - 45f, h + 150f).map { hue ->
        if (!dark) hsl(hue, 1f, .70f) to hsl(hue, .85f, .12f)
        else hsl(hue, .95f, .64f) to hsl(hue, .90f, .09f)
    }
}

private fun hsl(h: Float, s: Float, l: Float): Color {
    val hue = ((h % 360f) + 360f) % 360f
    return Color(ColorUtils.HSLToColor(floatArrayOf(hue, s, l)))
}

/**
 * A Material-3-like scheme built from a single hue.
 * Note colours use the four containers: primary (h), tertiary (h+60),
 * secondary (h-45) and "error" container (h+150) — so they stay distinct.
 */
private fun toneScheme(h: Float, dark: Boolean): ColorScheme {
    val sec = h - 45f
    val ter = h + 60f
    val acc = h + 150f
    return if (!dark) lightColorScheme(
        primary = hsl(h, .50f, .42f), onPrimary = Color.White,
        primaryContainer = hsl(h, .80f, .90f), onPrimaryContainer = hsl(h, .60f, .16f),
        inversePrimary = hsl(h, .70f, .80f),
        secondary = hsl(sec, .25f, .40f), onSecondary = Color.White,
        secondaryContainer = hsl(sec, .60f, .88f), onSecondaryContainer = hsl(sec, .55f, .15f),
        tertiary = hsl(ter, .40f, .40f), onTertiary = Color.White,
        tertiaryContainer = hsl(ter, .75f, .90f), onTertiaryContainer = hsl(ter, .60f, .15f),
        error = Color(0xFFBA1A1A), onError = Color.White,
        errorContainer = hsl(acc, .75f, .89f), onErrorContainer = hsl(acc, .60f, .15f),
        background = hsl(h, .40f, .985f), onBackground = hsl(h, .12f, .11f),
        surface = hsl(h, .40f, .985f), onSurface = hsl(h, .12f, .11f),
        surfaceVariant = hsl(h, .22f, .90f), onSurfaceVariant = hsl(h, .10f, .30f),
        surfaceTint = hsl(h, .50f, .42f),
        inverseSurface = hsl(h, .10f, .19f), inverseOnSurface = hsl(h, .35f, .95f),
        outline = hsl(h, .08f, .48f), outlineVariant = hsl(h, .15f, .80f),
        surfaceBright = hsl(h, .40f, .985f), surfaceDim = hsl(h, .18f, .87f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = hsl(h, .35f, .965f),
        surfaceContainer = hsl(h, .30f, .945f),
        surfaceContainerHigh = hsl(h, .26f, .925f),
        surfaceContainerHighest = hsl(h, .22f, .905f),
    ) else darkColorScheme(
        primary = hsl(h, .75f, .80f), onPrimary = hsl(h, .60f, .20f),
        primaryContainer = hsl(h, .40f, .30f), onPrimaryContainer = hsl(h, .80f, .91f),
        inversePrimary = hsl(h, .50f, .42f),
        secondary = hsl(sec, .30f, .78f), onSecondary = hsl(sec, .30f, .18f),
        secondaryContainer = hsl(sec, .35f, .27f), onSecondaryContainer = hsl(sec, .60f, .90f),
        tertiary = hsl(ter, .50f, .78f), onTertiary = hsl(ter, .50f, .18f),
        tertiaryContainer = hsl(ter, .40f, .29f), onTertiaryContainer = hsl(ter, .75f, .91f),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = hsl(acc, .40f, .28f), onErrorContainer = hsl(acc, .75f, .90f),
        background = hsl(h, .18f, .075f), onBackground = hsl(h, .18f, .90f),
        surface = hsl(h, .18f, .075f), onSurface = hsl(h, .18f, .90f),
        surfaceVariant = hsl(h, .12f, .28f), onSurfaceVariant = hsl(h, .15f, .80f),
        surfaceTint = hsl(h, .75f, .80f),
        inverseSurface = hsl(h, .18f, .90f), inverseOnSurface = hsl(h, .12f, .19f),
        outline = hsl(h, .08f, .58f), outlineVariant = hsl(h, .10f, .30f),
        surfaceBright = hsl(h, .12f, .24f), surfaceDim = hsl(h, .18f, .075f),
        surfaceContainerLowest = hsl(h, .20f, .05f),
        surfaceContainerLow = hsl(h, .16f, .10f),
        surfaceContainer = hsl(h, .15f, .12f),
        surfaceContainerHigh = hsl(h, .13f, .15f),
        surfaceContainerHighest = hsl(h, .12f, .19f),
    )
}
