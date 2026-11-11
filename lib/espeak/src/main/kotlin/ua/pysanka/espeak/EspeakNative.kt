package ua.pysanka.espeak

/**
 * Pysanka: eSpeak NG, only to turn English text into IPA phonemes for the English voice (it was
 * trained on them). No sound and no files but its language data, whose folder [init] is given.
 */
object EspeakNative {
    @Volatile private var loaded = false

    /** Loads the library and starts eSpeak with its data in [dataParent] (the folder that holds espeak-ng-data). */
    @Synchronized
    fun init(dataParent: String): Boolean {
        if (!loaded) {
            runCatching { System.loadLibrary("pysanka_speech") }.onFailure { return false }
            loaded = true
        }
        return nativeInit(dataParent) > 0
    }

    /** The IPA phonemes of [text] in eSpeak's [voice] (one call at a time: eSpeak keeps state). */
    @Synchronized
    fun phonemes(text: String, voice: String): String? = if (!loaded) null else nativePhonemes(text, voice)

    @JvmStatic private external fun nativeInit(path: String): Int
    @JvmStatic private external fun nativePhonemes(text: String, voice: String): String?
}
