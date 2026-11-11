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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import org.florisboard.lib.compose.stringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.launch
import ua.pysanka.whisper.WhisperNative

/**
 * Pysanka: dictation in the settings: what to dictate with (Pysanka, or another app's voice keyboard
 * if the phone has one, see [VoiceKeyboards]); for Pysanka, how it works, the model (downloaded by
 * the person, see [ua.pysanka.models.ModelSection]), the pause that ends it and the microphone,
 * asked for here, never from the keyboard.
 */
@Composable
fun VoiceSettingsScreen() = FlorisScreen {
    title = stringRes(R.string.pysanka__voice__title)
    previewFieldVisible = false

    content {
        val context = LocalContext.current
        var mic by remember { mutableStateOf(VoiceInput.hasMic(context)) }
        var denied by remember { mutableStateOf(false) }
        var keyboards by remember { mutableStateOf(VoiceKeyboards.list(context)) }
        // Back from the system settings: read the permission and the keyboards again
        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
        androidx.compose.runtime.DisposableEffect(lifecycle) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    mic = VoiceInput.hasMic(context)
                    keyboards = VoiceKeyboards.list(context)
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            mic = granted
            denied = !granted
        }
        val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val engine by prefs.pysankaVoice.engine.observeAsState()
        // The choice only when there is something to choose (or a chosen keyboard that has gone)
        if (keyboards.isNotEmpty() || engine.isNotEmpty()) {
            val pysanka = stringRes(R.string.pysanka__voice__engine_pysanka)
            val options = listOf("" to pysanka) + keyboards.map { it.id to it.label } +
                (if (engine.isNotEmpty() && keyboards.none { it.id == engine }) listOf(engine to stringRes(R.string.pysanka__voice__other_gone)) else emptyList())
            ua.pysanka.settings.ChoiceRow(stringRes(R.string.pysanka__voice__engine), options, engine) { v ->
                scope.launch { prefs.pysankaVoice.engine.set(v) }
            }
        }
        if (engine.isNotEmpty()) {
            val chosen = keyboards.firstOrNull { it.id == engine }
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // A keyboard that has gone is already named so in the row above
                if (chosen != null) {
                    Text(stringRes(R.string.pysanka__voice__other_about, "name" to chosen.label), style = MaterialTheme.typography.bodyMedium)
                    if (!chosen.enabled) {
                        // Android lets a keyboard switch only to keyboards that are turned on
                        Text(stringRes(R.string.pysanka__voice__other_off, "name" to chosen.label), color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }) { Text(stringRes(R.string.pysanka__voice__keyboard_settings)) }
                    }
                }
            }
        }
        // Pysanka's own dictation
        if (engine.isEmpty()) Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringRes(R.string.pysanka__voice__about), style = MaterialTheme.typography.bodyMedium)
            if (!WhisperNative.supported) {
                Text(stringRes(R.string.pysanka__voice__unsupported), color = MaterialTheme.colorScheme.error)
            } else {
                // The model first: without it nothing below does anything
                ua.pysanka.models.ModelSection(
                    ua.pysanka.models.ModelImport.VOICE,
                    step1 = R.string.pysanka__voice__step1,
                    deleteAsk = R.string.pysanka__voice__delete_ask,
                )
                HorizontalDivider()
            }
            Text(stringRes(R.string.pysanka__voice__how_to_use), style = MaterialTheme.typography.bodyMedium)
            // A pause ends the dictation by itself (or only «Готово» does)
            val autoStop by prefs.pysankaVoice.autoStop.observeAsState()
            val holding by prefs.pysankaVoice.holdToTalk.observeAsState()
            Row(Modifier.fillMaxWidth().alpha(if (holding) 0.5f else 1f).toggleable(autoStop, enabled = !holding, role = Role.Switch) { v -> scope.launch { prefs.pysankaVoice.autoStop.set(v) } }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringRes(R.string.pysanka__voice__auto_stop), style = MaterialTheme.typography.bodyLarge)
                    Text(stringRes(R.string.pysanka__voice__auto_stop_summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = autoStop, onCheckedChange = null, enabled = !holding)
            }
            // Hold to speak: what is recognised is exactly what was said while held
            val hold by prefs.pysankaVoice.holdToTalk.observeAsState()
            Row(Modifier.fillMaxWidth().toggleable(hold, role = Role.Switch) { v -> scope.launch { prefs.pysankaVoice.holdToTalk.set(v) } }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringRes(R.string.pysanka__voice__hold), style = MaterialTheme.typography.bodyLarge)
                    Text(stringRes(R.string.pysanka__voice__hold_summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = hold, onCheckedChange = null)
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
