package ua.pysanka.ai

import ua.pysanka.models.ModelStore
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pysanka: the AI editor from other apps, without the keyboard:
 * - "Share" → "Pysanka AI" with any text (an article, a long message, a document): opens the
 *   editor with "Summary" chosen; the answer can be copied.
 * These are the person's explicit choice in another app, so the keyboard's field rules (password,
 * incognito) do not apply here: the text is what the person chose to send.
 * - The text selection menu → "Pysanka AI": opens the editor with "Grammar" chosen; if the app
 *   allows it, "Replace" puts the answer in place of the selection.
 *
 * This small screen is in the keyboard's process (so it can pass the "block screenshots" choice
 * along) and shows nothing itself; the editor runs in its own process as always. Any app may send
 * text here, so only plain text is taken, and no more than [AiJob.HARD_MAX] characters.
 */
class AiEntryActivity : ComponentActivity() {
    private companion object {
        const val SETTINGS_WAIT_MS = 2000L
        const val KEY_LAUNCHED = "launched"
    }

    private val editor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringExtra(AiEditorActivity.EXTRA_TEXT)
        if (intent.action == Intent.ACTION_PROCESS_TEXT && r.resultCode == RESULT_OK && text != null) {
            setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text))
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Already waiting for the editor (this screen was only recreated)
        if (savedInstanceState?.getBoolean(KEY_LAUNCHED) == true) return
        // A cold start loads the settings in the background: wait for them (briefly), so the
        // editor gets the person's "block screenshots" and language, not the defaults
        lifecycleScope.launch {
            withTimeoutOrNull(SETTINGS_WAIT_MS) {
                dev.patrickgold.florisboard.FlorisApplication.getHack().preferenceStoreLoaded.first { it }
            }
            // Only while on screen (a start from the background could be blocked)
            lifecycle.withStarted { open() }
        }
    }

    /** The editor was started: a recreated screen only waits for its answer. */
    private var launched = false

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LAUNCHED, launched)
    }

    private fun open() {
        launched = true
        val text: String
        val preset: AiAction
        var canReplace = false
        when (intent.action) {
            Intent.ACTION_SEND -> {
                text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
                preset = AiAction.SUMMARY
            }
            Intent.ACTION_PROCESS_TEXT -> {
                text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
                preset = AiAction.FIX
                canReplace = !intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
            }
            else -> {
                finish()
                return
            }
        }
        if (text.isBlank()) {
            finish()
            return
        }
        val body = text.take(AiJob.HARD_MAX)
        if (canReplace) {
            // The answer comes back here and goes in place of the selection
            editor.launch(AiEditorActivity.intent(this, body, preset, receiver = null, canReplace = true, forResult = true))
        } else {
            // Nothing to put back: the editor only shows and copies the answer
            startActivity(AiEditorActivity.intent(this, body, preset, receiver = null, canReplace = false, alongside = true))
            finish()
        }
    }
}

/**
 * Pysanka: "Pysanka AI" shows up in "Share" and in the text selection menu only while the model is
 * installed (without it there is nothing it could do).
 */
object AiEntry {
    fun sync(context: Context) {
        val on = ModelStore(context).modelFile() != null
        val pm = context.packageManager
        val component = ComponentName(context, AiEntryActivity::class.java)
        val wanted = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        if (pm.getComponentEnabledSetting(component) != wanted) {
            runCatching { pm.setComponentEnabledSetting(component, wanted, PackageManager.DONT_KILL_APP) }
        }
    }
}
