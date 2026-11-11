package app.memi.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import app.memi.CapsuleSeg
import app.memi.FlowStyle
import app.memi.LockNotification
import app.memi.NOTE_COLORS
import app.memi.Note
import app.memi.NotesRepo
import app.memi.R
import app.memi.countLabel
import app.memi.flowCapsules
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
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

    // Arrange mode: entered by a long press; capsules show a delete cross and can be dragged
    var arranging by remember { mutableStateOf(false) }
    var dragOrder by remember { mutableStateOf<List<Note>?>(null) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    if (notes.isEmpty() && arranging) arranging = false
    BackHandler(enabled = arranging) { arranging = false }

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
    LaunchedEffect(Unit) {
        NotesRepo.saveErrors.collect {
            snackbar.showSnackbar(ctx.getString(R.string.save_failed), duration = SnackbarDuration.Long)
        }
    }

    fun undoable(message: String, items: List<Note>) {
        if (items.isEmpty()) return
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, ctx.getString(R.string.action_undo), duration = SnackbarDuration.Short)
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
                        Text(stringResource(R.string.app_name))
                        if (notes.isNotEmpty()) Text(
                            if (active == 0) stringResource(R.string.all_done_party) else countLabel(ctx, active),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (arranging) {
                        TextButton(onClick = { arranging = false }) { Text(stringResource(R.string.action_done)) }
                    } else {
                        if (hasDone) IconButton(onClick = {
                            undoable(ctx.getString(R.string.done_deleted), NotesRepo.clearDone())
                        }) { Icon(Icons.Default.Delete, stringResource(R.string.delete_done)) }
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(Icons.Default.Settings, stringResource(R.string.settings))
                        }
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
                        stringResource(R.string.empty_hint),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .pointerInput(arranging) {
                        // Tap on empty space leaves arrange mode
                        detectTapGestures { if (arranging) arranging = false }
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                CapsuleFlow(
                    notes = dragOrder ?: notes,
                    arranging = arranging,
                    draggingId = draggingId,
                    dragPos = dragPos,
                    onTap = { note ->
                        if (arranging) editing = note
                        else {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            NotesRepo.toggle(note.id)
                        }
                    },
                    onDelete = { note ->
                        NotesRepo.delete(note.id)?.let { undoable(ctx.getString(R.string.note_deleted, it.text), listOf(it)) }
                    },
                    onEmptyTap = { arranging = false },
                    onDragStart = { note, pos ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        arranging = true
                        dragOrder = notes
                        draggingId = note.id
                        dragPos = pos
                    },
                    onDragMove = { pos -> dragPos = pos },
                    onDragOver = { target ->
                        val list = dragOrder ?: return@CapsuleFlow
                        val from = list.indexOfFirst { it.id == draggingId }
                        val to = list.indexOfFirst { it.id == target.id }
                        // Reorder only inside the same group (pinned / regular / done)
                        if (from >= 0 && to >= 0 && from != to &&
                            list[from].done == list[to].done && list[from].pinned == list[to].pinned
                        ) {
                            dragOrder = list.toMutableList().apply { add(to, removeAt(from)) }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    onDragEnd = {
                        dragOrder?.let { list -> NotesRepo.reorder(list.map { it.id }) }
                        dragOrder = null
                        draggingId = null
                    },
                )
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
                NotesRepo.delete(note.id)?.let { undoable(ctx.getString(R.string.note_deleted, it.text), listOf(it)) }
            },
        )
    }
}

private sealed interface Hit {
    data class Body(val note: Note) : Hit
    data class Delete(val note: Note) : Hit
}

/**
 * Notes as a continuous flow of capsules; a capsule that doesn't fit the rest of a line is cut
 * and continues on the next line in the same colour. All gestures are handled here:
 * tap — toggle (or edit in arrange mode), long press — arrange mode, long press + move — drag.
 */
@Composable
private fun CapsuleFlow(
    notes: List<Note>,
    arranging: Boolean,
    draggingId: Long?,
    dragPos: Offset,
    onTap: (Note) -> Unit,
    onDelete: (Note) -> Unit,
    onEmptyTap: () -> Unit,
    onDragStart: (Note, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragOver: (Note) -> Unit,
    onDragEnd: () -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodyMedium
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth.toFloat()
        val px = with(density) {
            object {
                val hPad = 14.dp.toPx()
                val gap = 6.dp.toPx()
                val segH = 36.dp.toPx()
                val lineGap = 6.dp.toPx()
                val icon = (16 + 5).dp.toPx()
                val minPiece = 36.dp.toPx()
                val badge = 20.dp.toPx()
                val badgeTouch = 32.dp.toPx()
            }
        }
        val segs = remember(notes, widthPx, textStyle, density) {
            flowCapsules(
                texts = notes.map { it.text },
                prefixes = notes.map { if (it.done || it.pinned) px.icon else 0f },
                lineWidth = widthPx,
                style = FlowStyle(hPad = px.hPad, gap = px.gap, minPiece = px.minPiece),
            ) { s -> measurer.measure(s, textStyle, maxLines = 1, softWrap = false).size.width.toFloat() }
        }
        val lines = (segs.maxOfOrNull { it.line } ?: -1) + 1
        val topInset = px.badge / 2   // room for the delete crosses of the first line
        fun lineY(line: Int) = topInset + line * (px.segH + px.lineGap)
        val totalH = if (lines == 0) 0f else lineY(lines - 1) + px.segH

        // Delete cross position: top-end corner of the capsule's last piece
        fun badgeCenter(s: CapsuleSeg) = Offset(s.x + s.width - px.badge * 0.35f, lineY(s.line) + px.badge * 0.15f)

        val state by rememberUpdatedState(Triple(segs, notes, arranging))
        val cb by rememberUpdatedState(listOf(onTap, onDelete))

        fun bodyAt(p: Offset): Note? {
            val (ss, ns) = state
            return ss.firstOrNull { s ->
                val y = lineY(s.line)
                p.x in s.x..(s.x + s.width) && p.y in y..(y + px.segH)
            }?.let { ns[it.index] }
        }

        fun hitAt(p: Offset): Hit? {
            val (ss, ns, arr) = state
            if (arr) ss.filter { it.last }.forEach { s ->
                if ((p - badgeCenter(s)).getDistance() <= px.badgeTouch / 2) return Hit.Delete(ns[s.index])
            }
            return bodyAt(p)?.let { Hit.Body(it) }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(with(density) { totalH.toDp() })
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val hit = hitAt(down.position)
                        // 1 = released (tap), 2 = moved away / taken by scrolling, null = long press
                        val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var r = 0
                            while (r == 0) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id }
                                r = when {
                                    ch == null -> 2
                                    ch.changedToUp() -> { ch.consume(); 1 }
                                    ch.isConsumed -> 2
                                    (ch.position - down.position).getDistance() > viewConfiguration.touchSlop -> 2
                                    else -> 0
                                }
                            }
                            r
                        }
                        when (outcome) {
                            1 -> when (hit) {
                                is Hit.Body -> cb[0](hit.note)
                                is Hit.Delete -> cb[1](hit.note)
                                null -> onEmptyTap()
                            }
                            2 -> Unit
                            null -> {
                                val note = (hit as? Hit.Body)?.note ?: return@awaitEachGesture
                                onDragStart(note, down.position)
                                var last: Long? = note.id
                                try {
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        if (ch.changedToUp()) { ch.consume(); break }
                                        if (ch.positionChanged()) {
                                            ch.consume()
                                            onDragMove(ch.position)
                                            val target = bodyAt(ch.position)
                                            if (target?.id != last) {
                                                last = target?.id
                                                if (target != null && target.id != note.id) onDragOver(target)
                                            }
                                        }
                                    }
                                } finally {
                                    onDragEnd()
                                }
                            }
                        }
                    }
                }
        ) {
            segs.forEachIndexed { i, s ->
                val note = notes[s.index]
                key(note.id, i - segs.indexOfFirst { it.index == s.index }) {
                    CapsulePiece(
                        note = note,
                        seg = s,
                        modifier = Modifier
                            .offset { IntOffset(s.x.roundToInt(), lineY(s.line).roundToInt()) }
                            .size(with(density) { s.width.toDp() }, with(density) { px.segH.toDp() })
                            .alpha(if (note.id == draggingId) 0.35f else 1f),
                        textStyleHPad = with(density) { px.hPad.toDp() },
                    )
                }
            }
            if (arranging) segs.filter { it.last && notes[it.index].id != draggingId }.forEach { s ->
                val c = badgeCenter(s)
                Box(
                    Modifier
                        .offset { IntOffset((c.x - px.badge / 2).roundToInt(), (c.y - px.badge / 2).roundToInt()) }
                        .size(with(density) { px.badge.toDp() })
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.inverseSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close, stringResource(R.string.action_delete),
                        Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
            // The capsule being dragged follows the finger
            notes.firstOrNull { it.id == draggingId }?.let { note ->
                val (bg, fg) = noteColors(note.color)
                Box(
                    Modifier
                        .offset {
                            IntOffset((dragPos.x - 40.dp.toPx()).roundToInt(), (dragPos.y - px.segH * 1.4f).roundToInt())
                        }
                        .widthIn(max = with(density) { (widthPx * 0.8f).toDp() })
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(if (note.done) MaterialTheme.colorScheme.surfaceContainerHigh else bg)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        note.text,
                        color = if (note.done) MaterialTheme.colorScheme.outline else fg,
                        style = textStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CapsulePiece(note: Note, seg: CapsuleSeg, modifier: Modifier, textStyleHPad: androidx.compose.ui.unit.Dp) {
    val (bg, fg) = noteColors(note.color)
    val c = MaterialTheme.colorScheme
    val background by animateColorAsState(if (note.done) Color.Transparent else bg, label = "bg")
    val content by animateColorAsState(if (note.done) c.outline else fg, label = "fg")
    // Rounded at the real ends of the capsule, almost square where it is cut
    val round = 50
    val cut = 10
    val shape = RoundedCornerShape(
        topStartPercent = if (seg.first) round else cut,
        topEndPercent = if (seg.last) round else cut,
        bottomEndPercent = if (seg.last) round else cut,
        bottomStartPercent = if (seg.first) round else cut,
    )
    Row(
        modifier = modifier
            .clip(shape)
            .background(background)
            .then(if (note.done) Modifier.border(BorderStroke(1.dp, c.outlineVariant), shape) else Modifier)
            .padding(horizontal = textStyleHPad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (seg.first && (note.done || note.pinned)) {
            if (note.done) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = content)
            else Icon(painterResource(R.drawable.ic_pin), null, Modifier.size(16.dp), tint = content)
            Spacer(Modifier.width(5.dp))
        }
        Text(
            seg.text,
            color = content,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            style = MaterialTheme.typography.bodyMedium,
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
                placeholder = { Text(stringResource(R.string.input_placeholder)) },
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
            ) { Icon(Icons.Default.Add, stringResource(R.string.action_add)) }
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
                    label = { Text(stringResource(R.string.action_pin)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_pin), null, Modifier.size(18.dp)) },
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSave(text, color, pinned) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}
