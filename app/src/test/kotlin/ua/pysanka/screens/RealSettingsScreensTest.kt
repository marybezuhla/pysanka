package ua.pysanka.screens

import android.content.ComponentName
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import dev.patrickgold.florisboard.app.AppTheme
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ua.pysanka.settings.PysankaHome
import ua.pysanka.ui.PysankaBackdrop
import java.io.File

/**
 * Pysanka: the real settings screen, as the app draws it (Pysanka's own application, its settings
 * with their defaults), with each block open in turn, in the day and night look. The pictures go
 * to build/screens for review: cut texts, crowded rows, poor contrast. A tall screen, so an open
 * block fits whole.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "uk-w400dp-h2000dp-xxhdpi")
open class RealSettingsScreensTest {
    protected open val prefix = "real"

    @Before fun setUp() {
        // Pysanka's crash handler ends the process (as on a phone): here an error in the background
        // is printed instead, so the test shows it
        Thread.setDefaultUncaughtExceptionHandler { thread, e -> System.err.println("Uncaught in ${thread.name}: ${e.stackTraceToString()}") }
        val app = ApplicationProvider.getApplicationContext<FlorisApplication>()
        // A phone grants the app its own signature permission by itself; Robolectric does not
        shadowOf(app).grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        // The settings are read in the background when the app starts
        val until = System.currentTimeMillis() + 10_000
        while (!app.preferenceStoreLoaded.value && System.currentTimeMillis() < until) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
    }

    protected fun shot(name: String, open: Int?, dark: Boolean) = ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
        scenario.onActivity { activity ->
            activity.setContent {
                org.florisboard.lib.compose.ProvideLocalizedResources(activity, appName = R.string.app_name) {
                    FlorisAppTheme(theme = if (dark) AppTheme.DARK else AppTheme.LIGHT) {
                        CompositionLocalProvider(LocalNavController provides rememberNavController()) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                PysankaBackdrop()
                                PysankaHome(initialOpen = open?.let { activity.getString(it) })
                            }
                        }
                    }
                }
            }
        }
        repeat(10) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(100) }
        var bitmap: Bitmap? = null
        scenario.onActivity { activity ->
            val view = activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        }
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$prefix-$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Unit
    }

    @Test fun home() = shot("00-home", null, dark = false)
    @Test fun homeDark() = shot("00-home-dark", null, dark = true)
    @Test fun languages() = shot("01-languages", R.string.pysanka__settings__languages, dark = false)
    @Test fun typing() = shot("02-typing", R.string.pysanka__settings__typing, dark = false)
    @Test fun typingDark() = shot("02-typing-dark", R.string.pysanka__settings__typing, dark = true)
    @Test fun look() = shot("03-look", R.string.pysanka__settings__look, dark = false)
    @Test fun emoji() = shot("04-emoji", R.string.pysanka__settings__emoji, dark = false)
    @Test fun clipboard() = shot("05-clipboard", R.string.pysanka__settings__clipboard, dark = false)
    @Test fun privacy() = shot("06-privacy", R.string.pysanka__settings__privacy, dark = false)
    @Test fun ai() = shot("07-ai", R.string.pysanka__settings__ai, dark = false)
    @Test fun voice() = shot("08-voice", R.string.pysanka__voice__title, dark = false)
}

/**
 * Pysanka: the same screens on Android 8.0, the oldest Pysanka runs on: they must draw there too
 * (an API newer than the phone would fail here).
 */
@Config(sdk = [26], qualifiers = "uk-w360dp-h2000dp-xhdpi")
class RealSettingsScreensAndroid8Test : RealSettingsScreensTest() {
    override val prefix = "android8"
}
