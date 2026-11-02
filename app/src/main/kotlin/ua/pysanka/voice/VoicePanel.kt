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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.editor.OperationUnit
import dev.patrickgold.florisboard.ime.media.emoji.Emoji
import dev.patrickgold.florisboard.ime.media.emoji.EmojiHistoryHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * Pysanka: dictation in place of the keys (see [VoiceInput]), as in WhisperPlus: the big microphone
 * in the middle listens (how long, how loud; a tap or «Готово» recognises, the text goes to the
 * cursor), and the panel stays for the next sentence. Beside it: punctuation on the left, ⌫ (held:
 * repeats), space and a new line on the right, and recent emoji below. ⌨ goes back to the keys.
 * Leaving the panel (another field, the keyboard hidden) drops what is being said.
 */
@Composable
fun VoicePanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val state by VoiceInput.state.collectAsState()
    val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
    val autoStop by prefs.pysankaVoice.autoStop.observeAsState()
    val history by prefs.emoji.historyData.observeAsState()
    val scope = rememberCoroutineScope()
    fun close() {
        VoiceInput.cancel()
        keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
    }
    fun mic() = when (state) {
        is VoiceInput.State.Listening -> VoiceInput.finish()
        VoiceInput.State.Recognising -> Unit
        else -> keyboardManager.pysankaDictate()
    }
    fun punctuation(mark: String) {
        // «слово .» → «слово.»: the next dictation adds its own space
        if (editorInstance.activeContent.textBeforeSelection.endsWith(' ')) {
            editorInstance.deleteBackwards(OperationUnit.CHARACTERS)
        }
        editorInstance.commitText(mark)
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
                SnyggIcon(imageVector = Icons.Default.Keyboard, contentDescription = stringRes(R.string.pysanka__voice__keys))
            }
        }
        SnyggBox(FlorisImeUi.ClipboardContent.elementName, modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Punctuation
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (pair in listOf(listOf(".", ","), listOf("?", "!"))) {
                            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (mark in pair) {
                                    Key(Modifier.weight(1f), mark) { punctuation(mark) }
                                }
                            }
                        }
                    }
                    // The microphone
                    Column(
                        Modifier.weight(1.5f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        val s = state
                        val ink = androidx.compose.material3.LocalContentColor.current
                        val listening = s as? VoiceInput.State.Listening
                        val micLabel = stringRes(if (listening != null) R.string.pysanka__voice__done else R.string.pysanka__voice__speak_again)
                        Box(
                            Modifier.weight(1f, fill = false).sizeIn(maxWidth = 88.dp, maxHeight = 88.dp).aspectRatio(1f)
                                .clip(CircleShape)
                                .clickable(enabled = s !is VoiceInput.State.Recognising, onClickLabel = micLabel, role = Role.Button) { mic() },
                            contentAlignment = Alignment.Center,
                        ) {
                            // The ring empties as the time for one dictation runs out; the
                            // microphone grows with the voice, so it is plain that it hears
                            val left = listening?.let { (1f - it.seconds / VoiceService.MAX_SECONDS).coerceIn(0f, 1f) } ?: 1f
                            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                val inset = stroke.width
                                val box = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset)
                                val at = androidx.compose.ui.geometry.Offset(inset, inset)
                                drawCircle(ink.copy(alpha = 0.18f), style = stroke, radius = size.minDimension / 2 - stroke.width)
                                if (listening != null) {
                                    drawArc(ink, -90f, 360f * left, useCenter = false, style = stroke, topLeft = at, size = box)
                                }
                            }
                            SnyggIcon(
                                imageVector = Icons.Default.KeyboardVoice,
                                contentDescription = micLabel,
                                modifier = Modifier.fillMaxSize(0.45f).scale(1f + ((listening?.level ?: 0f) * 3f).coerceAtMost(0.6f))
                                    .alpha(if (s is VoiceInput.State.Recognising) 0.4f else 1f),
                            )
                        }
                        when (s) {
                            is VoiceInput.State.Listening -> {
                                val secs = s.seconds.toInt()
                                SnyggText(
                                    text = stringRes(
                                        if (s.heard) R.string.pysanka__voice__listening else R.string.pysanka__voice__speak,
                                        "time" to "%d:%02d".format(secs / 60, secs % 60),
                                    ),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Chip(stringRes(R.string.pysanka__voice__cancel)) { VoiceInput.cancel() }
                                    Chip(stringRes(R.string.pysanka__voice__done), primary = true) { VoiceInput.finish() }
                                }
                            }
                            VoiceInput.State.Recognising -> SnyggText(text = stringRes(R.string.pysanka__voice__recognising))
                            is VoiceInput.State.Failed -> SnyggText(text = stringRes(s.message))
                            VoiceInput.State.Idle -> SnyggText(text = stringRes(R.string.pysanka__voice__tap_to_speak))
                        }
                        if (autoStop && listening != null) SnyggText(text = stringRes(R.string.pysanka__voice__pause_hint))
                    }
                    // Editing
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Key(Modifier.weight(1f), icon = Icons.AutoMirrored.Outlined.Backspace, label = stringRes(R.string.pysanka__voice__backspace), repeat = true) {
                            editorInstance.deleteBackwards(OperationUnit.CHARACTERS)
                        }
                        Key(Modifier.weight(1f), icon = Icons.Default.SpaceBar, label = stringRes(R.string.pysanka__voice__space)) {
                            editorInstance.commitText(" ")
                        }
                        Key(Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.KeyboardReturn, label = stringRes(R.string.pysanka__voice__new_line)) {
                            editorInstance.commitText("\n")
                        }
                    }
                }
                // Recent emoji (the keyboard's own history), the usual ones until there are enough
                val emoji = remember(history) {
                    (history.pinned + history.recent).map { it.value }.plus(COMMON_EMOJI).distinct().take(8)
                }
                Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (e in emoji) {
                        Key(Modifier.weight(1f), e) {
                            editorInstance.commitText(e)
                            scope.launch { EmojiHistoryHelper.markEmojiUsed(prefs, Emoji(e, "", emptyList())) }
                        }
                    }
                }
            }
        }
    }
}

private val COMMON_EMOJI = listOf("🙂", "😂", "❤️", "👍", "🙏", "🔥", "😢", "🎉")

/** A key of the panel: text or an icon (its name read out); [repeat]: held, it repeats like ⌫. */
@Composable
private fun Key(
    modifier: Modifier,
    text: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    label: String = text.orEmpty(),
    repeat: Boolean = false,
    onClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val action by androidx.compose.runtime.rememberUpdatedState(onClick)
    val press = if (repeat) {
        Modifier
            .semantics { onClick(label) { action(); true }; role = Role.Button }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    action()
                    val job = scope.launch {
                        delay(400)
                        while (true) {
                            action()
                            delay(60)
                        }
                    }
                    tryAwaitRelease()
                    job.cancel()
                })
            }
    } else {
        Modifier.clickable(onClickLabel = label, role = Role.Button) { action() }
    }
    SnyggBox(
        FlorisImeUi.SmartbarActionTile.elementName,
        modifier = modifier.fillMaxHeight(),
        clickAndSemanticsModifier = press,
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            SnyggIcon(imageVector = icon, contentDescription = label)
        } else {
            SnyggText(FlorisImeUi.SmartbarActionTileText.elementName, text = text.orEmpty())
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
