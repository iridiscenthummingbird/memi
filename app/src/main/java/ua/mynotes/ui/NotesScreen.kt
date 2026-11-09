package ua.mynotes.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ua.mynotes.LockNotification
import ua.mynotes.NOTE_COLORS
import ua.mynotes.Note
import ua.mynotes.NotesRepo
import ua.mynotes.R
import ua.mynotes.countLabel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotesScreen(focusRequest: Int) {
    val ctx = LocalContext.current
    val notes by NotesRepo.notes.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var editing by remember { mutableStateOf<Note?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var lockEnabled by remember { mutableStateOf(LockNotification.isEnabled(ctx)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            lockEnabled = false
            LockNotification.setEnabled(ctx, false)
        } else NotesRepo.onChanged()
    }
    fun askPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && !LockNotification.hasPermission(ctx)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) { if (lockEnabled) askPermissionIfNeeded() }

    fun undoable(message: String, items: List<Note>) {
        if (items.isEmpty()) return
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, "Повернути", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) NotesRepo.restore(items)
        }
    }

    val active = notes.count { !it.done }
    val hasDone = notes.any { it.done }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Замітки")
                        if (notes.isNotEmpty()) Text(
                            if (active == 0) "Все зроблено 🎉" else countLabel(active),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (hasDone) IconButton(onClick = {
                        undoable("Виконані видалено", NotesRepo.clearDone())
                    }) { Icon(Icons.Default.Delete, "Видалити виконані") }
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Default.Settings, "Налаштування")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = { InputBar(focusRequest) },
    ) { padding ->
        if (notes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✨", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Поки що порожньо.\nНапиши щось унизу — наприклад, «купити молоко»",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            FlowRow(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                notes.forEach { note ->
                    androidx.compose.runtime.key(note.id) {
                        NoteChip(
                            note = note,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                NotesRepo.toggle(note.id)
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                editing = note
                            },
                        )
                    }
                }
            }
        }
    }

    if (settingsOpen) SettingsSheet(
        lockEnabled = lockEnabled,
        onLockChange = {
            lockEnabled = it
            LockNotification.setEnabled(ctx, it)
            if (it) askPermissionIfNeeded()
        },
        onDismiss = { settingsOpen = false },
    )

    editing?.let { note ->
        EditSheet(
            note = note,
            onDismiss = { editing = null },
            onSave = { text, color, pinned ->
                NotesRepo.edit(note.id, text, color, pinned); editing = null
            },
            onDelete = {
                editing = null
                NotesRepo.delete(note.id)?.let { undoable("Видалено «${it.text}»", listOf(it)) }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteChip(note: Note, onClick: () -> Unit, onLongClick: () -> Unit) {
    val (bg, fg) = noteColors(note.color)
    val c = MaterialTheme.colorScheme
    val background by animateColorAsState(if (note.done) Color.Transparent else bg, label = "bg")
    val content by animateColorAsState(if (note.done) c.outline else fg, label = "fg")
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .then(if (note.done) Modifier.border(BorderStroke(1.dp, c.outlineVariant), CircleShape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .animateContentSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (note.done || note.pinned) {
            if (note.done) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = content)
            else Icon(painterResource(R.drawable.ic_pin), null, Modifier.size(16.dp), tint = content)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            note.text,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (note.done) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun InputBar(focusRequest: Int) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) {
            focus.requestFocus(); keyboard?.show()
        }
    }
    fun submit() {
        if (text.isNotBlank()) {
            NotesRepo.add(text); text = ""
        }
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = text,
                onValueChange = { text = it.replace("\n", "") },
                modifier = Modifier.weight(1f).focusRequester(focus),
                placeholder = { Text("Нова замітка…") },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                // Keep the keyboard open so several notes can be typed in a row
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { submit() },
                enabled = text.isNotBlank(),
                modifier = Modifier.size(56.dp),
            ) { Icon(Icons.Default.Add, "Додати") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditSheet(
    note: Note,
    onDismiss: () -> Unit,
    onSave: (String, Int, Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember { mutableStateOf(note.text) }
    var color by remember { mutableStateOf(note.color) }
    var pinned by remember { mutableStateOf(note.pinned) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.replace("\n", "") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSave(text, color, pinned) }),
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                (0 until NOTE_COLORS).forEach { i ->
                    val (bg, fg) = noteColors(i)
                    Box(
                        Modifier
                            .padding(end = 12.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(bg)
                            .then(
                                if (color == i) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                else Modifier
                            )
                            .clickable { color = i },
                        contentAlignment = Alignment.Center,
                    ) { if (color == i) Icon(Icons.Default.Check, null, tint = fg) }
                }
                Spacer(Modifier.weight(1f))
                FilterChip(
                    selected = pinned,
                    onClick = { pinned = !pinned },
                    label = { Text("Закріпити") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_pin), null, Modifier.size(18.dp)) },
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text("Видалити", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSave(text, color, pinned) }, enabled = text.isNotBlank()) { Text("Зберегти") }
            }
        }
    }
}
