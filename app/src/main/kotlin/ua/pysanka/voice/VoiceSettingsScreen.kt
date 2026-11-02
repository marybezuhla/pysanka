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
import ua.pysanka.models.ModelImport
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
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            mic = granted
            denied = !granted
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringRes(R.string.pysanka__voice__about), style = MaterialTheme.typography.bodyMedium)
            if (!WhisperNative.supported) {
                Text(stringRes(R.string.pysanka__voice__unsupported), color = MaterialTheme.colorScheme.error)
            }
            ModelSection(ModelImport.VOICE) {
                Text(stringRes(R.string.pysanka__voice__how_to_use), style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider()
            if (mic) {
                Text(stringRes(R.string.pysanka__voice__mic_granted), style = MaterialTheme.typography.titleMedium)
            } else {
                Button(onClick = { ask.launch(Manifest.permission.RECORD_AUDIO) }) { Text(stringRes(R.string.pysanka__voice__mic)) }
                if (denied) {
                    // Asked before and refused: Android does not ask again; only its own settings can allow it
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
