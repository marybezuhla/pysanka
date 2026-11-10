package ua.pysanka.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.florisboard.lib.color.neutralDynamicColorScheme
import org.florisboard.lib.color.getColor
import org.florisboard.lib.color.systemAccentOrDefault
import org.florisboard.lib.compose.stringRes

/** Pysanka: how a theme looks, for its small picture of the keyboard. */
private class Look(val background: Color, val key: Color, val text: Color, val accent: Color, val onAccent: Color)

private const val OWN = "org.florisboard.themes"
private const val MY = "org.florisboard.themes.my"

/**
 * The themes that come with Pysanka, as in the other apps with these themes: the phone's own colours
 * first, then in pairs, each day theme at the same place as its night one (sky and starry night,
 * viburnum and poppy, linen and black earth, beeswax and spruce, clay and embers).
 */
private val DAY = listOf(MY to "floris_day_my", OWN to "nebo", OWN to "kalyna", OWN to "linen", OWN to "visk", OWN to "glyna")
private val NIGHT = listOf(MY to "floris_night_my", OWN to "zoriana_nich", OWN to "mak", OWN to "chornozem", OWN to "smereka", OWN to "zhar")

/** A theme's pair: the night theme for a day one and the other way round (null for a theme not listed). */
internal fun pairOf(id: String): ExtensionComponentName? {
    val i = DAY.indexOfFirst { it.second == id }
    val j = NIGHT.indexOfFirst { it.second == id }
    val (ext, pair) = when {
        i >= 0 -> NIGHT[i]
        j >= 0 -> DAY[j]
        else -> return null
    }
    return ExtensionComponentName(ext, pair)
}

/** A theme's colours, read from its stylesheet (the system's colours for the Material You ones). */
private fun look(context: Context, extension: String, id: String, seed: Color): Look? = runCatching {
    val sheet = context.assets.open("ime/theme/$extension/stylesheets/$id.json").use { it.readBytes().decodeToString() }
    val defines = Json.parseToJsonElement(sheet).jsonObject["@defines"]!!.jsonObject
    val light = neutralDynamicColorScheme(primary = seed, isDark = false)
    val dark = neutralDynamicColorScheme(primary = seed, isDark = true)
    fun color(name: String): Color {
        val v = defines[name]!!.jsonPrimitive.content.trim()
        return when {
            v.startsWith("#") -> Color(android.graphics.Color.parseColor(v))
            v.startsWith("dynamic-light-color(") -> light.getColor(v.substringAfter('(').substringBefore(')'))
            v.startsWith("dynamic-dark-color(") -> dark.getColor(v.substringAfter('(').substringBefore(')'))
            else -> error("Unknown colour $v")
        }
    }
    // As the stylesheets draw the keys: Pysanka's themes letters in --on-surface and Enter's in
    // --on-primary; the Material You ones letters in --on-background and Enter's in --on-surface
    val my = extension.endsWith(".my")
    val letters = color(if (my) "--on-background" else "--on-surface")
    val onAccent = color(if (my) "--on-surface" else "--on-primary")
    Look(color("--background"), color("--surface"), letters, color("--primary"), onAccent)
}.getOrNull()

/**
 * Pysanka: the day or the night theme as small pictures of the keyboard, in a row; a tap chooses
 * one. For the settings and the welcome screen.
 */
@Composable
fun ThemePicker(night: Boolean, modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val accent by prefs.theme.accentColor.observeAsState()
    val pref = if (night) prefs.theme.nightThemeId else prefs.theme.dayThemeId
    val other = if (night) prefs.theme.dayThemeId else prefs.theme.nightThemeId
    val chosen by pref.observeAsState()
    // The phone's accent, as the keyboard's Material You themes take it
    ThemeRow(night, systemAccentOrDefault(accent), chosen.componentId, modifier) { id ->
        scope.launch {
            pref.set(id)
            // Its pair comes with it, as in the other apps; the other one can still be changed after
            pairOf(id.componentId)?.let { other.set(it) }
        }
    }
}

/** The pictures themselves ([chosen]: a theme's id), apart from the settings (drawn alone for review), in rows of three. */
@Composable
internal fun ThemeRow(night: Boolean, seed: Color, chosen: String, modifier: Modifier = Modifier, onChoose: (ExtensionComponentName) -> Unit) {
    val context = LocalContext.current
    val names = themeNames()
    // Read off the main thread (six small stylesheets)
    val looks by androidx.compose.runtime.produceState(emptyList<Pair<ExtensionComponentName, Look>>(), seed, night) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            (if (night) NIGHT else DAY).mapNotNull { (ext, id) -> look(context, ext, id, seed)?.let { ExtensionComponentName(ext, id) to it } }
        }
    }
    // Rows of exactly three across the whole width: none hides behind the edge of the screen
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (row in looks.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((id, look) in row) {
                    val selected = id.componentId == chosen
                    Column(
                        Modifier.weight(1f).selectable(selected, role = Role.RadioButton) { onChoose(id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Sample(look, selected)
                        ThemeName(names[id.componentId] ?: id.componentId, selected)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * A theme's name under its picture: never broken inside a word ("Viburnu / m" on a narrow phone with
 * large text). As many lines as words at most; a word that does not fit gets a smaller size instead.
 */
@Composable
private fun ThemeName(name: String, selected: Boolean) {
    val style = MaterialTheme.typography.bodyMedium.merge(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
    )
    // One word per line: any other break overflows, so the text shrinks instead
    BasicText(
        name.replace(' ', '\n'),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        style = style,
        maxLines = name.split(' ').size,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
    )
}

/** Two rows of keys and Enter, in the theme's colours; the chosen one framed in the accent of the settings. */
@Composable
private fun Sample(look: Look, selected: Boolean) {
    val frame = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    // A picture only: a screen reader reads the theme's name below it, not the letters
    Surface(shape = RoundedCornerShape(12.dp), border = frame, color = look.background, modifier = Modifier.fillMaxWidth().clearAndSetSemantics { }) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in listOf("йцук", "фіва")) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in row) Key(c.toString(), look.key, look.text, Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Key("", look.key, look.text, Modifier.weight(3f))
                Key("↵", look.accent, look.onAccent, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Key(label: String, color: Color, ink: Color, modifier: Modifier) {
    Box(modifier.height(22.dp).background(color, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
        Text(label, color = ink, fontSize = 12.sp)
    }
}
