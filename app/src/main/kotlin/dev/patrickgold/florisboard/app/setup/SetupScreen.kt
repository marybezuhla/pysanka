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

package dev.patrickgold.florisboard.app.setup

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.florisboard.lib.util.launchActivity
import dev.patrickgold.florisboard.lib.util.launchUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.FlorisBulletSpacer
import org.florisboard.lib.compose.stringRes

/**
 * Pysanka: the welcome screen, one page that scrolls: the greeting, two steps Android needs (turn
 * Pysanka on, choose it), the day and night theme as small keyboards (they can be chosen at once),
 * and "to the settings". No permission is asked here.
 */
@Composable
fun SetupScreen() = FlorisScreen {
    title = ""
    navigationIconVisible = false
    previewFieldVisible = false

    content {
        val navController = LocalNavController.current
        val context = LocalContext.current
        val prefs by FlorisPreferenceStore
        val scope = rememberCoroutineScope()
        val enabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
        val selected by InputMethodUtils.observeIsFlorisboardSelected(foregroundOnly = true)

        // Back here from the system's keyboard settings as soon as Pysanka is turned on there
        LaunchedEffect(enabled) {
            while (!enabled) {
                delay(200L)
                if (InputMethodUtils.isFlorisboardEnabled(context)) {
                    context.launchActivity(FlorisAppActivity::class) {
                        it.flags = Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    break
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PysankaWelcome()
            Spacer(Modifier.height(4.dp))
            SetupStep(
                1, stringRes(R.string.setup__enable_ime__title), done = enabled, active = !enabled,
                text = stringRes(R.string.pysanka__setup__enable), button = stringRes(R.string.setup__enable_ime__open_settings_btn),
            ) { InputMethodUtils.showImeEnablerActivity(context) }
            SetupStep(
                2, stringRes(R.string.setup__select_ime__title), done = enabled && selected, active = enabled && !selected,
                text = stringRes(R.string.setup__select_ime__description), button = stringRes(R.string.setup__select_ime__switch_keyboard_btn),
            ) { InputMethodUtils.showImePicker(context) }
            // "Your" Pysanka at once: chosen now, it is there when the keyboard opens
            Text(stringRes(R.string.pysanka__setup__look), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(stringRes(R.string.pysanka__look__day_theme), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ua.pysanka.settings.ThemePicker(night = false)
            Text(stringRes(R.string.pysanka__look__night_theme), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ua.pysanka.settings.ThemePicker(night = true)
            Button(
                enabled = enabled && selected,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onClick = {
                    scope.launch { prefs.internal.isImeSetUp.set(true) }
                    navController.navigate(Routes.Settings.Home) { popUpTo(Routes.Setup.Screen) { inclusive = true } }
                },
            ) { Text(stringRes(R.string.setup__finish_up__finish_btn)) }
            footer(context)
        }
    }
}

/** One step: its number (a tick once done), and while it is the one to do, what to do and its button. */
@Composable
internal fun SetupStep(number: Int, title: String, done: Boolean, active: Boolean, text: String, button: String, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            val scheme = MaterialTheme.colorScheme
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (done || active) scheme.primary else scheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
                } else {
                    Text(number.toString(), color = if (active) scheme.onPrimary else scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (done || active) scheme.onSurface else scheme.onSurfaceVariant,
                    modifier = Modifier.semantics { heading() },
                )
                if (active) {
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.padding(top = 4.dp))
                    Button(onClick = onClick, modifier = Modifier.padding(top = 8.dp)) { Text(button) }
                }
            }
        }
    }
}

/** Pysanka: the icon and a short, warm welcome: what Pysanka is and what it keeps to itself. */
@Composable
internal fun PysankaWelcome() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(colorResource(R.color.ic_app_icon_background)),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_app_icon_foreground), contentDescription = null, modifier = Modifier.size(132.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringRes(R.string.pysanka__welcome__title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        Text(stringRes(R.string.pysanka__welcome__lead), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            stringRes(R.string.pysanka__welcome__promise),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun footer(context: Context) {
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val privacyPolicyUrl = stringRes(R.string.florisboard__privacy_policy_url)
        TextButton(onClick = { context.launchUrl(privacyPolicyUrl) }) {
            Text(text = stringRes(R.string.setup__footer__privacy_policy))
        }
        FlorisBulletSpacer()
        val repositoryUrl = stringRes(R.string.florisboard__repo_url)
        TextButton(onClick = { context.launchUrl(repositoryUrl) }) {
            Text(text = stringRes(R.string.setup__footer__repository))
        }
    }
}
