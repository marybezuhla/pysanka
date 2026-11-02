/*
 * Copyright (C) 2024-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.media.emoji

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.stream.Collectors
import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.nlp.EmojiSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.lib.FlorisLocale
import io.github.reactivecircus.cache4k.Cache

/**
 * Provides emoji suggestions within a text input context.
 *
 * This class handles the following tasks:
 * - Initializes and maintains a list of supported emojis.
 * - Generates and returns emoji suggestions based on user input and preferences.
 *
 * @param context The application context.
 */
class EmojiSuggestionProvider(private val context: Context) : SuggestionProvider {
    override val providerId = "org.florisboard.nlp.providers.emoji"

    private val prefs by FlorisPreferenceStore
    // Pysanka: letters of any alphabet (it was Latin only, so ":кава" found nothing), and apostrophes
    private val lettersRegex = "^[\\p{L}'ʼ’]*$".toRegex()

    private val cachedEmojiMappings = Cache.Builder<FlorisLocale, EmojiDataBySkinTone>().build()

    override suspend fun create() {
    }

    override suspend fun preload(subtype: Subtype) {
        subtype.locales().forEach { locale ->
            cachedEmojiMappings.get(locale) {
                EmojiData.get(context, locale).bySkinTone
            }
        }
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean
    ): List<SuggestionCandidate> {
        val preferredSkinTone = prefs.emoji.preferredSkinTone.get()
        val showName = prefs.emoji.suggestionCandidateShowName.get()
        val query = validateInputQuery(content.composingText) ?: return emptyList()
        val emojis = cachedEmojiMappings.get(subtype.primaryLocale)?.get(preferredSkinTone) ?: emptyList()
        // Pysanka: for a word as it is typed (as Apple does), only an emoji the word really names:
        // a keyword that is the word, or (from 4 letters) begins with it; "при" offers nothing
        if (prefs.emoji.suggestionType.get() == EmojiSuggestionType.INLINE_TEXT) {
            return withContext(Dispatchers.Default) { inline(emojis, query, maxCandidateCount, showName) }
        }
        val candidates = withContext(Dispatchers.Default) {
            emojis.parallelStream()
                .map { emoji ->
                    val nameWeight = emoji.name.containsWeighted(query, ignoreCase = true)
                    val keywordWeight = emoji.keywords
                        .any { it.contains(query, ignoreCase = true) }
                        .let { if (it) 1.0 else 0.0 }
                    emoji to (nameWeight * 0.7 + keywordWeight * 0.3)
                }
                .sorted { (_, a), (_, b) -> b.compareTo(a) }
                .limit(maxCandidateCount.toLong())
                .filter { (_, a) -> a > 0 }
                .map { (emoji, _) ->
                    EmojiSuggestionCandidate(
                        emoji = emoji,
                        showName = showName,
                        sourceProvider = this@EmojiSuggestionProvider,
                    )
                }
                .collect(Collectors.toList())
        }
        return candidates
    }

    private fun inline(emojis: List<Emoji>, query: String, max: Int, showName: Boolean): List<SuggestionCandidate> =
        pysankaEmojiFor(emojis, query, max).map { EmojiSuggestionCandidate(emoji = it, showName = showName, sourceProvider = this) }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        val updateHistory = prefs.emoji.suggestionUpdateHistory.get()
        if (!updateHistory || candidate !is EmojiSuggestionCandidate) {
            return
        }
        EmojiHistoryHelper.markEmojiUsed(prefs, candidate.emoji)
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        // No-op
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate) = false

    override suspend fun getListOfWords(subtype: Subtype) = emptyList<String>()

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String) = 0.0

    override suspend fun destroy() {
        cachedEmojiMappings.invalidateAll()
    }

    /**
     * Validates the user input query for emoji suggestions.
     */
    private fun validateInputQuery(composingText: CharSequence): String? {
        val prefix = prefs.emoji.suggestionType.get().prefix
        val queryMinLength = prefs.emoji.suggestionQueryMinLength.get() + prefix.length
        if (prefix.isNotEmpty() && !composingText.startsWith(prefix)) {
            return null
        }
        if (composingText.length < queryMinLength) {
            return null
        }
        val emojiPartialName = composingText.substring(prefix.length)
        if (!lettersRegex.matches(emojiPartialName)) {
            return null
        }
        return emojiPartialName
    }
}

private fun String.containsWeighted(other: String, ignoreCase: Boolean = false): Double = let { str ->
    if (str.contains(other, ignoreCase = ignoreCase)) {
        other.length.toDouble() / str.length.toDouble()
    } else {
        0.0
    }
}

/**
 * Pysanka: the emoji a typed word names, the closest first: a keyword (or the name) that is the
 * word, then, from 4 letters, one with a word that begins with it ("кав" offers nothing, "кава" ☕).
 */
internal fun pysankaEmojiFor(emojis: List<Emoji>, query: String, max: Int): List<Emoji> {
    val q = query.lowercase()
    fun score(e: Emoji): Int {
        val words = e.keywords.map { it.lowercase() } + e.name.lowercase()
        return when {
            words.any { it == q } -> 2
            q.length >= 4 && words.any { w -> w.split(' ').any { it.startsWith(q) } } -> 1
            else -> 0
        }
    }
    return emojis.map { it to score(it) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(max).map { it.first }
}
