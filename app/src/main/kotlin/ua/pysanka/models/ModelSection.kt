package ua.pysanka.models

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import org.florisboard.lib.compose.stringRes

/**
 * Pysanka: getting a model on a settings screen (download in the browser, then pick the file; only
 * the exact published file is accepted) and removing it. [ready]: what to show while it is there.
 * [step1] and [deleteAsk] get the model's size: in GB for a model of 1 GB or more, else in MB.
 */
@Composable
fun ModelSection(
    import: ModelImport,
    @StringRes step1: Int = R.string.pysanka__ai__step1,
    @StringRes deleteAsk: Int = R.string.pysanka__ai__delete_ask,
    ready: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val model = import.model
    val store = remember { ModelStore(context, model).also { if (!import.running) it.cleanUp() } }
    val status by import.status.collectAsState()
    var state by remember { mutableStateOf(store.state()) }
    // A copy that ended (here or while this screen was gone): read the model's state again
    LaunchedEffect(status.finished) {
        state = store.state()
        if (status.originalKept) {
            Toast.makeText(context, R.string.pysanka__ai__original_kept, Toast.LENGTH_LONG).show()
            import.noteShown()
        }
    }
    val progress = status.progress ?: -1f
    // Sizes written the way the settings' language writes numbers (2,6 or 2.6)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    fun gb(v: Double) = java.text.NumberFormat.getInstance(locale).apply { maximumFractionDigits = 2 }.format(v)
    val size = if (model.sizeGb >= 1.0) gb(model.sizeGb) else java.text.NumberFormat.getInstance(locale).format(Math.round(model.sizeGb * 1000))
    // Off unless chosen: the same file may serve another app too
    var deleteOriginal by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) import.start(context, uri, deleteOriginal)
    }

    when (val s = state) {
        is ModelStore.State.Ready -> {
            Text(stringRes(R.string.pysanka__ai__ready, "size" to s.sizeMb.toString()), style = MaterialTheme.typography.titleMedium)
            ready()
            var asking by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { asking = true }) {
                Text(stringRes(R.string.pysanka__ai__delete))
            }
            if (asking) {
                ua.pysanka.settings.ConfirmDialog(
                    stringRes(deleteAsk, "size" to size),
                    onConfirm = { store.delete(); state = store.state() },
                    onDismiss = { asking = false },
                )
            }
        }
        ModelStore.State.Missing -> {
            // A phone with less memory than the AI model needs: said before a 2.6 GB download
            val ramGb = if (model !== PinnedModel.GEMMA) Double.MAX_VALUE else remember {
                val info = android.app.ActivityManager.MemoryInfo()
                (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(info)
                info.totalMem / 1_000_000_000.0
            }
            // Android reports less than the marketed size (a 6 GB phone says about 5.3–5.6)
            if (ramGb < 5.0) {
                Text(
                    stringRes(R.string.pysanka__ai__low_memory, "ram" to gb(ramGb)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                stringRes(step1, "file" to model.downloadName, "size" to size),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(model.download)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { Toast.makeText(context, R.string.pysanka__ai__no_browser, Toast.LENGTH_LONG).show() }
                }) { Text(stringRes(R.string.pysanka__ai__download)) }
                TextButton(onClick = {
                    ua.pysanka.privacy.Clip.copy(context, model.download)
                }) { Text(stringRes(R.string.pysanka__ai__copy_link)) }
            }
            Text(stringRes(R.string.pysanka__ai__step2), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = deleteOriginal, onCheckedChange = { deleteOriginal = it })
                Text(stringRes(R.string.pysanka__ai__delete_original), style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = { pick.launch(arrayOf("*/*")) }, enabled = progress < 0f) {
                Text(stringRes(R.string.pysanka__ai__pick))
            }
            if (progress >= 0f) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(stringRes(R.string.pysanka__ai__checking), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    status.error?.let {
        Text(
            stringRes(it, "file" to model.downloadName, "size" to gb(model.spaceGb)),
            color = MaterialTheme.colorScheme.error,
        )
    }
}
