/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.app.apptheme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.patrickgold.florisboard.app.AppTheme
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.jetpref.datastore.model.observeAsState
import org.florisboard.lib.color.neutralDynamicColorScheme
import org.florisboard.lib.color.systemAccentOrDefault

/** Pysanka: whether the settings are in the phone's colours (Material You) rather than Pysanka's. */
val LocalSystemColors = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Pysanka: the colours of its icon (the red egg, the gold star) and a warm cream for surfaces. */
val PysankaRed = Color(0xFFB3261E)
val PysankaGold = Color(0xFFC9962B)
val PysankaCream = Color(0xFF9A8670)


@Composable
fun getColorScheme(
    theme: AppTheme,
): ColorScheme {
    val prefs by FlorisPreferenceStore
    val accentColor by prefs.other.accentColor.observeAsState()
    val systemColors by prefs.other.pysankaSystemColors.observeAsState()

    val seedColor = systemAccentOrDefault(accentColor)
    val isDark = when (theme) {
        AppTheme.AUTO, AppTheme.AUTO_AMOLED -> isSystemInDarkTheme()
        AppTheme.DARK, AppTheme.AMOLED_DARK -> true
        AppTheme.LIGHT -> false
    }
    val isAmoled = theme == AppTheme.AUTO_AMOLED || theme == AppTheme.AMOLED_DARK
    // Pysanka: the settings wear the keyboard's own theme (Linen by day, Poppy by night, and so on), so
    // choosing a colour theme changes both. The phone's colours when chosen; the Material You themes too.
    if (!systemColors) {
        val themeId by (if (isDark) prefs.theme.nightThemeId else prefs.theme.dayThemeId).observeAsState()
        val context = androidx.compose.ui.platform.LocalContext.current
        val sheet = androidx.compose.runtime.remember(themeId) { keyboardThemeColors(context, themeId.extensionId, themeId.componentId) }
        if (sheet != null) {
            val base = neutralDynamicColorScheme(
                primary = sheet.primary, isDark = isDark, isAmoled = isAmoled, tertiary = sheet.secondary,
                neutral = sheet.surface, neutralVariant = sheet.surface,
            )
            return base.copy(
                primary = sheet.primary, onPrimary = sheet.onPrimary, secondary = sheet.secondary,
                background = sheet.background, onBackground = sheet.onSurface,
                surface = sheet.background, onSurface = sheet.onSurface, onSurfaceVariant = sheet.onSurfaceVariant,
                surfaceContainerLowest = sheet.background, surfaceContainerLow = sheet.surface, surfaceContainer = sheet.surface,
                surfaceContainerHigh = sheet.surfaceVariant, surfaceContainerHighest = sheet.surfaceVariant,
            )
        }
        // The icon's red, its gold, and warm cream surfaces (from a warm neutral), like the egg on the icon
        return neutralDynamicColorScheme(
            primary = PysankaRed, isDark = isDark, isAmoled = isAmoled,
            tertiary = PysankaGold, neutral = PysankaCream, neutralVariant = PysankaCream,
        )
    }
    return neutralDynamicColorScheme(primary = seedColor, isDark = isDark, isAmoled = isAmoled)
}

fun ColorScheme.amoled(): ColorScheme {
    return this.copy(background = Color.Black, surface = Color.Black)
}

@Composable
fun FlorisAppTheme(
    theme: AppTheme,
    // Pysanka: given by a screen in another process, where the settings are not loaded
    systemColorsOverride: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val colors = getColorScheme(theme = theme)

    val darkTheme =
        theme == AppTheme.DARK
            || theme == AppTheme.AMOLED_DARK
            || (theme == AppTheme.AUTO && isSystemInDarkTheme())
            || (theme == AppTheme.AUTO_AMOLED && isSystemInDarkTheme())

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !darkTheme
        }
    }

    val prefs by FlorisPreferenceStore
    val systemColorsPref by prefs.other.pysankaSystemColors.observeAsState()
    val systemColors = systemColorsOverride ?: systemColorsPref
    androidx.compose.runtime.CompositionLocalProvider(LocalSystemColors provides systemColors) {
        MaterialTheme(
            colorScheme = colors,
            typography = Typography,
            content = content,
        )
    }
}

/** The colours of one of Pysanka's own keyboard themes, as its stylesheet defines them. */
private class SheetColors(
    val primary: Color, val onPrimary: Color, val secondary: Color, val background: Color,
    val surface: Color, val surfaceVariant: Color, val onSurface: Color, val onSurfaceVariant: Color,
)

/**
 * Pysanka: reads a keyboard theme's colours from its stylesheet; null for a theme that is not one of
 * Pysanka's own (the Material You ones take the phone's colours) or that cannot be read.
 */
private fun keyboardThemeColors(context: android.content.Context, extension: String, id: String): SheetColors? = runCatching {
    if (extension != "org.florisboard.themes") return null
    val text = context.assets.open("ime/theme/$extension/stylesheets/$id.json").use { it.readBytes().decodeToString() }
    val defines = kotlinx.serialization.json.Json.parseToJsonElement(text)
        .let { (it as kotlinx.serialization.json.JsonObject)["@defines"] as kotlinx.serialization.json.JsonObject }
    fun c(name: String) = Color(android.graphics.Color.parseColor((defines[name] as kotlinx.serialization.json.JsonPrimitive).content.trim()))
    SheetColors(
        c("--primary"), c("--on-primary"), c("--secondary"), c("--background"),
        c("--surface"), c("--surface-variant"), c("--on-surface"), c("--on-surface-variant"),
    )
}.getOrNull()
