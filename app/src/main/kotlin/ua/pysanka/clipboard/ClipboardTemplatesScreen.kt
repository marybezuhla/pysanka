package ua.pysanka.clipboard

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.observeAsState
import org.florisboard.lib.compose.stringRes

/**
 * Pysanka: templates — texts the person types here once (an address, a signature, a polite
 * answer) and then pastes with one tap: they are pinned in the clipboard history (encrypted, like
 * the rest of it), always on top. The pinned texts are listed here to remove.
 */
@Composable
fun ClipboardTemplatesScreen() = FlorisScreen {
    title = stringRes(R.string.pysanka__templates__title)
    previewFieldVisible = false

    content {
        val context = LocalContext.current
        val clipboardManager by context.clipboardManager()
        val prefs by FlorisPreferenceStore
        val historyOn by prefs.clipboard.historyEnabled.observeAsState()
        val history by clipboardManager.historyFlow.collectAsState()
        var draft by remember { mutableStateOf("") }

        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringRes(R.string.pysanka__templates__about), style = MaterialTheme.typography.bodyMedium)
            if (!historyOn) {
                Text(stringRes(R.string.pysanka__templates__history_off), color = MaterialTheme.colorScheme.error)
                return@Column
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(MAX_CHARS) },
                label = { Text(stringRes(R.string.pysanka__templates__new)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(enabled = draft.isNotBlank(), onClick = {
                clipboardManager.pysankaAddPinned(draft.trim())
                draft = ""
            }) { Text(stringRes(R.string.pysanka__templates__add)) }
            for (item in history.pinned.filter { it.type == ItemType.TEXT && !it.isSensitive }) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.text.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 3, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                        IconButton(onClick = { clipboardManager.clearExactHistory(listOf(item)) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringRes(R.string.pysanka__templates__delete))
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_CHARS = 2000
