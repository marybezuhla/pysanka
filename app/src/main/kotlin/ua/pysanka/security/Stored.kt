package ua.pysanka.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.util.AtomicFile
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Pysanka: a key in the phone's hardware keystore: AES-256 for GCM, never leaving the hardware,
 * StrongBox when the phone has it and, on Android 9+, usable only while the phone is unlocked.
 * One place for every key Pysanka keeps (the clipboard's, the style samples').
 */
object HardwareKey {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** The key under [alias], made the first time. */
    fun getOrCreate(alias: String): SecretKey = (keyStore().getKey(alias, null) as? SecretKey) ?: create(alias)

    /** A new key under [alias] (one already there is replaced). */
    fun create(alias: String): SecretKey {
        keyStore().let { if (it.containsAlias(alias)) it.deleteEntry(alias) }
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(
            alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setKeySize(256)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setUnlockedDeviceRequired(true)
                if (strongBox) setIsStrongBoxBacked(true)
            }
        }.build()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        return try {
            gen.init(spec(strongBox = true))
            gen.generateKey()
        } catch (e: java.security.ProviderException) {
            // No StrongBox (StrongBoxUnavailableException is a ProviderException; some phones throw the base class)
            gen.init(spec(strongBox = false))
            gen.generateKey()
        }
    }
}

/**
 * Pysanka: writes [target] whole or not at all (Android's AtomicFile: a new file, synced to the
 * disk, then put in place of the old one), so a power cut never leaves half a file. [block] may
 * close the stream it gets; the file is finished after it.
 */
fun writeWhole(target: File, block: (OutputStream) -> Unit) {
    target.parentFile?.mkdirs()
    val file = AtomicFile(target)
    val out = file.startWrite()
    try {
        block(object : FilterOutputStream(out) {
            override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
            override fun close() = out.flush()
        })
    } catch (e: Throwable) {
        file.failWrite(out)
        throw e
    }
    file.finishWrite(out)
    // AtomicFile only logs a failed rename (it leaves the new file behind): a key that never
    // reached its place would be lost at the next start
    check(target.isFile && !File(target.path + ".new").exists()) { "Could not save ${target.name}" }
    // The rename itself on the disk too (AtomicFile syncs the file, not its folder)
    runCatching {
        val fd = android.system.Os.open(target.parentFile!!.path, android.system.OsConstants.O_RDONLY, 0)
        try { android.system.Os.fsync(fd) } finally { android.system.Os.close(fd) }
    }
}
