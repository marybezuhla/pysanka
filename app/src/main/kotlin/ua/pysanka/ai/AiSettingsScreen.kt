package ua.pysanka.ai

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import ua.pysanka.models.ModelImport
import ua.pysanka.models.ModelSection

/**
 * Pysanka: "AI on the phone" in the settings: getting the model (the person downloads it in the
 * browser, then picks the file; only the exact published file is accepted), removing it, and
 * «Мій стиль» (samples of the person's own writing).
 */
@androidx.compose.runtime.Composable
fun AiSettingsScreen() = FlorisScreen {
    title = stringRes(R.string.pysanka__settings__ai)
    previewFieldVisible = false

    content {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringRes(R.string.pysanka__ai__about), style = MaterialTheme.typography.bodyMedium)
            ModelSection(ModelImport.AI) {
                Text(stringRes(R.string.pysanka__ai__how_to_use), style = MaterialTheme.typography.bodyMedium)
            }
            StyleSection()
        }
    }
}

/** «Мій стиль»: the person's own samples, added and removed only by them. */
@androidx.compose.runtime.Composable
private fun StyleSection() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var samples by remember { mutableStateOf<List<String>?>(null) }
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    // One change at a time: each is made from the list as it was saved
    var saving by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        samples = withContext(Dispatchers.IO) { StyleSamples.read(context) }
    }
    fun save(new: List<String>) {
        if (saving) return
        saving = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { StyleSamples.write(context, new) }.isSuccess }
            if (ok) samples = new else Toast.makeText(context, R.string.pysanka__style__save_failed, Toast.LENGTH_LONG).show()
            saving = false
        }
    }

    HorizontalDivider()
    Text(stringRes(R.string.pysanka__style__title), style = MaterialTheme.typography.titleMedium)
    Text(stringRes(R.string.pysanka__style__about), style = MaterialTheme.typography.bodyMedium)
    val list = samples ?: return
    var deleting by remember { mutableStateOf(-1) }
    if (deleting in list.indices) {
        ua.pysanka.settings.ConfirmDialog(
            stringRes(R.string.pysanka__style__remove_ask),
            onConfirm = { val i = deleting; save(list.filterIndexed { j, _ -> j != i }) },
            onDismiss = { deleting = -1 },
        )
    }
    list.forEachIndexed { i, sample ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(sample, style = MaterialTheme.typography.bodySmall, maxLines = 3, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                IconButton(onClick = { deleting = i }, enabled = !saving) {
                    Icon(Icons.Default.Delete, contentDescription = stringRes(R.string.pysanka__style__remove_named, "text" to sample.take(30)))
                }
            }
        }
    }
    OutlinedButton(onClick = { draft = ""; adding = true }, enabled = list.size < StyleSamples.MAX && !saving) {
        Text(stringRes(R.string.pysanka__style__add))
    }
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringRes(R.string.pysanka__style__add)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = StyleSamples.cut(it) },
                    label = { Text(stringRes(R.string.pysanka__style__hint, "n" to draft.length.toString(), "max" to StyleSamples.MAX_CHARS.toString())) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { adding = false; save(list + draft) }, enabled = draft.isNotBlank()) {
                    Text(stringRes(R.string.pysanka__style__save))
                }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringRes(R.string.action__cancel)) } },
        )
    }
}
