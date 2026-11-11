package ua.pysanka.speech

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes
import ua.pysanka.models.ModelImport
import ua.pysanka.models.ModelSection

/**
 * Pysanka: reading aloud in the settings: the two voices (downloaded by the person, each checked by
 * SHA-256), the pace, reading in the background, and Pysanka as the phone's voice.
 */
@androidx.compose.runtime.Composable
fun SpeechSettingsScreen() = FlorisScreen {
    title = stringRes(R.string.pysanka__speech__title)
    previewFieldVisible = false

    content {
        val context = LocalContext.current
        val prefs by FlorisPreferenceStore
        val scope = rememberCoroutineScope()
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringRes(R.string.pysanka__speech__about), style = MaterialTheme.typography.bodyMedium)
            Text(stringRes(R.string.pysanka__speech__voice_uk), style = MaterialTheme.typography.titleMedium)
            ModelSection(ModelImport.TETIANA, step1 = R.string.pysanka__voice__step1, deleteAsk = R.string.pysanka__speech__delete_ask)
            HorizontalDivider()
            Text(stringRes(R.string.pysanka__speech__voice_en), style = MaterialTheme.typography.titleMedium)
            ModelSection(ModelImport.CORI, step1 = R.string.pysanka__voice__step1, deleteAsk = R.string.pysanka__speech__delete_ask)
            HorizontalDivider()
            Text(stringRes(R.string.pysanka__speech__how_to_use), style = MaterialTheme.typography.bodyMedium)
        }

        // The pace for reading aloud and the word in a translation (Android sets its own for the phone's voice)
        val speed by prefs.pysankaSpeech.speed.observeAsState()
        ua.pysanka.settings.SliderRow(
            stringRes(R.string.pysanka__speech__speed), speed, 80..150, 10, label = { speedLabel(it / 100f) },
        ) { v -> scope.launch { prefs.pysankaSpeech.speed.set(v) } }

        // In the background: the notification needs Android 13's permission, asked when it is turned on
        val background by prefs.pysankaSpeech.background.observeAsState()
        val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        ua.pysanka.settings.ToggleRow(stringRes(R.string.pysanka__speech__background), background, stringRes(R.string.pysanka__speech__background_hint)) { v ->
            scope.launch { prefs.pysankaSpeech.background.set(v) }
            if (v && android.os.Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // As the phone's voice: offered to Android only when turned on here (and a voice is there)
        val system by prefs.pysankaSpeech.systemVoice.observeAsState()
        ua.pysanka.settings.ToggleRow(stringRes(R.string.pysanka__speech__system), system, stringRes(R.string.pysanka__speech__system_hint)) { v ->
            scope.launch {
                prefs.pysankaSpeech.systemVoice.set(v)
                SpeechEntry.sync(context)
            }
        }
        if (system) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedButton(onClick = {
                    // Android's own text-to-speech settings (where the engine is chosen)
                    val tts = Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(tts) }.onFailure {
                        runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }) { Text(stringRes(R.string.pysanka__speech__system_open)) }
            }
        }
    }
}
