package ua.pysanka.voice

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import org.florisboard.lib.compose.stringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.launch
import ua.pysanka.models.ModelImport
import ua.pysanka.models.ModelStore
import ua.pysanka.models.PinnedModel
import ua.pysanka.models.ModelSection
import ua.pysanka.whisper.WhisperNative

/**
 * Pysanka: dictation in the settings: the model (downloaded by the person, only the exact published
 * file) and the microphone, asked for here, never from the keyboard.
 */
@Composable
fun VoiceSettingsScreen() = FlorisScreen {
    title = stringRes(R.string.pysanka__voice__title)
    previewFieldVisible = false

    content {
        val context = LocalContext.current
        var mic by remember { mutableStateOf(VoiceInput.hasMic(context)) }
        var denied by remember { mutableStateOf(false) }
        // Back from the system settings: read the permission again
        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
        androidx.compose.runtime.DisposableEffect(lifecycle) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) mic = VoiceInput.hasMic(context)
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            mic = granted
            denied = !granted
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringRes(R.string.pysanka__voice__about), style = MaterialTheme.typography.bodyMedium)
            if (!WhisperNative.supported) {
                Text(stringRes(R.string.pysanka__voice__unsupported), color = MaterialTheme.colorScheme.error)
            }
            // Which Whisper: the more accurate or the faster
            val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val chosen by prefs.pysankaVoice.model.observeAsState()
            Text(stringRes(R.string.pysanka__voice__model), style = MaterialTheme.typography.titleMedium)
            for ((name, label) in listOf("base" to R.string.pysanka__voice__model_base, "small" to R.string.pysanka__voice__model_small)) {
                Row(
                    Modifier.fillMaxWidth().selectable(selected = chosen == name, role = Role.RadioButton) {
                        scope.launch { prefs.pysankaVoice.model.set(name) }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = chosen == name, onClick = null)
                    Text(stringRes(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                }
            }
            val import = if (chosen == "base") ModelImport.VOICE_BASE else ModelImport.VOICE
            key(import) {
                ModelSection(import) {
                    Text(stringRes(R.string.pysanka__voice__how_to_use), style = MaterialTheme.typography.bodyMedium)
                }
                // The other one, if it is still on the phone, can go
                val other = remember { ModelStore(context, if (import === ModelImport.VOICE) PinnedModel.WHISPER_BASE else PinnedModel.WHISPER) }
                var otherThere by remember { mutableStateOf(other.modelFile() != null) }
                // Not while either is being copied in (its temporary file is in its folder)
                val copying by import.status.collectAsState()
                if (otherThere && copying.progress == null && !ModelImport.VOICE.running && !ModelImport.VOICE_BASE.running) {
                    OutlinedButton(onClick = { other.delete(); otherThere = false }) {
                        Text(stringRes(R.string.pysanka__voice__delete_other))
                    }
                }
            }
            // A pause ends the dictation by itself (or only «Готово» does)
            val autoStop by prefs.pysankaVoice.autoStop.observeAsState()
            Row(Modifier.fillMaxWidth().toggleable(autoStop, role = Role.Switch) { v -> scope.launch { prefs.pysankaVoice.autoStop.set(v) } }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringRes(R.string.pysanka__voice__auto_stop), style = MaterialTheme.typography.bodyLarge)
                    Text(stringRes(R.string.pysanka__voice__auto_stop_summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = autoStop, onCheckedChange = null)
            }
            HorizontalDivider()
            if (mic) {
                Text(stringRes(R.string.pysanka__voice__mic_granted), style = MaterialTheme.typography.titleMedium)
            } else {
                Button(onClick = { ask.launch(Manifest.permission.RECORD_AUDIO) }) { Text(stringRes(R.string.pysanka__voice__mic)) }
                if (denied) {
                    // Refused: Android may not ask again; its own settings can always allow it
                    Text(stringRes(R.string.pysanka__voice__mic_denied), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }) { Text(stringRes(R.string.pysanka__voice__system_settings)) }
                }
            }
        }
    }
}
