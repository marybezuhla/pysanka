package ua.pysanka.clipboard

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pysanka: an optional fingerprint (or screen lock) before the clipboard history shows, for a phone
 * that is handed to others unlocked. Opened, it stays open for [OPEN_MS], and closes when the phone
 * locks. The keyboard cannot ask itself (it has no screen of its own), so a small see-through
 * screen asks ([ClipboardUnlockActivity]) and then the history opens again in the same field.
 * Android 9 and newer, and only on a phone with a screen lock.
 */
object ClipboardLock {
    private const val OPEN_MS = 5 * 60 * 1000L

    /** Whether this phone can ask (Android 9+ with a screen lock). */
    fun supported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 28 && context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    private val openUntil = MutableStateFlow(0L)

    /** Changes when the lock opens or closes (for the clipboard panel to redraw). */
    val changes: StateFlow<Long> = openUntil

    fun isOpen(): Boolean = System.currentTimeMillis() < openUntil.value

    fun open() {
        openUntil.value = System.currentTimeMillis() + OPEN_MS
    }

    fun close() {
        openUntil.value = 0L
    }

    /** The history opens again once the keyboard is back (after the fingerprint screen). */
    @Volatile var reopen = false

    /** Asks for the fingerprint (the keyboard is hidden meanwhile). */
    fun ask(context: Context) {
        reopen = true
        FlorisImeService.hideUi()
        runCatching {
            context.startActivity(Intent(context, ClipboardUnlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** Pysanka: the see-through screen that asks for the fingerprint (or screen lock) for [ClipboardLock]. */
class ClipboardUnlockActivity : Activity() {
    private val cancel = CancellationSignal()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 28) {
            finish()
            return
        }
        val builder = BiometricPrompt.Builder(this).setTitle(getString(R.string.pysanka__clipboard_lock__prompt))
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        runCatching {
            builder.build().authenticate(cancel, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    ClipboardLock.open()
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    ClipboardLock.reopen = false
                    finish()
                }
            })
        }.onFailure {
            ClipboardLock.reopen = false
            finish()
        }
    }

    override fun onDestroy() {
        cancel.cancel()
        super.onDestroy()
    }
}
