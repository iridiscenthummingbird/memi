package app.memi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import app.memi.ui.NotesScreen
import app.memi.ui.NotesTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_ADD = "app.memi.ADD"
    }

    /** Incremented every time the app is opened via "+" (widget / shortcut) to focus the input. */
    private val focusRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Settings.init(this)
        NotesRepo.init(this)
        if (savedInstanceState == null) handle(intent)
        setContent {
            NotesTheme { NotesScreen(focusRequests.intValue) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        NotesRepo.onChanged() // permission/setting may have changed while away
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_ADD) focusRequests.intValue++
    }
}
