package ua.pysanka.typing.swipe

import ua.pysanka.privacy.FieldPrivacy
import ua.pysanka.typing.PersonalWords
import ua.pysanka.typing.PreviousWord
import ua.pysanka.typing.UserWords

/**
 * Pysanka: the person's habits for a swipe: a word they often write after the previous one counts
 * more (from their own learned word pairs, encrypted; never in a private field or with learning
 * off).
 */
object SwipeContext {
    fun bonus(language: String): (String) -> Double {
        val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
        if (!prefs.correction.pysankaLearnWords.get() || FieldPrivacy.isPrivate || FieldPrivacy.isPassword) return { 0.0 }
        val own = UserWords.peek(language) ?: return { 0.0 }
        val editor = dev.patrickgold.florisboard.FlorisImeService.pysankaTextBefore() ?: return { 0.0 }
        // The swiped word comes after the previous one with a space (typed, or added with it)
        val previous = PreviousWord.of(editor) ?: PreviousWord.of(editor, spaceComing = true) ?: return { 0.0 }
        val prev = PersonalWords.key(previous)
        return { word -> SwipeDecoder.pairBonus(own.pairCount(prev, PersonalWords.key(word))) }
    }
}
