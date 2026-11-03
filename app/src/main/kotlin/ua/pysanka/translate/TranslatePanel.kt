package ua.pysanka.translate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import org.florisboard.lib.compose.florisVerticalScroll
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggChip
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText
import java.util.Locale

/**
 * Pysanka: the word at the cursor (or the selection), translated, in place of the keys (the
 * translate button on the bar above the keyboard). A translation tapped goes in place of the word.
 * The word is read once, when the button is pressed (see [TranslateBridge.open]).
 */
@Composable
fun TranslatePanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val word = remember { TranslateBridge.take() }
    fun close() {
        keyboardManager.activeState.imeUiMode = ImeUiMode.TEXT
    }

    val ui = remember { Locale.forLanguageTag(TranslateChoice.ownLanguage(context)) }
    var from by remember { mutableStateOf<String?>(null) }
    var to by remember { mutableStateOf(TranslateChoice.ownLanguage(context)) }
    var lookup by remember { mutableStateOf<Translations.Lookup?>(null) }
    var loading by remember { mutableStateOf(word != null) }
    var picking by remember { mutableStateOf<Picking?>(null) }

    LaunchedEffect(Unit) {
        if (word != null) {
            val s = TranslateChoice.start(context, word.text)
            from = s.from
            to = s.to
            lookup = s.lookup
            loading = false
        }
    }
    LaunchedEffect(from, to) {
        val f = from
        if (word != null && f != null && (lookup?.from != f || lookup?.to != to)) {
            loading = true
            lookup = TranslateChoice.lookup(context, word.text, f, to)
            loading = false
        }
    }

    SnyggColumn(modifier = modifier.fillMaxWidth().height(FlorisImeSizing.imeUiHeight())) {
        val headerHeight = FlorisImeSizing.smartbarHeight
        SnyggRow(
            FlorisImeUi.ClipboardHeader.elementName,
            modifier = Modifier.fillMaxWidth().height(headerHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val size = Modifier.sizeIn(maxHeight = headerHeight).aspectRatio(1f)
            SnyggIcon(imageVector = Icons.Default.Translate, modifier = size.padding(10.dp))
            val lemma = lookup?.lemmas?.firstOrNull()
            SnyggText(
                FlorisImeUi.ClipboardHeaderText.elementName,
                modifier = Modifier.weight(1f),
                text = when {
                    word == null -> stringRes(R.string.pysanka__translate__title)
                    lemma != null && lemma != Translations.key(word.text) -> "${word.text} → $lemma"
                    else -> word.text
                },
            )
            SnyggIconButton(FlorisImeUi.ClipboardHeaderButton.elementName, onClick = { close() }, modifier = size) {
                SnyggIcon(imageVector = Icons.Default.Close, contentDescription = stringRes(R.string.pysanka__ocr__close))
            }
        }
        SnyggBox(FlorisImeUi.ClipboardContent.elementName, modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxSize()) {
                if (word == null) {
                    Note(stringRes(R.string.pysanka__translate__no_word))
                    return@Column
                }
                val f = from
                if (f != null) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Chip(Translations.languageName(f, ui), picking == Picking.FROM) {
                            picking = if (picking == Picking.FROM) null else Picking.FROM
                        }
                        SnyggIconButton(
                            FlorisImeUi.ClipboardHeaderButton.elementName,
                            onClick = {
                                val old = f
                                from = to
                                to = old
                            },
                        ) {
                            SnyggIcon(imageVector = Icons.Default.SwapHoriz, contentDescription = stringRes(R.string.pysanka__translate__swap))
                        }
                        Chip(Translations.languageName(to, ui), picking == Picking.TO) {
                            picking = if (picking == Picking.TO) null else Picking.TO
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().weight(1f).florisVerticalScroll()) {
                    val p = picking
                    when {
                        p != null -> LanguageGrid(ui, if (p == Picking.FROM) f else to) { lang ->
                            if (p == Picking.FROM) from = lang else to = lang
                            picking = null
                        }
                        loading -> Note(stringRes(R.string.pysanka__translate__looking))
                        f == null -> Note(stringRes(R.string.pysanka__translate__unknown, "word" to word.text))
                        lookup?.results.isNullOrEmpty() -> Note(
                            stringRes(R.string.pysanka__translate__none, "word" to word.text, "language" to Translations.languageName(to, ui)),
                        )
                        else -> {
                            val results = lookup!!.results
                            if (results.first().viaEnglish) Note(stringRes(R.string.pysanka__translate__via_english))
                            for (r in results) {
                                ResultRow(r) {
                                    if (TranslateBridge.replace(word, r.word)) close()
                                }
                            }
                        }
                    }
                    SnyggText(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).alpha(0.6f),
                        text = stringRes(R.string.pysanka__translate__source),
                    )
                }
            }
        }
    }
}

private enum class Picking { FROM, TO }

@Composable
private fun Note(text: String) {
    SnyggText(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), text = text)
}

@Composable
private fun Chip(text: String, active: Boolean, onClick: () -> Unit) {
    SnyggChip(
        FlorisImeUi.ClipboardFilterChip.elementName,
        attributes = mapOf("state" to if (active) "active" else "inactive"),
        onClick = onClick,
        text = text,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageGrid(ui: Locale, current: String?, onPick: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (lang in Translations.LANGUAGES) {
            Chip(Translations.languageName(lang, ui), lang == current) { onPick(lang) }
        }
    }
}

@Composable
private fun ResultRow(r: Translations.Result, onClick: () -> Unit) {
    SnyggBox(
        FlorisImeUi.ClipboardItem.elementName,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        clickAndSemanticsModifier = Modifier.clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            SnyggText(text = r.word)
            if (!r.hint.isNullOrBlank()) {
                SnyggText(modifier = Modifier.alpha(0.65f), text = r.hint)
            }
        }
    }
}
