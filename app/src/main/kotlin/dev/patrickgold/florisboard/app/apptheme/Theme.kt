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
    // Pysanka: its own colours unless the phone's are chosen: the icon's red, its gold, and
    // warm cream surfaces (from a warm neutral), like the egg on the icon
    if (!systemColors) {
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
    val systemColors by prefs.other.pysankaSystemColors.observeAsState()
    androidx.compose.runtime.CompositionLocalProvider(LocalSystemColors provides systemColors) {
        MaterialTheme(
            colorScheme = colors,
            typography = Typography,
            content = content,
        )
    }
}
