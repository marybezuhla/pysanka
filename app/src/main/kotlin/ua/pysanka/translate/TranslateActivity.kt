package ua.pysanka.translate

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.AppTheme
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import org.florisboard.lib.compose.stringRes
import ua.pysanka.privacy.followScreenshotBlock
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * Pysanka: «Перекласти (Писанка)» in the text selection menu of any app: the selected word,
 * translated offline (see [Translations]), on a card over the app. A tap on a translation puts it
 * in place of the selection, if the app allows that; the copy button copies it. Only plain text
 * is taken, and only a word or a short expression ([Translations.MAX_CHARS]).
 */
class TranslateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        followScreenshotBlock()
        if (android.os.Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        if (intent.action != Intent.ACTION_PROCESS_TEXT || text.isEmpty()) {
            finish()
            return
        }
        val canReplace = !intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        setContent {
            val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
            val language = runCatching { prefs.other.settingsLanguage.get() }.getOrNull()
            org.florisboard.lib.compose.ProvideLocalizedResources(ua.pysanka.settings.AppLanguage.context(this, language), appName = R.string.app_name) {
                FlorisAppTheme(theme = AppTheme.AUTO) {
                    TranslateScreen(
                        text = text.take(Translations.MAX_CHARS + 1),
                        canReplace = canReplace,
                        onReplace = { word ->
                            setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, word))
                            finish()
                        },
                        onClose = { finish() },
                    )
                }
            }
        }
    }
}

private enum class Pick { FROM, TO }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranslateScreen(text: String, canReplace: Boolean, onReplace: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val ui = remember { Locale.forLanguageTag(TranslateChoice.ownLanguage(context)) }
    var from by remember { mutableStateOf<String?>(null) }
    var to by remember { mutableStateOf(TranslateChoice.ownLanguage(context)) }
    var lookup by remember { mutableStateOf<Translations.Lookup?>(null) }
    var loading by remember { mutableStateOf(true) }
    var picking by remember { mutableStateOf<Pick?>(null) }
    val tooLong = text.length > Translations.MAX_CHARS

    LaunchedEffect(Unit) {
        // From the selection menu the app may be starting cold: the settings (the language to
        // translate into, the keyboard's languages) are read only once they are loaded
        kotlinx.coroutines.withTimeoutOrNull(2000L) {
            dev.patrickgold.florisboard.FlorisApplication.getHack().preferenceStoreLoaded.first { it }
        }
        if (!tooLong) {
            val s = TranslateChoice.start(context, text)
            from = s.from
            to = s.to
            lookup = s.lookup
        }
        loading = false
    }
    LaunchedEffect(from, to) {
        val f = from
        if (f != null && (lookup?.from != f || lookup?.to != to)) {
            loading = true
            try {
                lookup = TranslateChoice.lookup(context, text, f, to)
            } finally {
                loading = false
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).navigationBarsPadding().padding(8.dp)
                // Taps on the card stay on it
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                val lemma = lookup?.lemmas?.firstOrNull()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (lemma != null && lemma != Translations.key(text)) "$text → $lemma" else text,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    // Said aloud by Pysanka's voice for its language, when one is downloaded
                    from?.let { Speak(text, it) }
                }
                val f = from
                if (f != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(picking == Pick.FROM, { picking = if (picking == Pick.FROM) null else Pick.FROM }, { Text(Translations.languageName(f, ui)) })
                        // Not a swap: the word stays what it is, in its own language
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(horizontal = 4.dp))
                        FilterChip(picking == Pick.TO, { picking = if (picking == Pick.TO) null else Pick.TO }, { Text(Translations.languageName(to, ui)) })
                    }
                }
                val p = picking
                when {
                    p != null -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (lang in Translations.LANGUAGES) {
                            FilterChip(lang == (if (p == Pick.FROM) f else to), {
                                // The same language on both sides: they change places
                                if (p == Pick.FROM) {
                                    if (lang == to) to = f ?: to
                                    from = lang
                                } else {
                                    if (lang == f) from = to
                                    to = lang
                                }
                                picking = null
                            }, { Text(Translations.languageName(lang, ui)) })
                        }
                    }
                    tooLong -> Note(stringRes(R.string.pysanka__translate__too_long))
                    loading -> Note(stringRes(R.string.pysanka__translate__looking))
                    f == null -> Note(stringRes(R.string.pysanka__translate__unknown, "word" to text))
                    lookup?.results.isNullOrEmpty() -> Note(
                        stringRes(R.string.pysanka__translate__none, "word" to text, "language" to Translations.languageName(to, ui)),
                    )
                    else -> {
                        val results = lookup!!.results
                        if (results.first().viaEnglish) Note(stringRes(R.string.pysanka__translate__via_english))
                        if (canReplace) Note(stringRes(R.string.pysanka__translate__tap_to_replace))
                        for (r in results) {
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = canReplace) { onReplace(matchCase(text, r.word)) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.word, style = MaterialTheme.typography.titleMedium)
                                    if (!r.hint.isNullOrBlank()) {
                                        Text(r.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Speak(r.word, to)
                                IconButton(onClick = { copy(context, r.word) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = stringRes(R.string.pysanka__translate__copy))
                                }
                            }
                        }
                    }
                }
                Text(
                    stringRes(R.string.pysanka__translate__source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** 🔊: the word said by Pysanka's voice (Ukrainian or English, when downloaded); nothing otherwise. */
@Composable
private fun Speak(word: String, language: String) {
    val context = LocalContext.current
    val can = remember(language) { language in setOf("uk", "en") && ua.pysanka.speech.SpeechEngine.has(context, language) }
    if (!can) return
    IconButton(onClick = { ua.pysanka.speech.SpeakService.say(context, word) }) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringRes(R.string.pysanka__speech__say, "word" to word))
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
}

/** A capital letter stays a capital in the translation. */
internal fun matchCase(original: String, translation: String): String =
    if (original.firstOrNull()?.isUpperCase() == true) translation.replaceFirstChar { it.uppercaseChar() } else translation

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(android.content.ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText(null, text))
    // Android 13 shows its own note of what was copied
    if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, R.string.pysanka__translate__copied, Toast.LENGTH_SHORT).show()
}

/**
 * Pysanka: «Перекласти (Писанка)» in the selection menu as the setting says (and only with the
 * dictionaries in the app).
 */
object TranslateEntry {
    fun sync(context: Context) {
        val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
        val on = prefs.pysankaTranslate.inSelectionMenu.get() && Translations.available(context)
        val pm = context.packageManager
        val component = ComponentName(context, TranslateActivity::class.java)
        val wanted = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (pm.getComponentEnabledSetting(component) != wanted) {
            runCatching { pm.setComponentEnabledSetting(component, wanted, PackageManager.DONT_KILL_APP) }
        }
    }
}
