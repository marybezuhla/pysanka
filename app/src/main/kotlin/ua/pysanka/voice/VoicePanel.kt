package ua.pysanka.voice

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.editor.OperationUnit
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.media.emoji.Emoji
import dev.patrickgold.florisboard.ime.media.emoji.EmojiHistoryHelper
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggChip
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggText

/**
 * Pysanka: dictation in place of the keys (see [VoiceInput]), drawn with the keyboard's own keys:
 *
 * - one line of state on top («Говоріть…», the time, what to do next), with ✕ while listening and
 *   «Прибрати» after a text went in (takes the whole dictated piece back);
 * - the microphone in the middle, coloured like Enter, with waves that follow the voice and a ring
 *   for the time left. A tap listens and a tap ends it, or, if the person chose to hold it, it
 *   listens while held and the release ends it;
 * - ⌨ (back to the keys) on its left, ⌫ (repeats when held) on its right;
 * - a row of , . ? ! space ↵ below, and the person's pinned and recent emoji at the bottom.
 *
 * The panel stays for the next sentence. Leaving it (another field, the keyboard hidden) drops what
 * is being said.
 */
@Composable
fun VoicePanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val state by VoiceInput.state.collectAsState()
    val inserted by VoiceInput.inserted.collectAsState()
    val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
    val autoStop by prefs.pysankaVoice.autoStop.observeAsState()
    val hold by prefs.pysankaVoice.holdToTalk.observeAsState()
    val history by prefs.emoji.historyData.observeAsState()
    val scope = rememberCoroutineScope()
    // Released too soon to have said anything (hold to speak): a hint instead of «nothing heard»
    var tooShort by remember { mutableStateOf(false) }

    /** Any key of the panel: the text changes, so the last piece can no longer be taken back. */
    fun edit(action: () -> Unit) {
        VoiceInput.inserted.value = null
        tooShort = false
        action()
    }
    fun listen() {
        tooShort = false
        keyboardManager.pysankaDictate(listen = true)
    }
    fun punctuate(mark: String) = edit {
        // «слово .» → «слово.»: the next dictation adds its own space
        if (editorInstance.activeContent.textBeforeSelection.endsWith(' ')) {
            editorInstance.deleteBackwards(OperationUnit.CHARACTERS)
        }
        editorInstance.commitText(mark)
    }
    fun undo() {
        val last = VoiceInput.inserted.value ?: return
        VoiceInput.inserted.value = null
        // Only if the cursor is still right after it (not moved elsewhere), nothing is selected and
        // the text there is still the same
        val ic = FlorisImeService.currentInputConnection() ?: return
        val sel = editorInstance.activeContent.selection
        if (sel.isSelectionMode || sel.start != last.end) return
        if (ic.getTextBeforeCursor(last.text.length, 0)?.toString() == last.text) ic.deleteSurroundingText(last.text.length, 0)
    }
    DisposableEffect(Unit) {
        onDispose {
            VoiceInput.cancel()
            VoiceInput.inserted.value = null
        }
    }

    VoicePanelView(
        state = state,
        canTakeBack = inserted != null,
        autoStop = autoStop,
        hold = hold,
        tooShort = tooShort,
        emoji = remember(history) {
            (history.pinned + history.recent).map { it.value }.plus(COMMON_EMOJI).distinct().take(16)
        },
        actions = object : VoiceActions {
            override fun keys() {
                VoiceInput.cancel()
                keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
            }
            override fun tap() = when (VoiceInput.state.value) {
                is VoiceInput.State.Listening -> VoiceInput.finish()
                VoiceInput.State.Recognising -> Unit
                else -> listen()
            }
            override fun holdStart() {
                // Already listening (a screen reader's double tap) or recognising: nothing new
                if (VoiceInput.state.value == VoiceInput.State.Idle || VoiceInput.state.value is VoiceInput.State.Failed) listen()
            }
            override fun holdEnd(long: Boolean) {
                if (VoiceInput.state.value is VoiceInput.State.Listening) {
                    if (long) VoiceInput.finish() else { VoiceInput.cancel(); tooShort = true }
                }
            }
            override fun cancel() = VoiceInput.cancel()
            override fun takeBack() = undo()
            override fun backspace() = edit { editorInstance.deleteBackwards(OperationUnit.CHARACTERS) }
            override fun punctuation(mark: String) = punctuate(mark)
            override fun space() = edit { editorInstance.commitText(" ") }
            override fun newLine() = edit { editorInstance.commitText("\n") }
            override fun emoji(e: String) {
                edit { editorInstance.commitText(e) }
                scope.launch { EmojiHistoryHelper.markEmojiUsed(prefs, Emoji(e, "", emptyList())) }
            }
        },
        modifier = modifier.fillMaxWidth().height(FlorisImeSizing.imeUiHeight()),
    )
}

