package ua.pysanka.typing.swipe

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.observeAsState
import org.florisboard.lib.compose.stringRes

/**
 * Pysanka: swipe practice: a word from a fixed list of common words at a time; the person swipes it
 * and the swipe is recorded with the decoder's guesses ([SwipeRecorder]), then the next word. The
 * records are saved to a file by the person, to tune the decoder on real swipes.
 */
@Composable
fun SwipePracticeScreen() = FlorisScreen {
    title = stringRes(R.string.pysanka__swipe_practice__title)
    previewFieldVisible = false

    content {
        val context = LocalContext.current
        val prefs by FlorisPreferenceStore
        val glide by prefs.glide.enabled.observeAsState()
        val words = remember { SwipeRecorder.words(context, "uk") }
        val count by SwipeRecorder.count.collectAsState()
        val last by SwipeRecorder.last.collectAsState()
        LaunchedEffect(Unit) { SwipeRecorder.refresh(context) }
        var index by remember { mutableIntStateOf(-1) }
        var seen by remember { mutableIntStateOf(-1) }
        var text by remember { mutableStateOf("") }
        // The swiped word arrives in the field a moment after it was recorded: it is cleared then too
        var clearNext by remember { mutableStateOf(false) }
        var confirmClear by remember { mutableStateOf(false) }
        var saved by remember { mutableStateOf<Boolean?>(null) }

        // Where the last practice stopped, at first; then the next word after each recorded swipe
        LaunchedEffect(count) {
            if (count < 0 || words.isEmpty()) return@LaunchedEffect
            if (seen < 0) index = count % words.size else if (count > seen) {
                index = (index + 1) % words.size
                text = ""
                clearNext = true
            } else if (count == 0) index = 0
            seen = count
        }
        val word = words.getOrNull(index)
        // Asked only while the screen is in front (not behind another app or the home screen)
        androidx.lifecycle.compose.LifecycleResumeEffect(word) {
            SwipeRecorder.target = word
            onPauseOrDispose { SwipeRecorder.target = null }
        }
        val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) saved = SwipeRecorder.export(context, uri)
        }

        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringRes(R.string.pysanka__swipe_practice__about), style = MaterialTheme.typography.bodyMedium)
            if (!glide) Text(stringRes(R.string.pysanka__swipe_practice__glide_off), color = MaterialTheme.colorScheme.error)
            if (word != null) {
                Text(
                    stringRes(R.string.pysanka__swipe_practice__progress, "n" to (index + 1).toString(), "all" to words.size.toString()),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(word, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (clearNext) { clearNext = false; text = "" } else text = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true,
                    label = { Text(stringRes(R.string.pysanka__swipe_practice__field)) },
                    // Marked: only a swipe into this field is recorded (SwipeRecorder.targetFor)
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                        platformImeOptions = androidx.compose.ui.text.input.PlatformImeOptions(SwipeRecorder.FIELD),
                    ),
                )
                last?.let { (asked, guess) ->
                    Text(
                        stringRes(
                            if (guess.equals(asked, ignoreCase = true)) R.string.pysanka__swipe_practice__right else R.string.pysanka__swipe_practice__wrong,
                            "word" to asked, "guess" to (guess ?: "—"),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedButton(onClick = {
                    index = (index + 1) % words.size
                    text = ""
                    clearNext = false
                }) { Text(stringRes(R.string.pysanka__swipe_practice__skip)) }
            }
            Text(stringRes(R.string.pysanka__swipe_practice__count, "n" to count.coerceAtLeast(0).toString()), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = count > 0, onClick = { save.launch("pysanka-swipes.jsonl") }) { Text(stringRes(R.string.pysanka__swipe_practice__save)) }
                TextButton(enabled = count > 0, onClick = { confirmClear = true }) { Text(stringRes(R.string.pysanka__swipe_practice__clear)) }
            }
            saved?.let { Text(stringRes(if (it) R.string.pysanka__swipe_practice__saved else R.string.pysanka__swipe_practice__save_failed)) }
        }

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                text = { Text(stringRes(R.string.pysanka__swipe_practice__clear_ask)) },
                confirmButton = {
                    TextButton(onClick = { SwipeRecorder.clear(context); confirmClear = false }) {
                        Text(stringRes(R.string.pysanka__swipe_practice__clear), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringRes(android.R.string.cancel)) } },
            )
        }
    }
}
