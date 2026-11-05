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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import java.io.File

/** The author's letter and Support Ukraine, whole, in Ukrainian and in English, for review (build/screens). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutScreensTest {
    @Before fun setUp() {
        val app: Application = ApplicationProvider.getApplicationContext()
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
    }

    private fun shot(name: String) = ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
        scenario.onActivity { activity ->
            activity.setContent {
                val scheme = org.florisboard.lib.color.neutralDynamicColorScheme(
                    primary = dev.patrickgold.florisboard.app.apptheme.PysankaRed, isDark = false,
                    tertiary = dev.patrickgold.florisboard.app.apptheme.PysankaGold,
                    neutral = dev.patrickgold.florisboard.app.apptheme.PysankaCream,
                    neutralVariant = dev.patrickgold.florisboard.app.apptheme.PysankaCream,
                )
                MaterialTheme(colorScheme = scheme) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                        ua.pysanka.settings.AuthorLetter()
                        Spacer(Modifier.height(24.dp))
                        ua.pysanka.settings.SupportUkraine(open = {})
                    }
                }
            }
        }
        repeat(4) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(50) }
        var bitmap: Bitmap? = null
        scenario.onActivity { activity ->
            val view = activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        }
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Unit
    }

    @Config(sdk = [34], application = Application::class, qualifiers = "uk-w400dp-h2900dp-mdpi")
    @Test fun ukrainian() = shot("about-uk")

    @Config(sdk = [34], application = Application::class, qualifiers = "en-w400dp-h2700dp-mdpi")
    @Test fun english() = shot("about-en")
}
