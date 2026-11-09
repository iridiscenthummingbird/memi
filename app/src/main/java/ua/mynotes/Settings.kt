package ua.mynotes

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** User preferences that affect how the app and the widget look. */
object Settings {
    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    private const val PREFS = "settings"
    private val _theme = MutableStateFlow(0)
    private val _mode = MutableStateFlow(MODE_SYSTEM)
    val theme: StateFlow<Int> = _theme
    val mode: StateFlow<Int> = _mode
    private var loaded = false

    fun init(ctx: Context) {
        if (loaded) return
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _theme.value = p.getInt("theme", 0)
        _mode.value = p.getInt("mode", MODE_SYSTEM)
        loaded = true
    }

    fun setTheme(ctx: Context, index: Int) {
        _theme.value = index
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("theme", index).apply()
        NotesRepo.onChanged()
    }

    fun setMode(ctx: Context, mode: Int) {
        _mode.value = mode
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("mode", mode).apply()
        NotesRepo.onChanged()
    }
}
