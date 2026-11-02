package ua.pysanka.voice

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.patrickgold.jetpref.datastore.model.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggChip
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

/**
 * Pysanka: dictation in place of the keys (see [VoiceInput]): listening (how long, how loud), then
 * «Готово» recognises and puts the text at the cursor; «Скасувати» or ✕ drops it. Leaving the panel
 * (another field, the keyboard hidden) drops it too.
 */
@Composable
fun VoicePanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val state by VoiceInput.state.collectAsState()
    val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
    val autoStop by prefs.pysankaVoice.autoStop.observeAsState()
    fun close() {
        VoiceInput.cancel()
        keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
    }
    DisposableEffect(Unit) { onDispose { VoiceInput.cancel() } }

    SnyggColumn(modifier = modifier.fillMaxWidth().height(FlorisImeSizing.imeUiHeight())) {
        SnyggRow(
            FlorisImeUi.ClipboardHeader.elementName,
            modifier = Modifier.fillMaxWidth().height(FlorisImeSizing.smartbarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val size = Modifier.sizeIn(maxHeight = FlorisImeSizing.smartbarHeight).aspectRatio(1f)
            SnyggIcon(imageVector = Icons.Default.KeyboardVoice, modifier = size.padding(10.dp))
            SnyggText(FlorisImeUi.ClipboardHeaderText.elementName, modifier = Modifier.weight(1f), text = stringRes(R.string.pysanka__voice__title))
            SnyggIconButton(FlorisImeUi.ClipboardHeaderButton.elementName, onClick = { close() }, modifier = size) {
                SnyggIcon(imageVector = Icons.Default.Close, contentDescription = stringRes(R.string.pysanka__voice__cancel))
            }
        }
        SnyggBox(FlorisImeUi.ClipboardContent.elementName, modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                when (val s = state) {
                    is VoiceInput.State.Listening -> {
                        // The microphone grows with the voice, so it is plain that it hears; the ring
                        // around it empties as the time for one dictation runs out
                        val ink = androidx.compose.material3.LocalContentColor.current
                        val left = (1f - s.seconds / VoiceService.MAX_SECONDS).coerceIn(0f, 1f)
                        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                            androidx.compose.foundation.Canvas(Modifier.size(96.dp)) {
                                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                drawCircle(ink.copy(alpha = 0.18f), style = stroke, radius = size.minDimension / 2 - stroke.width)
                                val inset = stroke.width
                                drawArc(
                                    ink, startAngle = -90f, sweepAngle = 360f * left, useCenter = false, style = stroke,
                                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                                    size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
                                )
                            }
                            SnyggIcon(
                                imageVector = Icons.Default.KeyboardVoice,
                                modifier = Modifier.size(40.dp).scale(1f + (s.level * 3f).coerceAtMost(0.6f)),
                            )
                        }
                        val secs = s.seconds.toInt()
                        SnyggText(
                            text = stringRes(
                                if (s.heard) R.string.pysanka__voice__listening else R.string.pysanka__voice__speak,
                                "time" to "%d:%02d".format(secs / 60, secs % 60),
                            ),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip(stringRes(R.string.pysanka__voice__cancel)) { close() }
                            Chip(stringRes(R.string.pysanka__voice__done), primary = true) { VoiceInput.finish() }
                        }
                        if (autoStop) SnyggText(text = stringRes(R.string.pysanka__voice__pause_hint))
                    }
                    VoiceInput.State.Recognising -> {
                        SnyggText(text = stringRes(R.string.pysanka__voice__recognising))
                        Chip(stringRes(R.string.pysanka__voice__cancel)) { close() }
                    }
                    is VoiceInput.State.Failed -> {
                        SnyggText(text = stringRes(s.message))
                        Chip(stringRes(R.string.pysanka__voice__close), primary = true) { close() }
                    }
                    // Done (the text is in the field) or not started: back to the keys
                    VoiceInput.State.Idle -> androidx.compose.runtime.LaunchedEffect(Unit) {
                        keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, primary: Boolean = false, onClick: () -> Unit) {
    SnyggChip(
        FlorisImeUi.ClipboardFilterChip.elementName,
        attributes = mapOf("state" to if (primary) "active" else "inactive"),
        onClick = onClick,
        text = text,
    )
}

/** Pysanka's dictation settings, from the keyboard (when something is missing). */
fun openVoiceSettings(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("ui://pysanka/settings/pysanka/voice")).apply {
        setPackage(context.packageName)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    runCatching { context.startActivity(intent) }
}
