package ua.pysanka.typing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.stringRes

/**
 * Pysanka: text replacement in the settings: a short word and its text; typed on the keyboard, the
 * word is offered as its text and a space puts it in. The same word again replaces its text.
 */
@Composable
fun TextShortcutsScreen() = FlorisScreen {
    title = stringRes(R.string.pysanka__shortcuts__title)
    previewFieldVisible = true

    content {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val list by TextShortcuts.all.collectAsState()
        var shortcut by remember { mutableStateOf("") }
        var text by remember { mutableStateOf("") }
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { TextShortcuts.load(context, now = true) } }

        fun keep(new: List<TextShortcuts.Shortcut>, done: () -> Unit = {}) = scope.launch {
            val ok = withContext(Dispatchers.IO) { TextShortcuts.save(context, new) }
            if (ok) done() else android.widget.Toast.makeText(context, R.string.pysanka__shortcuts__not_saved, android.widget.Toast.LENGTH_LONG).show()
        }

        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringRes(R.string.pysanka__shortcuts__about), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = shortcut,
                onValueChange = { v -> shortcut = v.filter { it.isLetterOrDigit() }.take(TextShortcuts.MAX_SHORTCUT) },
                label = { Text(stringRes(R.string.pysanka__shortcuts__shortcut)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(TextShortcuts.MAX_TEXT) },
                label = { Text(stringRes(R.string.pysanka__shortcuts__text)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            val current = list
            // At the most there are (a new one only in place of one with the same word)
            val full = current != null && current.size >= TextShortcuts.MAX && current.none { it.shortcut.equals(shortcut, ignoreCase = true) }
            // One with the same word is replaced only after asking
            var overwrite by remember { mutableStateOf(false) }
            var deleting by remember { mutableStateOf<TextShortcuts.Shortcut?>(null) }
            fun add() {
                val rest = current.orEmpty().filter { !it.shortcut.equals(shortcut, ignoreCase = true) }
                keep(rest + TextShortcuts.Shortcut(shortcut, text.trim())) { shortcut = ""; text = "" }
            }
            Button(enabled = current != null && !full && TextShortcuts.isValidShortcut(shortcut) && text.isNotBlank(), onClick = {
                if (current.orEmpty().any { it.shortcut.equals(shortcut, ignoreCase = true) }) overwrite = true else add()
            }) { Text(stringRes(R.string.pysanka__shortcuts__add)) }
            if (overwrite) {
                ua.pysanka.settings.ConfirmDialog(
                    stringRes(R.string.pysanka__shortcuts__overwrite, "shortcut" to shortcut),
                    confirm = stringRes(R.string.pysanka__confirm__replace),
                    onConfirm = { add() },
                    onDismiss = { overwrite = false },
                )
            }
            deleting?.let { d ->
                ua.pysanka.settings.ConfirmDialog(
                    stringRes(R.string.pysanka__shortcuts__delete_ask, "shortcut" to d.shortcut),
                    onConfirm = { keep(current.orEmpty() - d) {} },
                    onDismiss = { deleting = null },
                )
            }
            if (current == null) Text(stringRes(R.string.pysanka__shortcuts__not_ready), color = MaterialTheme.colorScheme.error)
            if (full) Text(stringRes(R.string.pysanka__shortcuts__full, "n" to TextShortcuts.MAX), color = MaterialTheme.colorScheme.error)
            for (item in current.orEmpty()) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(item.shortcut, style = MaterialTheme.typography.titleSmall)
                            Text(item.text, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                        }
                        IconButton(onClick = { deleting = item }) {
                            Icon(Icons.Default.Delete, contentDescription = stringRes(R.string.pysanka__shortcuts__delete, "shortcut" to item.shortcut))
                        }
                    }
                }
            }
        }
    }
}
