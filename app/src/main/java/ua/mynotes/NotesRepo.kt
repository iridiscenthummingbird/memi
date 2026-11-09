package ua.mynotes

import android.content.Context
import android.util.AtomicFile
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import ua.mynotes.widget.NotesWidget
import java.io.File

data class Note(
    val id: Long,
    val text: String,
    val color: Int,
    val pinned: Boolean = false,
    val done: Boolean = false,
    val created: Long = id,
)

const val NOTE_COLORS = 4

/** Single source of truth for notes, shared by the app, the widget and the lock-screen notification. */
object NotesRepo {
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes

    private lateinit var app: Context
    private var loaded = false
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshJob: Job? = null

    private val file get() = AtomicFile(File(app.filesDir, "notes.json"))

    fun init(context: Context) = synchronized(lock) {
        if (loaded) return
        app = context.applicationContext
        _notes.value = sort(load())
        loaded = true
    }

    fun add(text: String) {
        val t = text.trim().replace('\n', ' ')
        if (t.isEmpty()) return
        update { list ->
            val lastColor = list.maxByOrNull { it.created }?.color ?: -1
            val now = System.currentTimeMillis()
            val id = maxOf(now, (list.maxOfOrNull { it.id } ?: 0) + 1)
            list + Note(id = id, text = t, color = (lastColor + 1) % NOTE_COLORS, created = id)
        }
    }

    fun toggle(id: Long) = update { l -> l.map { if (it.id == id) it.copy(done = !it.done) else it } }

    fun edit(id: Long, text: String, color: Int, pinned: Boolean) {
        val t = text.trim().replace('\n', ' ')
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

    fun restore(items: List<Note>) = update { l ->
        val ids = l.map { it.id }.toSet()
        l + items.filterNot { it.id in ids }
    }

    private fun update(f: (List<Note>) -> List<Note>) {
        synchronized(lock) {
            _notes.value = sort(f(_notes.value))
            save(_notes.value)
        }
        onChanged()
    }

    fun onChanged() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            LockNotification.refresh(app)
            runCatching { NotesWidget().updateAll(app) }
        }
    }

    /** Active first (pinned on top), then done; inside each group — in order of creation. */
    private fun sort(l: List<Note>) =
        l.sortedWith(compareBy<Note>({ it.done }, { !it.pinned }, { it.created }))

    private fun load(): List<Note> = runCatching {
        val arr = JSONArray(String(file.readFully(), Charsets.UTF_8))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Note(
                id = o.getLong("id"),
                text = o.getString("text"),
                color = o.optInt("color", 0),
                pinned = o.optBoolean("pinned"),
                done = o.optBoolean("done"),
                created = o.optLong("created", o.getLong("id")),
            )
        }
    }.getOrDefault(emptyList())

    private fun save(l: List<Note>) {
        val arr = JSONArray()
        l.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("text", it.text).put("color", it.color)
                    .put("pinned", it.pinned).put("done", it.done).put("created", it.created)
            )
        }
        val f = file
        val out = f.startWrite()
        try {
            out.write(arr.toString().toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (e: Exception) {
            f.failWrite(out)
        }
    }
}

/** Ukrainian plural: 1 справа, 2 справи, 5 справ. */
fun countLabel(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    val word = when {
        m10 == 1 && m100 != 11 -> "справа"
        m10 in 2..4 && m100 !in 12..14 -> "справи"
        else -> "справ"
    }
    return "$n $word"
}
