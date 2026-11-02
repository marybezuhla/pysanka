package ua.pysanka.ai

import android.app.Application
import android.os.Build

/** Pysanka: tells the model processes (the AI's ":ai", dictation's ":voice") apart from the keyboard's. */
object AiProcess {
    fun isAiProcess(): Boolean = name()?.let { it.endsWith(":ai") || it.endsWith(":voice") } == true

    private fun name(): String? {
        if (Build.VERSION.SDK_INT >= 28) return Application.getProcessName()
        return runCatching {
            java.io.File("/proc/self/cmdline").readText().trimEnd(Char(0)).substringBefore(Char(0))
        }.getOrNull()
    }
}
