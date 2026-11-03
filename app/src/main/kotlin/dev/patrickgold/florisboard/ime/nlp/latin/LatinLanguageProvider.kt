/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.nlp.latin

import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.nlp.SpellingProvider
import dev.patrickgold.florisboard.ime.nlp.SpellingResult
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import dev.patrickgold.florisboard.R
import org.florisboard.lib.android.readText
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.kotlin.guardedByLock
import ua.pysanka.typing.AutoCorrect
import ua.pysanka.typing.KeyNeighbours
import ua.pysanka.typing.PersonalWords
import ua.pysanka.typing.PreviousWord
import ua.pysanka.typing.UserWords
import ua.pysanka.typing.WordSuggester

class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        // Default user ID used for all subtypes, unless otherwise specified.
        // See `ime/core/Subtype.kt` Line 210 and 211 for the default usage
        const val ProviderId = "org.florisboard.nlp.providers.latin"
    }

    private val appContext by context.appContext()

    private val prefs by FlorisPreferenceStore

    // Pysanka: a word list per language (assets/ime/dict/<language>.json), loaded when a language
    // is first used, and the person's own words (UserWords, encrypted)
    private val suggesters = guardedByLock { mutableMapOf<String, WordSuggester?>() }
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())

    private suspend fun suggester(subtype: Subtype): WordSuggester? {
        val lang = subtype.primaryLocale.language
        // Pysanka: the languages' lists of swear words and little words (assets)
        ua.pysanka.typing.LanguageExtras.load(appContext)
        return suggesters.withLock { map ->
            // A language without a list is remembered as such (not looked for on every key)
            if (lang in map) return@withLock map[lang]
            // A language without a list (Polish, German…) gets an empty one: the person's own words
            // and text shortcuts still come, corrections never (see ua.pysanka.typing.WordLists)
            runCatching {
                val words = if (ua.pysanka.typing.WordLists.has(appContext, lang)) {
                    Json.decodeFromString(wordDataSerializer, appContext.assets.readText("ime/dict/$lang.json"))
                } else {
                    emptyMap()
                }
                WordSuggester(words, KeyNeighbours.forLanguage(lang), lang)
            }.getOrNull().also { map[lang] = it }
        }
    }

    /** Pysanka: hashtags for [prefix] (see [ua.pysanka.typing.Hashtags]); [text]: the text being written. */
    suspend fun pysankaHashtags(subtype: Subtype, prefix: String, text: String): List<ua.pysanka.typing.HashtagCandidate> {
        val dict = suggester(subtype)
        // The person's own only with learning on (as for words)
        val own = if (prefs.correction.pysankaLearnWords.get()) {
            withContext(Dispatchers.IO) { UserWords.get(appContext, ua.pysanka.typing.Hashtags.LIST) }
        } else null
        val frequency = { w: String -> dict?.frequency(w) ?: 0 }
        val complete = { p: String -> dict?.suggest(p, 3)?.map { it.word }?.filter { PersonalWords.key(it).startsWith(PersonalWords.key(p)) }.orEmpty() }
        val hasList = ua.pysanka.typing.WordLists.has(appContext, subtype.primaryLocale.language)
        val single = ua.pysanka.typing.Hashtags.suggest(prefix, text, own, frequency, complete, hasList = hasList)
            .map { ua.pysanka.typing.HashtagCandidate(it) }
        // Right after «#»: the text's key words, all at once, first
        if (prefix.isNotEmpty()) return single
        val bundle = ua.pysanka.typing.Hashtags.keywords(
            text, frequency, complete, ua.pysanka.typing.Hashtags.BUNDLE,
            hasList = hasList,
        )
        return if (bundle.size >= 2) listOf(ua.pysanka.typing.HashtagCandidate(bundle)) + single else single
    }

    /**
     * Pysanka: the likely next letters; at a word's start also the first letters of the words the
     * person usually writes after the previous one (half and half with the word list's).
     */
    private fun pysankaNextLetters(dict: WordSuggester, word: String, content: EditorContent, own: PersonalWords?): Map<Char, Double> {
        val base = dict.nextLetters(word)
        if (word.isNotEmpty() || own == null) return base
        val previous = ua.pysanka.typing.PreviousWord.of(content.textBeforeSelection) ?: return base
        val firsts = dict.predict(previous, 5, own).mapNotNull { PersonalWords.key(it).firstOrNull() }
        if (firsts.isEmpty()) return base
        val habit = firsts.groupingBy { it }.eachCount().mapValues { it.value.toDouble() / firsts.size }
        return (base.keys + habit.keys).associateWith { 0.5 * (base[it] ?: 0.0) + 0.5 * (habit[it] ?: 0.0) }
    }

    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()

    /** Pysanka: the learned words, unless learning is off (then they are not used either). */
    private fun own(subtype: Subtype): PersonalWords? =
        if (prefs.correction.pysankaLearnWords.get()) UserWords.get(appContext, subtype.primaryLocale.language) else null

    /**
     * Pysanka: whether the person's own words may be shown here: suggestions on, and not a
     * password, incognito or "no learning" field (nor a number or phone keyboard).
     */
    private fun mayShowOwn(isPrivateSession: Boolean): Boolean =
        prefs.suggestion.enabled.get() && keyboardManager.activeState.isComposingEnabled && !isPrivateSession &&
            !ua.pysanka.privacy.FieldPrivacy.isPrivate && !ua.pysanka.privacy.FieldPrivacy.isPassword

    override val providerId = ProviderId

    override suspend fun create() {
        // Here we initialize our provider, set up all things which are not language dependent.
    }

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        suggester(subtype)
        own(subtype)
        Unit
    }

    override suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SpellingResult {
        // Pysanka: no list, nothing to check against
        if (!ua.pysanka.typing.WordLists.has(appContext, subtype.primaryLocale.language)) return SpellingResult.unspecified()
        val dict = suggester(subtype) ?: return SpellingResult.unspecified()
        // Pysanka: the spell checker is open to other apps, so it never uses the learned words
        // (an app could otherwise test which words the person uses)
        if (word.isBlank() || dict.contains(word)) return SpellingResult.validWord()
        val options = dict.suggest(word, maxSuggestionCount).map { it.word }.filter { !it.equals(word, ignoreCase = true) }
        return if (options.isEmpty()) SpellingResult.unspecified() else SpellingResult.typo(options.toTypedArray())
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        val word = content.composingText
        val dict = suggester(subtype)
        if (dict == null) {
            ua.pysanka.typing.KeyTargeting.publish(null)
            return emptyList()
        }
        // Pysanka: a language without a word list: taps as drawn, no corrections
        val hasList = ua.pysanka.typing.WordLists.has(appContext, subtype.primaryLocale.language)
        val own = if (mayShowOwn(isPrivateSession)) withContext(Dispatchers.IO) { own(subtype) } else null
        // Pysanka: the shortcuts in memory before taps are judged (see TextShortcuts.startsWith)
        if (!ua.pysanka.privacy.FieldPrivacy.isPassword) ua.pysanka.typing.TextShortcuts.load(appContext)
        // Pysanka: the likely next letters, for a tap near the edge between two keys (KeyTargeting);
        // never in a private or password field, nor while a word the person taught is being typed
        ua.pysanka.typing.KeyTargeting.publish(
            if (hasList && prefs.correction.pysankaKeyTargeting.get() && !isPrivateSession && keyboardManager.activeState.isComposingEnabled &&
                !ua.pysanka.privacy.FieldPrivacy.isPrivate && !ua.pysanka.privacy.FieldPrivacy.isPassword &&
                (word.isEmpty() || own?.withPrefix(PersonalWords.key(word), 1).isNullOrEmpty()) &&
                // nor while a text-replacement shortcut is being typed (it is no word of the list)
                !ua.pysanka.typing.TextShortcuts.startsWith(word)
            ) {
                ua.pysanka.typing.KeyTargeting.Priors(PersonalWords.key(word), pysankaNextLetters(dict, word, content, own))
            } else {
                null
            },
        )
        // Pysanka: before a word is begun, the words that usually follow the one before (also
        // right after a tapped suggestion, whose space comes with the next word)
        if (word.isEmpty()) {
            if (own == null || !content.selection.isCursorMode) return emptyList()
            val previous = PreviousWord.of(content.textBeforeSelection, spaceComing = editorInstance.phantomSpace.isActive)
                ?: return emptyList()
            return dict.predict(previous, maxCandidateCount, own).map {
                NextWordCandidate(text = it, previous = previous, sourceProvider = this@LatinLanguageProvider)
            }
        }
        // Pysanka: a shortcut the person made («дд» → «Добрий день!»), first and applied by a space;
        // never in a password field
        val shortcut = if (ua.pysanka.privacy.FieldPrivacy.isPassword || AutoCorrect.isRejected(word)) null else {
            ua.pysanka.typing.TextShortcuts.find(word)?.let { listOf(ua.pysanka.typing.ShortcutCandidate(word, it)) }
        }
        val autoCorrect = hasList && prefs.correction.pysankaAutoCorrect.get() && shortcut == null
        val previous = PreviousWord.of(content.textBeforeSelection.dropLast(word.length))
        val found = dict.suggest(
            word, maxCandidateCount, AutoCorrect.rejected, own, previous,
            blockOffensive = prefs.suggestion.blockPossiblyOffensive.get(),
        )
        AutoCorrect.suggestedFor = word
        // Pysanka: Ukrainian words with an apostrophe get the same ʼ the key types (smart punctuation),
        // not the list's straight one, unless one was typed (then that one is kept)
        val ukApostrophe = subtype.primaryLocale.language == "uk" && prefs.correction.pysankaSmartPunctuation.get() &&
            word.none { it == '\'' || it == '’' || it == 'ʼ' }
        val list = found.map {
            WordSuggestionCandidate(
                text = if (ukApostrophe) it.word.replace('\'', 'ʼ') else it.word,
                confidence = if (it.autoCorrect) 0.9 else 0.5,
                isEligibleForAutoCommit = autoCorrect && it.autoCorrect,
                // Only a learned word can be forgotten with a long press
                isEligibleForUserRemoval = dict.isOwn(it.word, own),
                sourceProvider = this@LatinLanguageProvider,
            )
        }
        // The word as typed is offered next to its correction, so it can be kept with a tap
        val corrected = list.firstOrNull()?.isEligibleForAutoCommit == true
        if (shortcut != null) return shortcut + list.take(maxCandidateCount - 1)
        return if (corrected) {
            listOf(
                list.first(),
                WordSuggestionCandidate(text = word, confidence = 0.5, isEligibleForUserRemoval = false, sourceProvider = this@LatinLanguageProvider),
            ) +
                list.drop(1).take(maxCandidateCount - 2)
        } else {
            list
        }
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        // Pysanka: words are learned in KeyboardManager, where the field's privacy is known
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        // Pysanka: a long press on a learned word forgets it; on an offered next word, that it follows
        val word = candidate.text.toString()
        val dict = suggester(subtype) ?: return false
        val language = subtype.primaryLocale.language
        if (candidate is NextWordCandidate) {
            return UserWords.forgetPair(appContext, language, candidate.previous, word).also { forgotten ->
                if (forgotten) appContext.showLongToast(R.string.pysanka__typing__next_word_forgotten, "word" to word)
            }
        }
        // Called on the main thread: only words already in memory (as they were just offered)
        if (!dict.isOwn(word, UserWords.peek(language))) return false
        return UserWords.forget(appContext, language, word).also { forgotten ->
            if (forgotten) appContext.showLongToast(R.string.pysanka__typing__word_forgotten, "word" to word)
        }
    }

    // Pysanka: swipe knows the person's own words too (those already in memory: the list is
    // taken through runBlocking), and how often they use words. The list is kept for the session,
    // so whether learned words may count is decided per swipe, by their frequency: 0 (in a
    // private field, with suggestions off, or once forgotten) leaves them out.
    private fun ownInMemory(subtype: Subtype): PersonalWords? =
        if (prefs.correction.pysankaLearnWords.get()) UserWords.peek(subtype.primaryLocale.language) else null

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        return suggester(subtype)?.words(ownInMemory(subtype)) ?: emptyList()
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        val own = if (mayShowOwn(keyboardManager.activeState.isIncognitoMode)) ownInMemory(subtype) else null
        return (suggester(subtype)?.frequency(word, own) ?: 0) / 255.0
    }

    override suspend fun destroy() {
        // Here we have the chance to de-allocate memory and finish our work. However this might never be called if
        // the app process is killed (which will most likely always be the case).
    }
}

/** Pysanka: a word offered before anything is typed, because it often followed [previous]. */
data class NextWordCandidate(
    override val text: CharSequence,
    val previous: String,
    override val sourceProvider: SuggestionProvider?,
) : SuggestionCandidate {
    override val secondaryText: CharSequence? = null
    override val confidence: Double = 0.5
    override val isEligibleForAutoCommit: Boolean = false
    override val isEligibleForUserRemoval: Boolean = true
    override val icon: androidx.compose.ui.graphics.vector.ImageVector? = null
}
