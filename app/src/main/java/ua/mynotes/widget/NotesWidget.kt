package ua.mynotes.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ua.mynotes.MainActivity
import ua.mynotes.Note
import ua.mynotes.NotesRepo
import ua.mynotes.R
import ua.mynotes.countLabel
import ua.mynotes.Settings
import ua.mynotes.ui.schemeFor
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProviders
import androidx.glance.color.colorProviders
import androidx.glance.color.ColorProvider as DayNightColor

class NotesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NotesWidget()
}

private val NoteIdKey = ActionParameters.Key<Long>("note_id")

class ToggleNoteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        NotesRepo.init(context)
        parameters[NoteIdKey]?.let { NotesRepo.toggle(it) }
    }
}

class NotesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        NotesRepo.init(context)
        Settings.init(context)
        provideContent {
            val notes by NotesRepo.notes.collectAsState()
            val theme by Settings.theme.collectAsState()
            val mode by Settings.mode.collectAsState()
            val useSystem = theme == 0 && mode == Settings.MODE_SYSTEM && Build.VERSION.SDK_INT >= 31
            val colors = if (useSystem) GlanceTheme.colors else widgetColors(context, theme, mode)
            GlanceTheme(colors = colors) { WidgetContent(context, notes) }
        }
    }
}

/** Day/night colour providers for a custom theme; background is a neutral surface container. */
private fun widgetColors(context: Context, theme: Int, mode: Int): ColorProviders {
    var light = schemeFor(context, theme, dark = false)
    var dark = schemeFor(context, theme, dark = true)
    if (mode == Settings.MODE_LIGHT) dark = light
    if (mode == Settings.MODE_DARK) light = dark
    fun p(f: (ColorScheme) -> Color) = DayNightColor(f(light), f(dark))
    return colorProviders(
        primary = p { it.primary }, onPrimary = p { it.onPrimary },
        primaryContainer = p { it.primaryContainer }, onPrimaryContainer = p { it.onPrimaryContainer },
        secondary = p { it.secondary }, onSecondary = p { it.onSecondary },
        secondaryContainer = p { it.secondaryContainer }, onSecondaryContainer = p { it.onSecondaryContainer },
        tertiary = p { it.tertiary }, onTertiary = p { it.onTertiary },
        tertiaryContainer = p { it.tertiaryContainer }, onTertiaryContainer = p { it.onTertiaryContainer },
        error = p { it.error }, errorContainer = p { it.errorContainer },
        onError = p { it.onError }, onErrorContainer = p { it.onErrorContainer },
        background = p { it.background }, onBackground = p { it.onBackground },
        surface = p { it.surface }, onSurface = p { it.onSurface },
        surfaceVariant = p { it.surfaceVariant }, onSurfaceVariant = p { it.onSurfaceVariant },
        outline = p { it.outline }, inverseOnSurface = p { it.inverseOnSurface },
        inverseSurface = p { it.inverseSurface }, inversePrimary = p { it.inversePrimary },
        widgetBackground = p { it.surfaceContainer },
    )
}

@Composable
private fun WidgetContent(context: Context, notes: List<Note>) {
    val c = GlanceTheme.colors
    val active = notes.count { !it.done }
    val addIntent = Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_ADD)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(c.widgetBackground)
            .cornerRadius(28.dp)
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = GlanceModifier.defaultWeight()
                    .clickable(actionStartActivity<MainActivity>())
            ) {
                Text(
                    "Замітки",
                    style = TextStyle(color = c.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Text(
                    if (active == 0) "Все зроблено" else countLabel(active),
                    style = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
            Box(
                modifier = GlanceModifier.size(44.dp)
                    .background(c.primary)
                    .cornerRadius(22.dp)
                    .clickable(actionStartActivity(addIntent)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_add),
                    contentDescription = "Додати",
                    colorFilter = ColorFilter.tint(c.onPrimary),
                    modifier = GlanceModifier.size(24.dp),
                )
            }
        }
        Spacer(GlanceModifier.height(10.dp))

        if (notes.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(addIntent)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Натисни +, щоб додати",
                    style = TextStyle(color = c.onSurfaceVariant, fontSize = 14.sp),
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(notes, itemId = { it.id }) { note ->
                    Column(modifier = GlanceModifier.fillMaxWidth().padding(end = 4.dp, bottom = 6.dp)) {
                        NotePill(note)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotePill(note: Note) {
    val c = GlanceTheme.colors
    val (bg, fg) = if (note.done) c.widgetBackground to c.outline else when (note.color) {
        0 -> c.primaryContainer to c.onPrimaryContainer
        1 -> c.tertiaryContainer to c.onTertiaryContainer
        2 -> c.secondaryContainer to c.onSecondaryContainer
        else -> c.errorContainer to c.onErrorContainer
    }
    Row(
        modifier = GlanceModifier.fillMaxWidth()
            .background(bg)
            .cornerRadius(20.dp)
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .clickable(actionRunCallback<ToggleNoteAction>(actionParametersOf(NoteIdKey to note.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (note.done || note.pinned) {
            Image(
                provider = ImageProvider(if (note.done) R.drawable.ic_check else R.drawable.ic_pin),
                contentDescription = null,
                colorFilter = ColorFilter.tint(fg),
                modifier = GlanceModifier.size(16.dp),
            )
            Spacer(GlanceModifier.width(6.dp))
        }
        Text(
            note.text,
            maxLines = 1,
            style = TextStyle(
                color = fg,
                fontSize = 15.sp,
                textDecoration = if (note.done) TextDecoration.LineThrough else TextDecoration.None,
            ),
        )
    }
}
