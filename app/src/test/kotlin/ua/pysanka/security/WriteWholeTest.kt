package ua.pysanka.security

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import javax.crypto.spec.SecretKeySpec

/** Pysanka: a file is replaced whole or not at all, also when the writer closes the stream itself. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class WriteWholeTest {
    private val dir: File = Files.createTempDirectory("whole").toFile()

    @Test
    fun writesAndReplaces() {
        val f = File(dir, "a/data.bin")
        writeWhole(f) { it.write(byteArrayOf(1, 2, 3)) }
        writeWhole(f) { it.write(byteArrayOf(4, 5)) }
        assertArrayEquals(byteArrayOf(4, 5), f.readBytes())
        assertEquals(listOf("data.bin"), f.parentFile!!.list()!!.toList())
    }

    @Test
    fun aFailedWriteKeepsTheOldFile() {
        val f = File(dir, "kept.bin")
        writeWhole(f) { it.write(byteArrayOf(9)) }
        runCatching { writeWhole(f) { it.write(byteArrayOf(1, 1, 1)); error("cut short") } }
        assertArrayEquals(byteArrayOf(9), f.readBytes())
        assertEquals(listOf("kept.bin"), dir.list()!!.filter { it.startsWith("kept") })
    }

    @Test
    fun aSealedWriterThatClosesTheStream() {
        val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        val label = byteArrayOf(7)
        val data = ByteArray(200_000) { (it % 251).toByte() }
        val f = File(dir, "sealed.bin")
        writeWhole(f) { out -> SealedFile.writer(key, label, out).use { it.write(data) } }
        assertTrue(f.isFile)
        assertArrayEquals(data, f.inputStream().use { SealedFile.readAll(key, label, it) })
    }
}
