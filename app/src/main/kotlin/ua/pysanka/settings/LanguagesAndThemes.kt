package ua.pysanka.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.PYSANKA_SETTINGS_LANGUAGES
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/**
 * Pysanka: the keyboard's languages, simply: the ones in use, removed with a tap, and "Add a
 * language" with the ready-made ones (each comes with its usual layout); the globe key switches.
 */
@Composable
fun LanguagesBlock() {
    val context = LocalContext.current
    val subtypeManager by context.subtypeManager()
    val keyboardManager by context.keyboardManager()
    val subtypes by subtypeManager.subtypesFlow.collectAsState()
    val presets by keyboardManager.resources.subtypePresets.collectAsState()
    var adding by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Subtype?>(null) }
    // Names in the settings' own language, not the phone's
    val ui = FlorisLocale.from(LocalConfiguration.current.locales[0])
    fun name(locale: FlorisLocale) = locale.displayName(ui)

    Block(
        stringRes(R.string.pysanka__settings__languages),
        subtypes.joinToString { name(it.primaryLocale) },
        icon = Icons.Default.Language,
    ) {
        for (subtype in subtypes) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name(subtype.primaryLocale), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                // The last language stays: a keyboard needs one
                if (subtypes.size > 1) {
                    IconButton(onClick = { removing = subtype }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringRes(R.string.pysanka__languages__remove, "name" to name(subtype.primaryLocale)))
                    }
                }
            }
        }
        ActionRow(stringRes(R.string.pysanka__languages__add)) { adding = true }
        BlockNote(stringRes(R.string.pysanka__languages__switch_hint))
        BlockDivider()
        SettingsLanguage()
    }

    if (adding) {
        // One row per language (some come with several layouts: the first is the usual one)
        val choices = remember(presets, subtypes) {
            presets.distinctBy { it.locale.languageTag() }
                .filter { p -> subtypes.none { it.primaryLocale == p.locale } }
                .sortedWith(compareBy({ PopularLanguages.rank(it.locale.languageTag()) }, { name(it.locale) }))
        }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringRes(R.string.pysanka__languages__add)) },
            text = {
                // The list is read when the keyboard starts: a moment after a fresh start it may be empty
                if (choices.isEmpty()) Text(stringRes(R.string.pysanka__languages__loading))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(choices, key = { it.locale.languageTag() }) { preset ->
                        Text(
                            name(preset.locale),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable { adding = false; subtypeManager.addSubtype(preset.toSubtype()) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringRes(R.string.action__cancel)) } },
        )
    }
    removing?.let { subtype ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringRes(R.string.pysanka__languages__remove_confirm, "name" to name(subtype.primaryLocale))) },
            confirmButton = {
                TextButton(onClick = { removing = null; subtypeManager.removeSubtype(subtype) }) { Text(stringRes(R.string.action__yes)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringRes(R.string.action__no)) } },
        )
    }
}

/** Pysanka's themes by their id, in the language of the settings (in the order they are offered). */
@Composable
fun themeNames(): Map<String, String> = linkedMapOf(
    "kreyda" to stringRes(R.string.pysanka__theme__kreyda),
    "visk" to stringRes(R.string.pysanka__theme__visk),
    "nebo" to stringRes(R.string.pysanka__theme__nebo),
    "floris_day_my" to stringRes(R.string.pysanka__theme__system),
    "podillia" to stringRes(R.string.pysanka__theme__podillia),
    "hutsulshchyna" to stringRes(R.string.pysanka__theme__hutsulshchyna),
    "chornozem" to stringRes(R.string.pysanka__theme__chornozem),
    "floris_night_my" to stringRes(R.string.pysanka__theme__system_dark),
)

/** The day and the night theme, as small pictures of the keyboard (see [ThemePicker]). */
@Composable
fun ThemeChoices() {
    val prefs by FlorisPreferenceStore
    val mode by prefs.theme.mode.observeAsState()
    // Only the theme that can show: "always day" never uses the night one, and the other way round
    if (mode != dev.patrickgold.florisboard.ime.theme.ThemeMode.ALWAYS_NIGHT) {
        Text(stringRes(R.string.pysanka__look__day_theme), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        ThemePicker(night = false)
    }
    if (mode != dev.patrickgold.florisboard.ime.theme.ThemeMode.ALWAYS_DAY) {
        Text(stringRes(R.string.pysanka__look__night_theme), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        ThemePicker(night = true)
    }
}

/** The language of these settings: as the phone, or one of Pysanka's (each named in itself). */
@Composable
private fun SettingsLanguage() {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val value by prefs.other.settingsLanguage.observeAsState()
    val names = mapOf(
        "auto" to stringRes(R.string.pysanka__settings__ui_language_auto),
        "uk" to "Українська",
        "en" to "English",
        "be" to "Беларуская",
        "pl" to "Polski",
        "sk" to "Slovenčina",
        "hu" to "Magyar",
        "ro" to "Română",
        "de" to "Deutsch",
        "fr" to "Français",
        "es" to "Español",
        "pt" to "Português",
    )
    ChoiceRow(
        stringRes(R.string.pysanka__settings__ui_language),
        PYSANKA_SETTINGS_LANGUAGES.map { it to names.getValue(it) },
        value,
    ) { scope.launch { prefs.other.settingsLanguage.set(it) } }
}
