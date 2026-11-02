package ua.pysanka.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Draws the launcher icon as a round launcher shows it (the inner 72 of 108), and its one-colour form, for review. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class IconScreenTest {
    private fun shot(name: String, drawable: Int, background: Int) {
        val app: Application = ApplicationProvider.getApplicationContext()
        val size = 432
        val full = Bitmap.createBitmap(size * 3 / 2, size * 3 / 2, Bitmap.Config.ARGB_8888)
        Canvas(full).apply {
            drawColor(background)
            ContextCompat.getDrawable(app, drawable)!!.apply { setBounds(0, 0, full.width, full.height) }.draw(this)
        }
        // The round mask over the middle two thirds
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            drawBitmap(full, -size / 4f, -size / 4f, paint)
        }
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun icon() = shot("icon-01-launcher", R.drawable.ic_app_icon_foreground, 0xFF85251E.toInt())
    @Test fun themed() = shot("icon-02-themed", R.drawable.ic_app_icon_monochrome, 0xFF33363B.toInt())
}
