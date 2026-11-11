package app.memi

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import app.memi.widget.NotesWidget
import java.io.File
import java.io.FileNotFoundException

data class Note(
    val id: Long,
    val text: String,
    val color: Int,
    val pinned: Boolean = false,
    val done: Boolean = false,
    val created: Long = id,
    /** Manual position set by drag & drop; new notes go to the end. */
    val order: Long = created,
)

const val NOTE_COLORS = 4

private const val TAG = "NotesRepo"

/** Single source of truth for notes, shared by the app, the widget and the lock-screen notification. */
object NotesRepo {
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes

    private val _saveErrors = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits when notes could not be written to disk (the in-memory state is still updated). */
    val saveErrors: SharedFlow<Unit> = _saveErrors

    private lateinit var app: Context
    private var loaded = false
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshJob: Job? = null

    private val file get() = AtomicFile(File(app.filesDir, "notes.json"))

    fun init(context: Context) = synchronized(lock) {
        if (loaded) return
        app = context.applicationContext
        _notes.value = sortNotes(load())
        loaded = true
    }

    fun add(text: String) = update { addNote(it, text, System.currentTimeMillis()) }

    fun toggle(id: Long) = update { l -> l.map { if (it.id == id) it.copy(done = !it.done) else it } }

    fun edit(id: Long, text: String, color: Int, pinned: Boolean) {
        val t = cleanText(text)
        if (t.isEmpty()) return
        update { l -> l.map { if (it.id == id) it.copy(text = t, color = color, pinned = pinned) else it } }
    }

    fun delete(id: Long): Note? {
        val n = _notes.value.find { it.id == id } ?: return null
        update { l -> l.filterNot { it.id == id } }
        return n
    }

    fun clearDone(): List<Note> {
        val done = _notes.value.filter { it.done }
        if (done.isNotEmpty()) update { l -> l.filterNot { it.done } }
        return done
    }

    /** Saves a new manual order (ids in the order they should be shown). */
    fun reorder(ids: List<Long>) = update { reorderNotes(it, ids) }

    fun restore(items: List<Note>) = update { restoreNotes(it, items) }

    private fun update(f: (List<Note>) -> List<Note>) {
        val ok: Boolean
        synchronized(lock) {
            val next = sortNotes(f(_notes.value))
            if (next == _notes.value) return
            _notes.value = next
            ok = save(next)
        }
        if (!ok) _saveErrors.tryEmit(Unit)
        onChanged()
    }

    fun onChanged() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            LockNotification.refresh(app)
            runCatching { NotesWidget().updateAll(app) }
                .onFailure { Log.w(TAG, "Widget update failed", it) }
        }
    }

    private fun load(): List<Note> {
        val f = file
        val bytes = try {
            f.readFully()
        } catch (_: FileNotFoundException) {
            return emptyList()  // first launch
        } catch (e: Exception) {
            Log.e(TAG, "Cannot read notes", e)
            return emptyList()
        }
        return try {
            decodeNotes(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            // Don't let the next save overwrite the only copy of the user's notes: set the broken file aside.
            Log.e(TAG, "notes.json is corrupt, moving it aside", e)
            runCatching {
                f.baseFile.copyTo(File(app.filesDir, "notes.corrupt-${System.currentTimeMillis()}.json"))
            }.onFailure { Log.e(TAG, "Cannot back up corrupt notes.json", it) }
            emptyList()
        }
    }

    private fun save(l: List<Note>): Boolean {
        val f = file
        val out = try {
            f.startWrite()
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open notes.json for writing", e)
            return false
        }
        return try {
            out.write(encodeNotes(l).toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Cannot save notes", e)
            f.failWrite(out)
            false
        }
    }
}

// ---- Pure list logic (unit-tested in NotesLogicTest) ----

/** Note text is single-line. */
internal fun cleanText(text: String) = text.trim().replace('\n', ' ')

/** Active first (pinned on top), then done; inside each group — in manual order. */
internal fun sortNotes(l: List<Note>) =
    l.sortedWith(compareBy<Note>({ it.done }, { !it.pinned }, { it.order }, { it.created }))

/** Appends a note with the next colour in the cycle; blank text is ignored. */
internal fun addNote(list: List<Note>, text: String, now: Long): List<Note> {
    val t = cleanText(text)
    if (t.isEmpty()) return list
    val lastColor = list.maxByOrNull { it.created }?.color ?: -1
    val id = maxOf(now, (list.maxOfOrNull { it.id } ?: 0) + 1)
    val order = (list.maxOfOrNull { it.order } ?: 0) + 1
    return list + Note(id = id, text = t, color = (lastColor + 1) % NOTE_COLORS, created = id, order = order)
}

internal fun reorderNotes(list: List<Note>, ids: List<Long>): List<Note> {
    val pos = ids.withIndex().associate { (i, id) -> id to i.toLong() }
    return list.map { n -> pos[n.id]?.let { n.copy(order = it) } ?: n }
}

/** Re-adds deleted notes (undo); notes that are still present are not duplicated. */
internal fun restoreNotes(list: List<Note>, items: List<Note>): List<Note> {
    val ids = list.map { it.id }.toSet()
    return list + items.filterNot { it.id in ids }
}

/** Fields added later must be read with opt* defaults so old files keep loading. */
internal fun decodeNotes(json: String): List<Note> {
    val arr = JSONArray(json)
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val id = o.getLong("id")
        val created = o.optLong("created", id)
        Note(
            id = id,
            text = o.getString("text"),
            color = o.optInt("color", 0),
            pinned = o.optBoolean("pinned"),
            done = o.optBoolean("done"),
            created = created,
            order = o.optLong("order", created),
        )
    }
}

internal fun encodeNotes(l: List<Note>): String {
    val arr = JSONArray()
    l.forEach {
        arr.put(
            JSONObject()
                .put("id", it.id).put("text", it.text).put("color", it.color)
                .put("pinned", it.pinned).put("done", it.done).put("created", it.created).put("order", it.order)
        )
    }
    return arr.toString()
}

/** Ukrainian plural: 1 справа, 2 справи, 5 справ. */
fun countLabel(ctx: Context, n: Int): String =
    ctx.resources.getQuantityString(R.plurals.notes_count, n, n)
