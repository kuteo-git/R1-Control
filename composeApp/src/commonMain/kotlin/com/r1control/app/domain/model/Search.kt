package com.r1control.app.domain.model

/**
 * Round-robin merge of several result lists: every list's first, then every
 * list's second, and so on. A song already placed (same [Track.id]) is dropped
 * wherever it comes up again, so the first occurrence in round-robin order wins.
 */
fun mixInterleaved(lists: List<List<Track>>): List<Track> {
    val seen = HashSet<String>()
    val mixed = ArrayList<Track>()
    val longest = lists.maxOfOrNull { it.size } ?: 0
    for (round in 0 until longest) {
        for (list in lists) {
            val track = list.getOrNull(round) ?: continue
            if (seen.add(track.id)) mixed.add(track)
        }
    }
    return mixed
}

/** One finished multi-keyword search, as the Music tab remembers it. */
data class RecentSearch(val keywords: List<String>, val atMillis: Long)

/**
 * How two keyword lists are compared: trimmed, lower-cased, inner whitespace
 * collapsed, order ignored. Order is ignored because the same keywords in
 * another order find the same songs; only the mix order differs, and the newer
 * entry replaces the older so the last typed order is the one kept.
 */
fun keywordSetKey(keywords: List<String>): Set<String> =
    keywords.map(::normalizedKeyword).filter { it.isNotEmpty() }.toSet()

fun normalizedKeyword(keyword: String): String =
    keyword.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ").lowercase()

/** The keywords worth searching: trimmed, blanks and repeats (by [normalizedKeyword]) removed, first spelling kept. */
fun cleanKeywords(keywords: List<String>): List<String> {
    val seen = HashSet<String>()
    return keywords.map { it.trim() }.filter { it.isNotEmpty() && seen.add(normalizedKeyword(it)) }
}

/**
 * The keywords in the one search field: split on a comma (the full-width one a
 * Vietnamese keyboard types too), trimmed, blanks and repeats dropped.
 */
fun keywordsIn(text: String): List<String> = cleanKeywords(text.split(',', '，'))

/** [keywords] as they read in the search field: the text [keywordsIn] gives them back from. */
fun keywordsText(keywords: List<String>): String = keywords.joinToString(", ")

/**
 * [recents] with [search] put first, an older entry with the same keyword set
 * removed, and the list cut to [cap]. A search with no usable keyword leaves
 * [recents] as it was.
 */
fun rememberSearch(recents: List<RecentSearch>, search: RecentSearch, cap: Int = RECENT_SEARCH_CAP): List<RecentSearch> {
    val keywords = cleanKeywords(search.keywords)
    if (keywords.isEmpty()) return recents
    val key = keywordSetKey(keywords)
    val kept = recents.filter { keywordSetKey(it.keywords) != key }
    return (listOf(RecentSearch(keywords, search.atMillis)) + kept).take(cap)
}

/** [recents] without the entry that has [search]'s keyword set. */
fun forgetSearch(recents: List<RecentSearch>, search: RecentSearch): List<RecentSearch> {
    val key = keywordSetKey(search.keywords)
    return recents.filter { keywordSetKey(it.keywords) != key }
}

/**
 * How many recent searches are kept. Applied on write (`rememberSearch`) and on
 * read (`SettingsPreferencesRepository.recentSearches`), so a phone upgraded
 * with 20 stored shows 10 before its next search.
 */
const val RECENT_SEARCH_CAP = 10

/** The R1's own cap on a queue: the panel sends at most this many songs, windowed around the tapped one. */
const val PLAY_QUEUE_MAX = 30

/** A queue to hand to the speaker and where in it to start. */
data class PlayQueue(val tracks: List<Track>, val startIndex: Int)

/**
 * The window of [tracks] to send when row [index] was tapped: all of them up to
 * [max], otherwise the [max] that still contain the tapped song (songs above it
 * stay, as the panel keeps them). The R1 takes the list in a POST body, but
 * the panel caps it, and so does this app.
 */
fun queueAround(tracks: List<Track>, index: Int, max: Int = PLAY_QUEUE_MAX): PlayQueue {
    val at = index.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
    if (tracks.size <= max) return PlayQueue(tracks, at)
    val from = at.coerceAtMost(tracks.size - max)
    return PlayQueue(tracks.subList(from, from + max), at - from)
}
