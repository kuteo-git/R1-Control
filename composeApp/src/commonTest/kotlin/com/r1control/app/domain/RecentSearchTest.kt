package com.r1control.app.domain

import com.r1control.app.domain.model.RECENT_SEARCH_CAP
import com.r1control.app.domain.model.RecentSearch
import com.r1control.app.domain.model.cleanKeywords
import com.r1control.app.domain.model.rememberSearch
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentSearchTest {
    private fun s(vararg k: String, at: Long = 0) = RecentSearch(k.toList(), at)

    @Test fun newestFirst() {
        val out = rememberSearch(listOf(s("a", at = 1)), s("b", at = 2))
        assertEquals(listOf(listOf("b"), listOf("a")), out.map { it.keywords })
    }

    @Test fun sameKeywordsIgnoringCaseAndPaddingReplaceTheOldEntry() {
        val out = rememberSearch(listOf(s("a", at = 1), s("Thanh Ngân", at = 2)), s("  thanh   ngân ", at = 3))
        assertEquals(2, out.size)
        assertEquals(listOf("thanh   ngân"), out.first().keywords)
        assertEquals(3L, out.first().atMillis)
    }

    @Test fun keywordOrderDoesNotMakeANewEntryAndTheNewerOrderWins() {
        val out = rememberSearch(listOf(s("a", "b", at = 1)), s("b", "a", at = 2))
        assertEquals(1, out.size)
        assertEquals(listOf("b", "a"), out.single().keywords)
    }

    @Test fun blanksAreRemovedAndASearchOfNothingChangesNothing() {
        val before = listOf(s("a"))
        assertEquals(before, rememberSearch(before, s("", "  ")))
        assertEquals(listOf("a", "b"), rememberSearch(emptyList(), s("a", " ", "b")).single().keywords)
    }

    @Test fun repeatsInsideOneSearchCollapse() {
        assertEquals(listOf("Sương"), rememberSearch(emptyList(), s("Sương", "sương ")).single().keywords)
        assertEquals(listOf("a", "b"), cleanKeywords(listOf("a", "A", " b ", "")))
    }

    @Test fun theCapIsTen() {
        assertEquals(10, RECENT_SEARCH_CAP)
    }

    @Test fun capDropsTheOldest() {
        var recents = emptyList<RecentSearch>()
        for (i in 1..25) recents = rememberSearch(recents, s("k$i", at = i.toLong()))
        assertEquals(10, recents.size)
        assertEquals("k25", recents.first().keywords.single())
        assertEquals("k16", recents.last().keywords.single())
    }

    @Test fun aRepeatedSearchDoesNotGrowTheListAtTheCap() {
        var recents = emptyList<RecentSearch>()
        for (i in 1..10) recents = rememberSearch(recents, s("k$i"))
        recents = rememberSearch(recents, s("K1"))
        assertEquals(10, recents.size)
        assertEquals("K1", recents.first().keywords.single())
    }
}
