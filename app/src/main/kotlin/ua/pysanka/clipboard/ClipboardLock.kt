package ua.pysanka.clipboard

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.florisboard.lib.compose.stringRes

/**
 * Pysanka: an optional fingerprint (or screen lock) before the clipboard history shows, for a phone
 * that is handed to others unlocked: in the keyboard, and on the settings screens that show the
 * history's texts (templates, the copy). Opened, it stays open for [OPEN_MS], and closes when the
 * screen goes off. The keyboard cannot ask itself (it has no screen of its own), so a small
 * see-through screen asks ([ClipboardUnlockActivity]) and the history opens again in the same field.
 * Android 10 and newer, and only on a phone with a screen lock.
 */
object ClipboardLock {
    private const val OPEN_MS = 5 * 60 * 1000L

    /** Whether this phone can ask (Android 10+ with a screen lock). */
    fun supported(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 29 && context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** Whether the person chose the lock and this phone can ask. */
    fun wanted(context: Context): Boolean {
        val prefs by FlorisPreferenceStore
        return supported(context) && runCatching { prefs.clipboard.pysankaFingerprint.get() }.getOrDefault(false)
    }

    private val openUntil = MutableStateFlow(0L)

    /** When it closes again (elapsed time; 0: closed); changes redraw what it guards. */
    val until: StateFlow<Long> = openUntil

    fun isOpen(): Boolean = SystemClock.elapsedRealtime() < openUntil.value

    fun open() {
        openUntil.value = SystemClock.elapsedRealtime() + OPEN_MS
    }

    fun close() {
        openUntil.value = 0L
    }

    /** Closes the lock whenever the screen goes off (registered once, in the keyboard's process). */
    fun watchScreen(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) = close()
        }
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                context.applicationContext, receiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    /** The history opens again once the keyboard is back (after the fingerprint screen). */
    @Volatile var reopen = false

    /** From the keyboard: the fingerprint screen asks (the keyboard goes away while it does). */
    fun ask(context: Context) {
        reopen = true
        val started = runCatching {
            context.startActivity(Intent(context, ClipboardUnlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!started) {
            reopen = false
            Toast.makeText(context, R.string.pysanka__clipboard_lock__failed, Toast.LENGTH_LONG).show()
        }
    }

    /** Asks for the fingerprint over [activity]; [done] gets whether it was given. */
    fun prompt(activity: Activity, cancel: CancellationSignal, done: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 29) return done(false)
        val builder = BiometricPrompt.Builder(activity).setTitle(activity.getString(R.string.pysanka__clipboard_lock__prompt))
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        runCatching {
            builder.build().authenticate(cancel, activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    open()
                    done(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = done(false)
            })
        }.onFailure { done(false) }
    }
}

/** Pysanka: the see-through screen that asks for the fingerprint (or screen lock) for [ClipboardLock]. */
class ClipboardUnlockActivity : Activity() {
    private val cancel = CancellationSignal()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ClipboardLock.prompt(this, cancel) { ok ->
            if (!ok) ClipboardLock.reopen = false
            finish()
        }
    }

    override fun onDestroy() {
        cancel.cancel()
        super.onDestroy()
    }
}

/**
 * Pysanka: on a settings screen, [content] (texts from the history) only after the fingerprint,
 * when the person chose the lock.
 */
@Composable
fun GuardedByLock(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val on by prefs.clipboard.pysankaFingerprint.observeAsState()
    val until by ClipboardLock.until.collectAsState()
    if (!on || !ClipboardLock.supported(context) || (until > 0 && ClipboardLock.isOpen())) {
        content()
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringRes(R.string.pysanka__clipboard_lock__title), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = {
            var c: Context? = context
            while (c is android.content.ContextWrapper && c !is Activity) c = c.baseContext
            (c as? Activity)?.let { a -> ClipboardLock.prompt(a, CancellationSignal()) {} }
        }) { Text(stringRes(R.string.pysanka__clipboard_lock__open)) }
    }
}
