package ua.mynotes.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.mynotes.Settings

@Composable
fun isDarkTheme(): Boolean {
    val mode by Settings.mode.collectAsStateWithLifecycle()
    return when (mode) {
        Settings.MODE_LIGHT -> false
        Settings.MODE_DARK -> true
        else -> isSystemInDarkTheme()
    }
}

@Composable
fun NotesTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val theme by Settings.theme.collectAsStateWithLifecycle()
    val dark = isDarkTheme()
    val scheme = remember(theme, dark) { schemeFor(ctx, theme, dark) }

    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Background/foreground for a note colour index. Same mapping as in the widget. */
@Composable
fun noteColors(index: Int): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    return when (index) {
        0 -> c.primaryContainer to c.onPrimaryContainer
        1 -> c.tertiaryContainer to c.onTertiaryContainer
        2 -> c.secondaryContainer to c.onSecondaryContainer
        else -> c.errorContainer to c.onErrorContainer
    }
}
