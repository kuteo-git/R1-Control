package com.r1control.app.ui

import com.r1control.app.domain.model.PlayRequest
import com.r1control.app.domain.model.RecentSearch
import com.r1control.app.testing.musicStore
import com.r1control.app.domain.model.SavedAddress
import com.r1control.app.domain.usecase.SearchMusic
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.testing.FakeSpeaker
import com.r1control.app.testing.MemoryPreferences
import com.r1control.app.testing.track
import com.r1control.app.testing.unreachable
import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.testing.media
import com.r1control.app.testing.speakerSnapshot
import com.r1control.app.ui.music.MusicPhase
import com.r1control.app.ui.music.SongMark
import com.r1control.app.ui.music.isOnSpeaker
import com.r1control.app.ui.music.songMarkOf
import com.r1control.app.domain.usecase.SpeakerView
import kotlinx.coroutines.launch
import com.r1control.app.ui.music.MusicViewModel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MusicViewModelTest {
    private fun TestScope.music(speaker: FakeSpeaker, prefs: MemoryPreferences = MemoryPreferences()): MusicViewModel {
        val scope = backgroundScope
        val feed = SpeakerFeed(speaker, scope)
        return MusicViewModel(SearchMusic(speaker, prefs) { 7 }, prefs, musicStore(speaker, feed, scope, prefs), feed, scope)
    }

    /** Searches so that [tracks] are the results on screen, the only way to a row to tap. */
    private suspend fun TestScope.showing(vm: MusicViewModel, speaker: FakeSpeaker, tracks: List<com.r1control.app.domain.model.Track>) {
        speaker.searchAnswers = { tracks }
        vm.onQuery("q")
        vm.search()
        advanceTimeBy(1_000)
        speaker.calls.clear()
    }

    private val answers: (String) -> List<com.r1control.app.domain.model.Track> = { k ->
        when (k) {
            "a" -> listOf(track("a1"), track("a2"))
            "b" -> listOf(track("b1"))
            else -> emptyList()
        }
    }

    @Test fun clearingTheQueryEmptiesTheFieldAndKeepsTheResults() = runTest {
        val s = FakeSpeaker()
        val vm = music(s)
        showing(vm, s, listOf(track("a1"), track("a2")))
        vm.clearQuery()
        assertEquals("", vm.query.value)
        assertIs<MusicPhase.Done>(vm.state.value.phase)
        assertEquals(2, vm.state.value.results.size)
    }

    @Test fun startsWithAnEmptyFieldAndNoSearch() = runTest {
        val vm = music(FakeSpeaker())
        runCurrent()
        assertEquals("", vm.query.value)
        assertEquals(MusicPhase.Idle, vm.state.value.phase)
        assertTrue(!vm.canSearch)
    }

    @Test fun onlyCommasAndBlanksIsNothingToSearch() = runTest {
        val vm = music(FakeSpeaker())
        vm.onQuery(" , ，  ,")
        assertTrue(!vm.canSearch)
        vm.onQuery("a,")
        assertTrue(vm.canSearch)
    }

    @Test fun searchMixesKeywordsAndShowsSearchingFirst() = runTest {
        val speaker = FakeSpeaker().apply { searchAnswers = answers; searchDelayMillis = { 100 } }
        val vm = music(speaker)
        vm.onQuery("a, b")
        vm.search()
        runCurrent()
        assertEquals(MusicPhase.Searching, vm.state.value.phase)
        advanceTimeBy(200)
        val done = assertIs<MusicPhase.Done>(vm.state.value.phase)
        assertEquals(listOf("a1", "b1", "a2"), vm.state.value.results.tracks.map { it.id })
        assertEquals(emptyList(), done.failed)
    }

    @Test fun aBlankSearchDoesNothing() = runTest {
        val speaker = FakeSpeaker()
        val vm = music(speaker)
        vm.search()
        runCurrent()
        assertEquals(MusicPhase.Idle, vm.state.value.phase)
        assertTrue(speaker.calls.isEmpty())
    }

    @Test fun aFinishedSearchJoinsTheRecentsNewestFirst() = runTest {
        val speaker = FakeSpeaker().apply { searchAnswers = answers }
        val prefs = MemoryPreferences(recents = listOf(RecentSearch(listOf("old"), 1)))
        val vm = music(speaker, prefs)
        runCurrent()
        assertEquals(listOf(listOf("old")), vm.state.value.recents.map { it.keywords })
        vm.onQuery("a")
        vm.search()
        advanceTimeBy(1_000)
        assertEquals(listOf(listOf("a"), listOf("old")), vm.state.value.recents.map { it.keywords })
    }

    @Test fun tappingARecentFillsTheFieldAsACommaListAndRunsItAgain() = runTest {
        val speaker = FakeSpeaker().apply { searchAnswers = answers }
        val vm = music(speaker)
        vm.searchAgain(RecentSearch(listOf("a", "b"), 5))
        advanceTimeBy(1_000)
        assertEquals("a, b", vm.query.value)
        assertEquals(listOf("search a", "search b").toSet(), speaker.calls.toSet())
        assertIs<MusicPhase.Done>(vm.state.value.phase)
    }

    @Test fun theLastQuestionWins() = runTest {
        val speaker = FakeSpeaker().apply {
            searchAnswers = answers
            searchDelayMillis = { if (it == "a") 1_000 else 10 }
        }
        val vm = music(speaker)
        vm.onQuery("a")
        vm.search()
        runCurrent()
        vm.onQuery("b")
        vm.search()
        advanceTimeBy(1_000)
        assertIs<MusicPhase.Done>(vm.state.value.phase)
        assertEquals(listOf("b1"), vm.state.value.results.tracks.map { it.id }, "only the last question's songs")
    }

    @Test fun failedKeywordsAreReportedNextToTheSongsFound() = runTest {
        val speaker = FakeSpeaker().apply {
            searchAnswers = { if (it == "b") throw unreachable else answers(it) }
        }
        val vm = music(speaker)
        vm.onQuery("a，b")
        vm.search()
        advanceTimeBy(1_000)
        val done = assertIs<MusicPhase.Done>(vm.state.value.phase)
        assertEquals(listOf("a1", "a2"), vm.state.value.results.tracks.map { it.id })
        assertEquals(listOf("b"), done.failed.map { it.keyword })
    }

    @Test fun tappingARowPlaysTheListFromThatRowAndAFailureStaysOnThatRow() = runTest {
        val speaker = FakeSpeaker()
        val vm = music(speaker)
        showing(vm, speaker, listOf(track("x"), track("y"), track("z")))
        vm.play(1)
        runCurrent()
        assertEquals(listOf("play [x, y, z] from 1"), speaker.calls)
        assertEquals(Problem.None, vm.state.value.problem)

        speaker.commandFailure = unreachable
        vm.play(0)
        runCurrent()
        assertEquals(PlayRequest.Failed("x", unreachable), vm.state.value.request)
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem)
        vm.onQuery("a")
        vm.search()
        advanceTimeBy(1_000)
        assertEquals(PlayRequest.None, vm.state.value.request, "a new search is a new list; the old failure goes with it")
    }

    /** U-10: a tap showed nothing until the speaker reported the song seconds later. */
    @Test fun theTappedRowIsMarkedAtOnceThenFollowsTheSpeaker() = runTest {
        val speaker = object : FakeSpeaker() {
            override suspend fun play(tracks: List<com.r1control.app.domain.model.Track>, startIndex: Int) {
                kotlinx.coroutines.delay(100)
                super.play(tracks, startIndex)
            }
        }
        val vm = music(speaker)
        backgroundScope.launch { vm.mark.collect {} }
        runCurrent()
        assertEquals(SongMark.None, vm.mark.value)

        showing(vm, speaker, listOf(track("x"), track("y")))
        vm.play(1)
        runCurrent()
        assertEquals(PlayRequest.Starting("y"), vm.state.value.request, "marked before the speaker answers")
        assertEquals(SongMark.Starting("y"), vm.mark.value)
        advanceTimeBy(200)
        speaker.current = speakerSnapshot(media = media(MediaStatus.Downloading, "y", "Song", downloadPercent = 20))
        advanceTimeBy(3_100)
        assertEquals(SongMark.Starting("y"), vm.mark.value, "still starting while the speaker downloads it")

        speaker.current = speakerSnapshot(media = media(MediaStatus.Playing, "y", "Song", 1, 100))
        advanceTimeBy(3_100)
        assertEquals(SongMark.OnSpeaker("y"), vm.mark.value)
    }

    @Test fun aLaterTapWinsOverTheEarlierTapsAnswer() = runTest {
        val speaker = FakeSpeaker()
        val vm = music(speaker)
        showing(vm, speaker, listOf(track("x"), track("y")))
        speaker.commandFailure = unreachable
        vm.play(0)
        vm.play(1)
        runCurrent()
        runCurrent()
        assertEquals(PlayRequest.Failed("y", unreachable), vm.state.value.request, "the first tap's answer did not take the second's place")
    }

    @Test fun searchingASetOfKeywordsStoresThemAsAListSoOlderRecentsStillLoad() = runTest {
        val speaker = FakeSpeaker().apply { searchAnswers = answers }
        val prefs = MemoryPreferences(recents = listOf(RecentSearch(listOf("x", "y"), 1)))
        val vm = music(speaker, prefs)
        vm.onQuery(" a ,b,, ")
        vm.search()
        advanceTimeBy(1_000)
        assertEquals(listOf(listOf("a", "b"), listOf("x", "y")), prefs.recents.map { it.keywords })
    }

    @Test fun aRecentSearchCanBeForgottenOnScreenAndInTheStore() = runTest {
        val prefs = MemoryPreferences(recents = listOf(RecentSearch(listOf("a"), 2), RecentSearch(listOf("Thanh Ngân"), 1)))
        val vm = music(FakeSpeaker(), prefs)
        runCurrent()
        vm.forget(RecentSearch(listOf("thanh ngân"), 1))
        runCurrent()
        assertEquals(listOf(listOf("a")), vm.state.value.recents.map { it.keywords })
        runCurrent()
        assertEquals(listOf(listOf("a")), prefs.recents.map { it.keywords })
    }

    @Test fun clearingResultsBringsTheRecentsBack() = runTest {
        val speaker = FakeSpeaker().apply { searchAnswers = answers }
        val vm = music(speaker)
        vm.onQuery("a")
        vm.search()
        advanceTimeBy(1_000)
        vm.clearResults()
        runCurrent()
        assertEquals(MusicPhase.Idle, vm.state.value.phase)
    }

    @Test fun onlyTheSongOnTheSpeakerIsTheLitRow() {
        assertTrue(SongMark.OnSpeaker("y").isOnSpeaker("y"))
        assertTrue(!SongMark.OnSpeaker("y").isOnSpeaker("x"))
        assertTrue(!SongMark.Starting("y").isOnSpeaker("y"), "a song still starting has a spinner, not the lit row")
        assertTrue(!SongMark.Failed("y", unreachable).isOnSpeaker("y"))
        assertTrue(!SongMark.None.isOnSpeaker("y"))
    }

    @Test fun aPausedSongStaysTheLitRowAndTheSpeakersOtherSongsLightNothingHere() {
        val paused = speakerSnapshot(media = media(MediaStatus.Paused, "y", "Song", 5, 100))
        val mark = songMarkOf(PlayRequest.None, SpeakerView.Online(paused))
        assertTrue(mark.isOnSpeaker("y"))
        assertTrue(!mark.isOnSpeaker("not in these results"))
    }

    /** R3B-03. Songs can be offered to a playlist only while the playlists are read; locked, the sheet does not open. */
    @Test fun nothingIsOfferedForAddingWhileThePlaylistsAreLocked() = runTest {
        val locked = music(FakeSpeaker(), MemoryPreferences(unreadablePlaylists = true, unreadableBackedUp = false))
        runCurrent()
        assertTrue(!locked.state.value.canAdd)
        locked.onAddToPlaylist(listOf(track("a")))
        runCurrent()
        assertIs<com.r1control.app.ui.music.Picker.Closed>(locked.state.value.picker)

        val readable = music(FakeSpeaker())
        runCurrent()
        assertTrue(readable.state.value.canAdd)
        readable.onAddToPlaylist(listOf(track("a")))
        runCurrent()
        assertIs<com.r1control.app.ui.music.Picker.Open>(readable.state.value.picker)
    }

    /** UX-08. An empty playlist's "Find songs" asks the search field for focus, once. */
    @Test fun findSongsAsksTheFieldForFocusUntilItHasIt() = runTest {
        val vm = music(FakeSpeaker())
        assertTrue(!vm.focusSearch.value)
        vm.askForSearch()
        assertTrue(vm.focusSearch.value)
        vm.onSearchFocused()
        assertTrue(!vm.focusSearch.value)
    }
}
