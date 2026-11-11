package ua.mynotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.mynotes.R
import ua.mynotes.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    lockEnabled: Boolean,
    onLockChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val theme by Settings.theme.collectAsStateWithLifecycle()
    val mode by Settings.mode.collectAsStateWithLifecycle()
    val dark = isDarkTheme()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.settings_theme))
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                THEMES.forEachIndexed { i, t ->
                    ThemeSwatch(
                        name = stringResource(t.name),
                        index = i,
                        dark = dark,
                        selected = theme == i,
                        onClick = { Settings.setTheme(ctx, i) },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.settings_mode))
            SingleChoiceSegmentedButtonRow(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            ) {
                val labels = listOf(R.string.mode_system, R.string.mode_light, R.string.mode_dark).map { stringResource(it) }
                labels.forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = mode == i,
                        onClick = { Settings.setMode(ctx, i) },
                        shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(12.dp))
            ListItem(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onLockChange(!lockEnabled) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                headlineContent = { Text(stringResource(R.string.settings_lock)) },
                supportingContent = { Text(stringResource(R.string.settings_lock_desc)) },
                trailingContent = { Switch(checked = lockEnabled, onCheckedChange = onLockChange) },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun ThemeSwatch(name: String, index: Int, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val s = remember(index, dark) { schemeFor(ctx, index, dark) }
    Column(
        Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(60.dp)
                .then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else Modifier
                )
                .padding(5.dp)
                .clip(CircleShape)
                .background(s.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            // Four quadrants = the four note colours of this theme
            Column {
                Row {
                    Box(Modifier.size(25.dp).background(s.primaryContainer))
                    Box(Modifier.size(25.dp).background(s.tertiaryContainer))
                }
                Row {
                    Box(Modifier.size(25.dp).background(s.secondaryContainer))
                    Box(Modifier.size(25.dp).background(s.errorContainer))
                }
            }
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(s.primary),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = s.onPrimary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
