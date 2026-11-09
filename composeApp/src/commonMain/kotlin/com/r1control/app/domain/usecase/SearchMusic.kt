package com.r1control.app.domain.usecase

import com.r1control.app.domain.model.RecentSearch
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.model.cleanKeywords
import com.r1control.app.domain.model.mixInterleaved
import com.r1control.app.domain.model.rememberSearch
import com.r1control.app.domain.repository.PreferencesRepository
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.repository.SpeakerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class FailedKeyword(val keyword: String, val error: SpeakerException)

/**
 * What a multi-keyword search found. [keywords] are the ones actually
 * searched (cleaned); [failed] says which of them got no answer, so the screen
 * can show the songs it has and name what is missing.
 */
data class SearchOutcome(
    val keywords: List<String>,
    val tracks: List<Track>,
    val failed: List<FailedKeyword>,
) {
    val searchedAnything: Boolean get() = keywords.isNotEmpty()
    val allFailed: Boolean get() = keywords.isNotEmpty() && failed.size == keywords.size
}

/**
 * Searches every keyword at once, interleaves the answers and remembers the
 * search. One keyword failing never fails the others.
 */
class SearchMusic(
    private val speaker: SpeakerRepository,
    private val preferences: PreferencesRepository,
    private val nowMillis: () -> Long,
) {
    suspend operator fun invoke(rawKeywords: List<String>): SearchOutcome {
        val keywords = cleanKeywords(rawKeywords)
        if (keywords.isEmpty()) return SearchOutcome(emptyList(), emptyList(), emptyList())

        val answers = coroutineScope {
            keywords.map { keyword ->
                async {
                    try {
                        Result.success(speaker.search(keyword))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: SpeakerException) {
                        Result.failure(e)
                    }
                }
            }.awaitAll()
        }

        val failed = keywords.zip(answers).mapNotNull { (keyword, answer) ->
            answer.exceptionOrNull()?.let { FailedKeyword(keyword, it as SpeakerException) }
        }
        val outcome = SearchOutcome(
            keywords = keywords,
            tracks = mixInterleaved(answers.map { it.getOrDefault(emptyList()) }),
            failed = failed,
        )
        if (!outcome.allFailed) remember(keywords)
        return outcome
    }

    // Best effort: a phone that cannot write its history must still show the songs it found.
    private suspend fun remember(keywords: List<String>) {
        try {
            val recents = preferences.recentSearches()
            preferences.saveRecentSearches(rememberSearch(recents, RecentSearch(keywords, nowMillis())))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}
