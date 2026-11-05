package ua.pysanka.screens

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.snygg.SnyggStylesheet
import org.florisboard.lib.snygg.ui.ProvideSnyggTheme
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.rememberSnyggTheme
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ua.pysanka.voice.VoiceActions
import ua.pysanka.voice.VoiceInput
import ua.pysanka.voice.VoicePanelView
import java.io.File

/**
 * Draws the dictation panel on the computer, with the keyboard's own day and night themes, for
 * review. A screen that crashes fails the build; the pictures go to build/screens.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "uk-w400dp-h760dp-xxhdpi")
class VoicePanelScreensTest {
    private lateinit var app: Application

    private val none = object : VoiceActions {
        override fun keys() {}
        override fun tap() {}
        override fun holdStart() {}
        override fun holdEnd(long: Boolean) {}
        override fun cancel() {}
        override fun takeBack() {}
        override fun backspace() {}
        override fun punctuation(mark: String) {}
        override fun space() {}
        override fun newLine() {}
        override fun emoji(e: String) {}
    }

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
    }

    private fun shot(
        name: String,
        state: VoiceInput.State,
        dark: Boolean = false,
        hold: Boolean = false,
        canTakeBack: Boolean = false,
        theme: String = if (dark) "mak" else "linen",
    ) = ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
        val json = app.assets.open("ime/theme/org.florisboard.themes/stylesheets/$theme.json").bufferedReader().use { it.readText() }
        val stylesheet = SnyggStylesheet.fromJson(json).getOrThrow()
        scenario.onActivity { activity ->
            activity.setContent {
                ProvideLocalizedResources(activity, appName = R.string.app_name) {
                    CompositionLocalProvider(LocalTextStyle provides TextStyle.Default) {
                        ProvideSnyggTheme(rememberSnyggTheme(stylesheet)) {
                            Column(Modifier.fillMaxSize().background(if (dark) Color(0xFF121212) else Color(0xFFF4F4F4))) {
                                Text(
                                    MESSAGE,
                                    color = if (dark) Color.White else Color.Black,
                                    modifier = Modifier.padding(16.dp)
                                        .background(if (dark) Color(0xFF2A2A2A) else Color.White, RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                )
                                Spacer(Modifier.weight(1f))
                                SnyggBox(FlorisImeUi.Window.elementName) {
                                    VoicePanelView(
                                        state = state,
                                        canTakeBack = canTakeBack,
                                        autoStop = true,
                                        hold = hold,
                                        tooShort = false,
                                        emoji = EMOJI,
                                        actions = none,
                                        modifier = Modifier.fillMaxWidth().height(300.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        repeat(8) { shadowOf(Looper.getMainLooper()).idle() }
        var bitmap: Bitmap? = null
        scenario.onActivity { activity ->
            val view = activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                .also { view.draw(android.graphics.Canvas(it)) }
        }
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Unit
    }

    private val listening = VoiceInput.State.Listening(seconds = 14f, level = 0.12f, heard = true)

    @Test fun ready() = shot("voice-01-ready", VoiceInput.State.Idle)
    @Test fun listening() = shot("voice-02-listening", listening)
    @Test fun listeningDark() = shot("voice-02-listening-dark", listening, dark = true)
    @Test fun inserted() = shot("voice-03-inserted", VoiceInput.State.Idle, canTakeBack = true)
    @Test fun insertedDark() = shot("voice-03-inserted-dark", VoiceInput.State.Idle, dark = true, canTakeBack = true)
    @Test fun recognising() = shot("voice-04-recognising", VoiceInput.State.Recognising, theme = "nebo")
    @Test fun hold() = shot("voice-05-hold", listening, hold = true, theme = "smereka")
    @Test fun failed() = shot("voice-06-failed", VoiceInput.State.Failed(R.string.pysanka__voice__nothing), theme = "chornozem")

    private companion object {
        const val MESSAGE = "Привіт! Я вже виїхала, буду хвилин за двадцять."
        val EMOJI = listOf("😂", "❤️", "🙏", "🙂", "👍", "🔥", "😢", "🎉", "😍")
    }
}
