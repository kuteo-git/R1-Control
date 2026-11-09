package com.r1control.app.qa

import com.r1control.app.domain.model.RecentSearch
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.usecase.SearchMusic
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.testing.FakeSpeaker
import com.r1control.app.testing.MemoryPreferences
import com.r1control.app.testing.musicStore
import com.r1control.app.testing.track
import com.r1control.app.ui.Problem
import com.r1control.app.ui.music.MusicPhase
import com.r1control.app.ui.music.MusicViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class QaMusicTest {
    private fun TestScope.music(speaker: FakeSpeaker, prefs: MemoryPreferences = MemoryPreferences()): MusicViewModel {
        val scope = backgroundScope
        val feed = SpeakerFeed(speaker, scope)
        return MusicViewModel(SearchMusic(speaker, prefs) { 7 }, prefs, musicStore(speaker, feed, scope, prefs), feed, scope)
    }

    private fun tracks(n: Int) = (0 until n).map { track("t$it") }
    private fun idList(from: Int, toInclusive: Int) = (from..toInclusive).map { "t$it" }

    @Test fun aSongFoundByTwoKeywordsIsListedOnceAtItsFirstPlace() = runTest {
        val speaker = FakeSpeaker().apply {
            searchAnswers = { k ->
                when (k) {
                    "thanh ngân" -> listOf(track("x"), track("y"))
                    "sương trắng" -> listOf(track("y"), track("z"))
                    else -> emptyList()
                }
            }
        }
        val vm = music(speaker)
        vm.onQuery("thanh ngân, sương trắng")
        vm.search()
        runCurrent()
        assertIs<MusicPhase.Done>(vm.state.value.phase)
        assertEquals(listOf("x", "y", "z"), vm.state.value.results.tracks.map { it.id })
    }

    @Test fun theSameVietnameseKeywordTwiceIsSearchedOnce() = runTest {
        val speaker = FakeSpeaker()
        val vm = music(speaker)
        vm.onQuery("Thành Tâm,  thành   tâm ,")
        vm.search()
        runCurrent()
        assertEquals(listOf("search Thành Tâm"), speaker.calls)
    }

    @Test fun playAllOfMoreThanThirtySendsTheFirstThirtyFromTheTop() = runTest {
        val speaker = FakeSpeaker().apply { searchAnswers = { tracks(45) } }
        val vm = music(speaker)
        vm.onQuery("q")
        vm.search()
        advanceTimeBy(1_000)
        speaker.calls.clear()
        vm.play(0)
        runCurrent()
        assertEquals(listOf("play ${idList(0, 29)} from 0"), speaker.calls)
    }

    @Test fun tappingNearTheEndOfALongListSendsTheWindowEndingThere() = runTest {
        val speaker = FakeSpeaker().apply { searchAnswers = { tracks(45) } }
        val vm = music(speaker)
        vm.onQuery("q")
        vm.search()
        advanceTimeBy(1_000)
        speaker.calls.clear()
        vm.play(40)
        runCurrent()
        assertEquals(listOf("play ${idList(15, 44)} from 25"), speaker.calls)
    }

    @Test fun clearingDuringASearchDropsItsAnswerAndIsNotAFailure() = runTest {
        val speaker = FakeSpeaker().apply {
            searchDelayMillis = { 1_000 }
            searchAnswers = { listOf(track("late")) }
        }
        val prefs = MemoryPreferences()
        val vm = music(speaker, prefs)
        vm.onQuery("bolero")
        vm.search()
        advanceTimeBy(500)
        assertEquals(MusicPhase.Searching, vm.state.value.phase)
        vm.clearResults()
        advanceTimeBy(2_000)
        assertEquals(MusicPhase.Idle, vm.state.value.phase)
        assertEquals(Problem.None, vm.state.value.problem)
        assertTrue(prefs.recents.isEmpty(), "a cancelled search is not a finished one")
    }

    /** A stored history entry with no keywords (an older or half-written store) must leave the field empty, not search. */
    @Test fun aRecentWithNoKeywordsLeavesTheFieldEmpty() = runTest {
        val speaker = FakeSpeaker()
        val vm = music(speaker)
        vm.searchAgain(RecentSearch(emptyList(), 1))
        runCurrent()
        assertEquals("", vm.query.value)
        assertEquals(MusicPhase.Idle, vm.state.value.phase)
        assertTrue(speaker.calls.isEmpty())
    }

    @Test fun aRecentSearchKeepsItsVietnameseKeywordsAsTyped() = runTest {
        val prefs = MemoryPreferences()
        val speaker = FakeSpeaker().apply { searchAnswers = { listOf(track("q")) } }
        val vm = music(speaker, prefs)
        vm.onQuery(" Sương Trắng Miền Quê Ngoại ")
        vm.search()
        runCurrent()
        assertEquals(listOf(RecentSearch(listOf("Sương Trắng Miền Quê Ngoại"), 7)), prefs.recents)
        assertEquals(prefs.recents, vm.state.value.recents)
    }
}

class QaSearchCancellationTest {
    @Test fun aCancelledSearchRemembersNothingAndThrowsNoFailure() = runTest {
        val speaker = FakeSpeaker().apply {
            searchDelayMillis = { 1_000 }
            searchAnswers = { listOf<Track>(track("a")) }
        }
        val prefs = MemoryPreferences()
        val search = SearchMusic(speaker, prefs) { 1 }
        val job = launch { search(listOf("a", "b")) }
        advanceTimeBy(500)
        job.cancel()
        advanceUntilIdle()
        assertTrue(job.isCancelled)
        assertTrue(prefs.recents.isEmpty())
        assertTrue(speaker.calls.isEmpty(), "neither keyword finished")
    }
}
