package app.memi.widget

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
import app.memi.MainActivity
import app.memi.Note
import app.memi.NotesRepo
import app.memi.R
import app.memi.countLabel
import app.memi.Settings
import app.memi.ui.schemeFor
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProviders
import androidx.glance.color.colorProviders
import androidx.glance.color.ColorProvider as DayNightColor
import android.graphics.Paint
import android.util.TypedValue
import androidx.compose.ui.unit.Dp
import androidx.glance.LocalSize
import androidx.glance.layout.ContentScale
import app.memi.CapsuleSeg
import app.memi.FlowStyle
import app.memi.flowCapsules
import app.memi.ui.notePalette

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
            val palette = widgetNotePalette(context, theme, mode)
            GlanceTheme(colors = colors) { WidgetContent(context, notes, palette) }
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

/** The same vivid note colours as in the app, as day/night providers. */
private fun widgetNotePalette(context: Context, theme: Int, mode: Int): List<Pair<ColorProvider, ColorProvider>> {
    var light = notePalette(schemeFor(context, theme, dark = false).primary, dark = false)
    var dark = notePalette(schemeFor(context, theme, dark = true).primary, dark = true)
    if (mode == Settings.MODE_LIGHT) dark = light
    if (mode == Settings.MODE_DARK) light = dark
    return light.indices.map { i ->
        DayNightColor(light[i].first, dark[i].first) to DayNightColor(light[i].second, dark[i].second)
    }
}

private const val TEXT_SP = 13f
private val PIECE_H = 30.dp
private const val PIECE_PAD = 11f   // dp, horizontal padding inside a piece
private const val GAP = 5f          // dp, between capsules
private const val ICON = 14f        // dp
private const val ICON_GAP = 4f     // dp

@Composable
private fun WidgetContent(context: Context, notes: List<Note>, palette: List<Pair<ColorProvider, ColorProvider>>) {
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
                    context.getString(R.string.app_name),
                    style = TextStyle(color = c.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Text(
                    if (active == 0) context.getString(R.string.all_done) else countLabel(context, active),
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
                    contentDescription = context.getString(R.string.action_add),
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
                    context.getString(R.string.widget_empty),
                    style = TextStyle(color = c.onSurfaceVariant, fontSize = 14.sp),
                )
            }
        } else {
            // Same continuous flow as in the app: capsules go one after another and are cut at line ends
            val dm = context.resources.displayMetrics
            val density = dm.density
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXT_SP, dm)
            }
            val safety = 2f * density  // measurement slack so text is never clipped
            val widthDp = LocalSize.current.width.value - 14f - 10f - 4f
            val segs = flowCapsules(
                texts = notes.map { it.text },
                prefixes = notes.map { if (it.done || it.pinned) (ICON + ICON_GAP) * density else 0f },
                lineWidth = widthDp * density,
                style = FlowStyle(
                    hPad = PIECE_PAD * density, gap = GAP * density, minPiece = 32f * density,
                    maxPerLine = 10,
                ),
            ) { paint.measureText(it) + safety }
            val lines = segs.groupBy { it.line }.toSortedMap().values.toList()
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(lines.size) { li ->
                    Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        var end = 0f
                        lines[li].forEach { s ->
                            CapsulePiece(notes[s.index], s, palette, startGap = ((s.x - end) / density).dp, density)
                            end = s.x + s.width
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapsulePiece(
    note: Note,
    seg: CapsuleSeg,
    palette: List<Pair<ColorProvider, ColorProvider>>,
    startGap: Dp,
    density: Float,
) {
    val c = GlanceTheme.colors
    val (bg, fg) = if (note.done) c.widgetBackground to c.outline
    else palette[note.color.mod(palette.size)]
    val shape = when {
        seg.first && seg.last -> R.drawable.capsule_full
        seg.first -> R.drawable.capsule_start
        seg.last -> R.drawable.capsule_end
        else -> R.drawable.capsule_mid
    }
    // Outer box only adds the gap, so a piece is a single child of the row (widgets allow 10 per row)
    Box(modifier = GlanceModifier.padding(start = startGap)) {
        Row(
            modifier = GlanceModifier
                .width((seg.width / density).dp)
                .height(PIECE_H)
                .background(ImageProvider(shape), ContentScale.FillBounds, ColorFilter.tint(bg))
                .padding(horizontal = PIECE_PAD.dp)
                .clickable(actionRunCallback<ToggleNoteAction>(actionParametersOf(NoteIdKey to note.id))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (seg.first && (note.done || note.pinned)) {
                Image(
                    provider = ImageProvider(if (note.done) R.drawable.ic_check else R.drawable.ic_pin),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(fg),
                    modifier = GlanceModifier.size(ICON.dp),
                )
                Spacer(GlanceModifier.width(ICON_GAP.dp))
            }
            Text(
                seg.text,
                maxLines = 1,
                style = TextStyle(
                    color = fg,
                    fontSize = TEXT_SP.sp,
                    textDecoration = if (note.done) TextDecoration.LineThrough else TextDecoration.None,
                ),
            )
        }
    }
}
