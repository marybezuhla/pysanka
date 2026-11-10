package ua.pysanka.speech

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.AppTheme
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.stringRes

/**
 * Pysanka: «Прочитати вголос (Писанка)» in the text selection menu of any app. In the keyboard's
 * process it only reads the settings and hands the text to [ReadAloudActivity] in the ":speech"
 * process, where the voices are (they never weigh on the keyboard).
 */
class ReadAloudEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        if (intent.action != Intent.ACTION_PROCESS_TEXT || text.isEmpty()) {
            finish()
            return
        }
        lifecycleScope.launch {
            // From the selection menu Pysanka may be starting cold: the settings are read once loaded
            kotlinx.coroutines.withTimeoutOrNull(2000L) {
                dev.patrickgold.florisboard.FlorisApplication.getHack().preferenceStoreLoaded.first { it }
            }
            startActivity(ReadAloudActivity.intent(this@ReadAloudEntryActivity, text))
            finish()
        }
    }
}

/**
 * Pysanka: the reading window, over the app the text came from: the text with the sentence being
 * read marked, ⏮ ⏯ ⏭, the pace and ✕. Without «Читати у фоні» reading ends when the window goes;
 * with it, reading goes on and the notification has the controls.
 */
class ReadAloudActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_SECURE, false)) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (android.os.Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        // A new text starts reading; opened from the notification, the reading under way is shown
        if (savedInstanceState == null && text.isNotEmpty()) {
            ReadAloud.start(this, text, intent.getIntExtra(EXTRA_SPEED, 100) / 100f, intent.getBooleanExtra(EXTRA_BACKGROUND, false))
        } else if (savedInstanceState == null && !ReadAloud.state.value.active) {
            finish()
            return
        }
        setContent {
            ProvideLocalizedResources(ua.pysanka.settings.AppLanguage.context(this, intent.getStringExtra(EXTRA_LANGUAGE)), appName = R.string.app_name) {
                FlorisAppTheme(theme = AppTheme.AUTO, systemColorsOverride = intent.getBooleanExtra(EXTRA_SYSTEM_COLORS, false)) {
                    ReadAloudScreen(
                        onSettings = { openSettings(this); finish() },
                        onClose = { ReadAloud.stop(); finish() },
                        onAway = { finish() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        if (text.isNotEmpty()) {
            setIntent(intent)
            ReadAloud.start(this, text, intent.getIntExtra(EXTRA_SPEED, 100) / 100f, intent.getBooleanExtra(EXTRA_BACKGROUND, false))
        }
    }

    override fun onStop() {
        super.onStop()
        // Without reading in the background, the window gone ends reading (turning the phone does not)
        if (!isChangingConfigurations && !ReadAloud.state.value.background) {
            ReadAloud.stop()
            finish()
        }
    }

    companion object {
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_SPEED = "speed"
        private const val EXTRA_BACKGROUND = "background"
        private const val EXTRA_SECURE = "secure"
        private const val EXTRA_LANGUAGE = "language"
        private const val EXTRA_SYSTEM_COLORS = "system_colors"

        /** Read in the keyboard's process (the ":speech" process does not touch the settings). */
        fun intent(context: Context, text: String?): Intent {
            val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
            val loaded = runCatching { dev.patrickgold.florisboard.FlorisApplication.getHack().preferenceStoreLoaded.value }.getOrDefault(false)
            val secure = !loaded || runCatching { prefs.pysankaOcr.blockScreenshots.get() }.getOrDefault(true)
            return Intent(context, ReadAloudActivity::class.java)
                .putExtra(EXTRA_TEXT, text?.take(ReadAloud.MAX_CHARS))
                .putExtra(EXTRA_SPEED, runCatching { prefs.pysankaSpeech.speed.get() }.getOrDefault(100))
                .putExtra(EXTRA_BACKGROUND, runCatching { prefs.pysankaSpeech.background.get() }.getOrDefault(false))
                .putExtra(EXTRA_SECURE, secure)
                .putExtra(EXTRA_LANGUAGE, runCatching { prefs.other.settingsLanguage.get() }.getOrDefault("auto"))
                .putExtra(EXTRA_SYSTEM_COLORS, runCatching { prefs.other.pysankaSystemColors.get() }.getOrDefault(false))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        /** Pysanka's reading settings (in the settings' own process). */
        fun openSettings(context: Context) {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("ui://pysanka/settings/pysanka/speech")).apply {
                setPackage(context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            runCatching { context.startActivity(intent) }
        }
    }
}

private val SPEEDS = listOf(0.8f, 1f, 1.2f, 1.5f)

@Composable
private fun ReadAloudScreen(onSettings: () -> Unit, onClose: () -> Unit, onAway: () -> Unit) {
    val s by ReadAloud.state.collectAsState()
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onAway() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).navigationBarsPadding().padding(8.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringRes(R.string.pysanka__speech__title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = stringRes(R.string.pysanka__speech__stop)) }
                }
                // The text, the sentence being read marked and kept in view
                val scroll = rememberScrollState()
                var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                val mark = MaterialTheme.colorScheme.primaryContainer
                val markText = MaterialTheme.colorScheme.onPrimaryContainer
                val shown = remember(s.text, s.start, s.end, mark) {
                    buildAnnotatedString {
                        val a = s.start.coerceIn(0, s.text.length)
                        val b = s.end.coerceIn(a, s.text.length)
                        append(s.text.substring(0, a))
                        withStyle(SpanStyle(background = mark, color = markText)) { append(s.text.substring(a, b)) }
                        append(s.text.substring(b))
                    }
                }
                LaunchedEffect(s.start, layout) {
                    val l = layout ?: return@LaunchedEffect
                    if (s.start <= l.layoutInput.text.length) {
                        val top = l.getLineTop(l.getLineForOffset(s.start.coerceAtMost(l.layoutInput.text.length))).toInt()
                        scroll.animateScrollTo((top - 48).coerceAtLeast(0))
                    }
                }
                Box(Modifier.weight(1f, fill = false).heightIn(max = 300.dp).verticalScroll(scroll)) {
                    Text(shown, style = MaterialTheme.typography.bodyLarge, onTextLayout = { layout = it })
                }
                when {
                    s.notice != null -> Column(Modifier.padding(top = 8.dp)) {
                        Text(
                            stringRes(
                                when (s.notice) {
                                    ReadAloud.NOTICE_NO_UK -> R.string.pysanka__speech__no_uk
                                    ReadAloud.NOTICE_NO_EN -> R.string.pysanka__speech__no_en
                                    else -> R.string.pysanka__speech__no_sound
                                },
                            ),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (s.notice == ReadAloud.NOTICE_NO_UK || s.notice == ReadAloud.NOTICE_NO_EN) {
                            TextButton(onClick = onSettings) { Text(stringRes(R.string.pysanka__speech__open_settings)) }
                        }
                    }
                    s.preparing -> Text(stringRes(R.string.pysanka__speech__preparing), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { ReadAloud.skip(-1) }) { Icon(Icons.Default.SkipPrevious, contentDescription = stringRes(R.string.pysanka__speech__previous)) }
                    FilledIconButton(onClick = { ReadAloud.toggle() }) {
                        Icon(
                            if (s.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringRes(if (s.playing) R.string.pysanka__speech__pause else R.string.pysanka__speech__play),
                        )
                    }
                    IconButton(onClick = { ReadAloud.skip(1) }) { Icon(Icons.Default.SkipNext, contentDescription = stringRes(R.string.pysanka__speech__next)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    for (v in SPEEDS) {
                        FilterChip(s.speed == v, { ReadAloud.setSpeed(v) }, { Text(speedLabel(v)) })
                    }
                }
            }
        }
    }
}

/** 1,2× in the settings' language. */
@Composable
internal fun speedLabel(v: Float): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return java.text.NumberFormat.getInstance(locale).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(v) + "×"
}

/**
 * Pysanka: a word said aloud (🔊 in the translation): started from the keyboard's process, said in
 * the ":speech" process. Not in the foreground: it ends with the word.
 */
class SpeakService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val word = intent?.getStringExtra(EXTRA_WORD).orEmpty().take(200)
        if (word.isNotBlank()) ReadAloud.start(this, word, intent?.getIntExtra(EXTRA_SPEED, 100)?.div(100f) ?: 1f, background = false)
        // Gone once the word has been said (or could not be)
        ReadAloud.whenIdle { stopSelf(startId) }
        return START_NOT_STICKY
    }

    companion object {
        private const val EXTRA_WORD = "word"
        private const val EXTRA_SPEED = "speed"

        fun say(context: Context, word: String) {
            val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
            val speed = runCatching { prefs.pysankaSpeech.speed.get() }.getOrDefault(100)
            runCatching { context.startService(Intent(context, SpeakService::class.java).putExtra(EXTRA_WORD, word).putExtra(EXTRA_SPEED, speed)) }
        }
    }
}

/**
 * Pysanka: «Прочитати вголос (Писанка)» in the selection menu and 🔊 in the translation only while a
 * voice is downloaded; Pysanka offered to the phone as a voice only when the person turned it on
 * and a voice is there.
 */
object SpeechEntry {
    fun anyVoice(context: Context) = SpeechEngine.has(context, "uk") || SpeechEngine.has(context, "en")

    fun sync(context: Context) {
        val voice = anyVoice(context)
        val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
        val system = runCatching { prefs.pysankaSpeech.systemVoice.get() }.getOrDefault(false)
        set(context, ReadAloudEntryActivity::class.java, voice)
        set(context, PysankaTtsService::class.java, voice && system)
    }

    private fun set(context: Context, cls: Class<*>, on: Boolean) {
        val pm = context.packageManager
        val component = ComponentName(context, cls)
        val wanted = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (pm.getComponentEnabledSetting(component) != wanted) {
            runCatching { pm.setComponentEnabledSetting(component, wanted, PackageManager.DONT_KILL_APP) }
        }
    }
}
