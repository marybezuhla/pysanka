package ua.pysanka.screens

import ua.pysanka.ui.PysankaBackdrop
import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ua.pysanka.settings.ActionRow
import ua.pysanka.settings.Block
import ua.pysanka.settings.BlockDivider
import ua.pysanka.settings.BlockNote
import ua.pysanka.settings.ChoiceRow
import ua.pysanka.settings.LinkRow
import ua.pysanka.settings.SliderRow
import ua.pysanka.settings.ToggleRow
import java.io.File

/**
 * Draws the settings' blocks on the computer, open and closed, in the day and night look, with
 * made-up states, for review. The pictures go to build/screens.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "uk-w400dp-h900dp-xxhdpi")
class SettingsScreensTest {
    private lateinit var app: Application

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
    }

    private fun shot(name: String, dark: Boolean, openTyping: Boolean) = ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
        scenario.onActivity { activity ->
            activity.setContent {
                // The settings' own way of finding texts in the chosen language
                org.florisboard.lib.compose.ProvideLocalizedResources(activity, appName = dev.patrickgold.florisboard.R.string.app_name) {
                // Pysanka's own colours of the settings, with the faint pysanka in the corner
                val scheme = org.florisboard.lib.color.neutralDynamicColorScheme(
                    primary = dev.patrickgold.florisboard.app.apptheme.PysankaRed, isDark = dark,
                    tertiary = dev.patrickgold.florisboard.app.apptheme.PysankaGold,
                    neutral = dev.patrickgold.florisboard.app.apptheme.PysankaCream,
                    neutralVariant = dev.patrickgold.florisboard.app.apptheme.PysankaCream,
                )
                MaterialTheme(colorScheme = scheme) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    PysankaBackdrop()
                    Column(
                        Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // The welcome screen as it opens the first time; the settings' blocks otherwise
                        if (!openTyping) {
                            dev.patrickgold.florisboard.app.setup.PysankaWelcome()
                            dev.patrickgold.florisboard.app.setup.SetupStep(
                                1, "Увімкнути Писанку", done = false, active = true,
                                text = app.getString(dev.patrickgold.florisboard.R.string.pysanka__setup__enable), button = "Відкрити системні налаштування",
                            ) {}
                            dev.patrickgold.florisboard.app.setup.SetupStep(2, "Вибрати Писанку", done = false, active = false, text = "", button = "") {}
                            Text(app.getString(dev.patrickgold.florisboard.R.string.pysanka__setup__look), color = MaterialTheme.colorScheme.onSurface)
                            ua.pysanka.settings.ThemeRow(false, androidx.compose.ui.graphics.Color(0xFF6750A4), "kreyda") {}
                            ua.pysanka.settings.ThemeRow(true, androidx.compose.ui.graphics.Color(0xFF6750A4), "podillia") {}
                            return@Column
                        }
                        Block("Мови", "Українська, Англійська (Сполучені Штати)", icon = Icons.Default.Language) {}
                        Block("Набір", "Свайп: увімкнено · підказки: увімкнено · виправлення: увімкнено", icon = Icons.Outlined.Keyboard, startOpen = openTyping) {
                            ToggleRow("Набір проведенням (свайп)", true, "Проводьте пальцем по літерах") {}
                            ToggleRow("Показувати слід пальця", true) {}
                            ToggleRow("Вчитися моїх слів", true, "Слова, якими ви користуєтеся, пропонуються раніше") {}
                            ToggleRow("Виправляти описки", false, "Лише явні описки, після пробілу", enabled = false) {}
                            ActionRow("Забути вивчені слова", "Одне слово: утримуйте його в підказках") {}
                            BlockDivider()
                            ChoiceRow("Проведення вліво по «Видалити»", listOf(1 to "Видалити слово", 2 to "Нічого"), 1) {}
                            SliderRow("Розмір літер на клавішах", 100, 50..150, 5, { "$it %" }) {}
                            BlockNote("Перемикає між мовами кнопка з глобусом на клавіатурі.")
                        }
                        Block("Приватність", "Без інтернету · паролі не зберігаються · буфер зашифровано", icon = Icons.Outlined.Lock) {}
                        Block("ШІ на телефоні", "Модель встановлено", icon = ua.pysanka.ui.AiMark) {
                            LinkRow("Модель і налаштування ШІ", "Завантажити чи видалити модель, «Мій стиль»") {}
                        }
                    }
                    }
                }
                }
            }
        }
        // The theme pictures are read off the main thread: a moment for them
        repeat(8) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(100) }
        var bitmap: Bitmap? = null
        scenario.onActivity { activity ->
            val view = activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        }
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Unit
    }

    @Test fun closed() = shot("welcome-01", dark = false, openTyping = false)
    @Test fun closedDark() = shot("welcome-01-dark", dark = true, openTyping = false)
    @Test fun typingOpen() = shot("settings-02-typing-open", dark = false, openTyping = true)
    @Test fun typingOpenDark() = shot("settings-02-typing-open-dark", dark = true, openTyping = true)
}