/** What the panel's controls do (the keyboard's, or nothing in the screenshots). */
interface VoiceActions {
    fun keys()
    fun tap()
    fun holdStart()
    fun holdEnd(long: Boolean)
    fun cancel()
    fun takeBack()
    fun backspace()
    fun punctuation(mark: String)
    fun space()
    fun newLine()
    fun emoji(e: String)
}

/** The panel itself, from its state only (drawn on the computer for review, too). */
@Composable
fun VoicePanelView(
    state: VoiceInput.State,
    canTakeBack: Boolean,
    autoStop: Boolean,
    hold: Boolean,
    tooShort: Boolean,
    emoji: List<String>,
    actions: VoiceActions,
    modifier: Modifier = Modifier,
) {
    SnyggColumn(
        FlorisImeUi.ClipboardContent.elementName,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // State
            Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val s = state
                val line = when (s) {
                    is VoiceInput.State.Listening -> {
                        val secs = s.seconds.toInt()
                        val time = "%d:%02d".format(secs / 60, secs % 60)
                        when {
                            hold -> stringRes(R.string.pysanka__voice__release_hint, "time" to time)
                            !s.heard -> stringRes(R.string.pysanka__voice__speak, "time" to time)
                            autoStop -> stringRes(R.string.pysanka__voice__listening_pause, "time" to time)
                            else -> stringRes(R.string.pysanka__voice__listening, "time" to time)
                        }
                    }
                    VoiceInput.State.Recognising -> stringRes(R.string.pysanka__voice__recognising)
                    is VoiceInput.State.Failed -> stringRes(s.message)
                    VoiceInput.State.Idle -> stringRes(
                        when {
                            tooShort -> R.string.pysanka__voice__hold_longer
                            hold -> R.string.pysanka__voice__hold_to_speak
                            else -> R.string.pysanka__voice__tap_to_speak
                        },
                    )
                }
                SnyggText(
                    FlorisImeUi.ClipboardHeaderText.elementName,
                    // Read out when it changes («Розпізнаю…», «Нічого не чути…»)
                    modifier = Modifier.weight(1f).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
                    text = line,
                )
                when {
                    s is VoiceInput.State.Listening || s == VoiceInput.State.Recognising ->
                        Chip(Icons.Default.Close, stringRes(R.string.pysanka__voice__cancel)) { actions.cancel() }
                    canTakeBack ->
                        Chip(Icons.AutoMirrored.Filled.Undo, stringRes(R.string.pysanka__voice__take_back)) { actions.takeBack() }
                }
            }
            // ⌨ · microphone · ⌫
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Key(Modifier.width(72.dp).fillMaxHeight(0.62f), icon = Icons.Default.Keyboard, label = stringRes(R.string.pysanka__voice__keys)) { actions.keys() }
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    val d = minOf(maxWidth, maxHeight * 0.92f, 120.dp)
                    Microphone(
                        diameter = d,
                        state = state,
                        hold = hold,
                        onTap = actions::tap,
                        onHoldStart = actions::holdStart,
                        onHoldEnd = actions::holdEnd,
                        onHoldCancel = actions::cancel,
                    )
                }
                Key(Modifier.width(72.dp).fillMaxHeight(0.62f), icon = Icons.AutoMirrored.Outlined.Backspace, label = stringRes(R.string.pysanka__voice__backspace), repeat = true) { actions.backspace() }
            }
            // , . ? !  space  ↵
            Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (mark in listOf(",", ".", "?", "!")) Key(Modifier.weight(1f), text = mark) { actions.punctuation(mark) }
                Key(Modifier.weight(3f), icon = Icons.Default.SpaceBar, label = stringRes(R.string.pysanka__voice__space), code = KeyCode.SPACE) { actions.space() }
                Key(Modifier.weight(1.4f), icon = Icons.AutoMirrored.Filled.KeyboardReturn, label = stringRes(R.string.pysanka__voice__new_line)) { actions.newLine() }
            }
            LazyRow(Modifier.fillMaxWidth().height(48.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                items(emoji) { e ->
                    Box(
                        Modifier.fillMaxHeight().width(48.dp).clip(CircleShape).clickable(onClickLabel = e, role = Role.Button) {
                            FlorisImeService.inputFeedbackController()?.keyPress()
                            actions.emoji(e)
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(e, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}

private val COMMON_EMOJI = listOf("🙂", "😂", "❤️", "👍", "🙏", "🔥", "😢", "🎉", "😍", "🤔", "👏", "😊")

/**
 * The microphone, coloured like Enter. Listening: waves around it follow the voice and a thin ring
 * shows the time left of one dictation.
 */
@Composable
private fun Microphone(
    diameter: androidx.compose.ui.unit.Dp,
    state: VoiceInput.State,
    hold: Boolean,
    onTap: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: (long: Boolean) -> Unit,
    onHoldCancel: () -> Unit,
) {
    val listening = state as? VoiceInput.State.Listening
    val level by animateFloatAsState((listening?.level ?: 0f).coerceIn(0f, 0.3f) / 0.3f, label = "level")
    val ink = LocalContentColor.current
    val label = stringRes(
        when {
            listening != null -> R.string.pysanka__voice__done
            else -> R.string.pysanka__voice__speak_again
        },
    )
    val tap by rememberUpdatedState(onTap)
    val start by rememberUpdatedState(onHoldStart)
    val end by rememberUpdatedState(onHoldEnd)
    val cancel by rememberUpdatedState(onHoldCancel)
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        val core = diameter * 0.72f
        Canvas(Modifier.fillMaxSize()) {
            val r = core.toPx() / 2
            val room = size.minDimension / 2 - r
            if (listening != null) {
                // Waves: the louder, the wider
                drawCircle(ink.copy(alpha = 0.10f), radius = r + room * (0.35f + 0.65f * level))
                drawCircle(ink.copy(alpha = 0.14f), radius = r + room * (0.15f + 0.4f * level))
                // Time left
                val w = 3.dp.toPx()
                val left = (1f - listening.seconds / VoiceService.MAX_SECONDS).coerceIn(0f, 1f)
                val inset = w / 2
                drawArc(
                    ink.copy(alpha = 0.55f), -90f, 360f * left, useCenter = false,
                    style = Stroke(width = w, cap = StrokeCap.Round),
                    topLeft = Offset(inset, inset), size = Size(size.width - w, size.height - w),
                )
            }
        }
        val gestures = if (hold) {
            Modifier
                // Holding is hard with a screen reader: there a double tap starts and ends it
                .semantics { onClick(label) { tap(); true }; role = Role.Button; contentDescription = label }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        start()
                        // Counted from when it listens (starting can wait for the last recording to let go)
                        val at = System.currentTimeMillis()
                        // A gesture taken away (not a release): nothing is recognised
                        if (tryAwaitRelease()) end(System.currentTimeMillis() - at >= 600) else cancel()
                    })
                }
        } else {
            Modifier.clickable(onClickLabel = label, role = Role.Button) { tap() }
        }
        SnyggBox(
            FlorisImeUi.Key.elementName,
            attributes = mapOf(FlorisImeUi.Attr.Code to KeyCode.ENTER),
            selector = if (listening != null) SnyggSelector.PRESSED else SnyggSelector.NONE,
            modifier = Modifier.size(core).clip(CircleShape),
            clickAndSemanticsModifier = gestures,
            contentAlignment = Alignment.Center,
        ) {
            SnyggIcon(
                imageVector = Icons.Default.KeyboardVoice,
                contentDescription = label,
                modifier = Modifier.size(core * 0.42f).alpha(if (state == VoiceInput.State.Recognising) 0.45f else 1f),
            )
        }
    }
}

/** A key of the panel, like the keyboard's: text or an icon (its name read out); [repeat]: held, it repeats like ⌫. */
@Composable
private fun Key(
    modifier: Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    label: String = text.orEmpty(),
    code: Int = KeyCode.UNSPECIFIED,
    repeat: Boolean = false,
    onClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val action by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    val press = Modifier
        .semantics { onClick(label) { action(); true }; role = Role.Button; contentDescription = label }
        .pointerInput(repeat) {
            detectTapGestures(onPress = {
                pressed = true
                FlorisImeService.inputFeedbackController()?.keyPress()
                action()
                val job = if (repeat) {
                    scope.launch {
                        delay(400)
                        while (true) {
                            FlorisImeService.inputFeedbackController()?.keyRepeatedAction()
                            action()
                            delay(60)
                        }
                    }
                } else null
                try {
                    tryAwaitRelease()
                } finally {
                    job?.cancel()
                    pressed = false
                }
            })
        }
    SnyggBox(
        FlorisImeUi.Key.elementName,
        attributes = mapOf(FlorisImeUi.Attr.Code to code),
        selector = if (pressed) SnyggSelector.PRESSED else SnyggSelector.NONE,
        modifier = modifier.fillMaxHeight(),
        clickAndSemanticsModifier = press,
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            SnyggIcon(imageVector = icon, contentDescription = null)
        } else {
            SnyggText(text = text.orEmpty())
        }
    }
}

@Composable
private fun Chip(icon: ImageVector, text: String, onClick: () -> Unit) {
    SnyggChip(
        FlorisImeUi.ClipboardFilterChip.elementName,
        attributes = mapOf("state" to "inactive"),
        onClick = onClick,
        imageVector = icon,
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
