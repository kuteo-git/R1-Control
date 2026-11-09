package com.r1control.app.qa.round3

import com.r1control.app.domain.model.ListRef
import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.Playlist
import com.r1control.app.domain.model.PlaylistId
import com.r1control.app.domain.model.Playlists
import com.r1control.app.domain.model.SongList
import com.r1control.app.domain.model.StoredPlaylists
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.usecase.MusicStore
import com.r1control.app.domain.usecase.SearchMusic
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.testing.made
import com.r1control.app.testing.MemoryPreferences
import com.r1control.app.testing.track
import com.r1control.app.ui.Problem
import com.r1control.app.ui.music.MusicViewModel
import com.r1control.app.ui.music.Picker
import com.r1control.app.ui.music.PlaylistPageState
import com.r1control.app.ui.music.PlaylistViewModel
import com.r1control.app.ui.music.ShelfState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The shelf, the All playlists page, a playlist's page and the "add to playlist" sheet, as the person sees them. */
class Round3PlaylistsTest {
    private class Tab(val speaker: RecordingSpeaker, val prefs: MemoryPreferences, val feed: SpeakerFeed, val store: MusicStore, val music: MusicViewModel)

    private fun TestScope.tab(prefs: MemoryPreferences = MemoryPreferences(), results: List<Track> = songs("a", "b", "c")): Tab {
        val speaker = RecordingSpeaker().apply { searchAnswers = { results } }
        val rig = StoreRig(backgroundScope, speaker, prefs)
        val music = MusicViewModel(SearchMusic(speaker, prefs) { 7 }, prefs, rig.store, rig.feed, backgroundScope)
        runCurrent()
        return Tab(speaker, prefs, rig.feed, rig.store, music)
    }

    private fun TestScope.page(tab: Tab, id: PlaylistId) =
        PlaylistViewModel(id, tab.store, tab.feed, backgroundScope).also { runCurrent() }

    private fun TestScope.searchShows(tab: Tab) {
        tab.music.onQuery("q")
        tab.music.search()
        advanceTimeBy(1_000)
    }

    private fun stored(n: Int) = List(n) { Playlist(PlaylistId("p$it"), "List $it", SongList.of(songs("s$it")), 100L - it) }

    private fun shelf(tab: Tab) = assertIs<ShelfState.Ready>(tab.music.state.value.shelf).shelf

    /** R3-24. Exactly five: five cards and no More. */
    @Test fun exactlyFivePlaylistsShowFiveCardsAndNoMore() = runTest {
        val tab = tab(MemoryPreferences(playlists = stored(5)))
        assertEquals(listOf("p0", "p1", "p2", "p3", "p4"), shelf(tab).cards.map { it.id.value })
        assertTrue(!shelf(tab).hasMore)
    }

    /** R3-25. Six: the five newest, and More. The sixth is reachable only from All playlists. */
    @Test fun sixPlaylistsShowTheFiveNewestAndMore() = runTest {
        val tab = tab(MemoryPreferences(playlists = stored(6)))
        assertEquals(5, shelf(tab).cards.size)
        assertTrue(shelf(tab).hasMore)
        tab.store.made("Newest", emptyList())
        runCurrent()
        assertEquals("Newest", shelf(tab).cards.first().name, "a new playlist is the first card")
        assertTrue("p4" !in shelf(tab).cards.map { it.id.value }, "the oldest of the five gives way")
    }

    /** R3-08. The same song added twice: once in the list, and the second add writes nothing. */
    @Test fun theSameSongAddedTwiceIsInThePlaylistOnceAndSavedOnce() = runTest {
        val tab = tab()
        searchShows(tab)
        val id = tab.store.made("Mine", emptyList())
        runCurrent()
        tab.music.onAddToPlaylist(listOf(track("a")))
        tab.music.onPick(id)
        runCurrent()
        val writes = tab.prefs.playlistWrites.size
        tab.music.onAddToPlaylist(listOf(track("a")))
        runCurrent()
        val row = assertIs<Picker.Open>(tab.music.state.value.picker).playlists.single { it.id == id }
        assertTrue(row.hasAll, "the sheet says the song is already there")
        tab.music.onPick(id)
        runCurrent()
        assertEquals(listOf("a"), tab.store.state.value.songs(ListRef.Saved(id)).tracks.map { it.id })
        assertEquals(writes, tab.prefs.playlistWrites.size, "a no-op add saves nothing")
        assertIs<Picker.Closed>(tab.music.state.value.picker)
    }

    /** R3-09. "Add all" puts the edited results in a new playlist, the X'd one left out. */
    @Test fun addAllAfterAnXSavesTheResultsAsTheyAreShown() = runTest {
        val tab = tab()
        searchShows(tab)
        tab.music.onRemove(track("b"))
        runCurrent()
        tab.music.onAddToPlaylist(tab.music.state.value.results.tracks)
        tab.music.onPickNew("  Evening   songs ")
        runCurrent()
        val made = assertIs<Playlists.Readable>(tab.store.state.value.playlists).all.single()
        assertEquals("Evening songs", made.name)
        assertEquals(listOf("a", "c"), made.songs.tracks.map { it.id })
        assertEquals(listOf(made), tab.prefs.playlists, "saved on the phone")
    }

    /** R3-30. A rename with an empty name keeps the old name and saves nothing. */
    @Test fun aRenameWithAnEmptyNameKeepsTheOldName() = runTest {
        val tab = tab()
        val id = tab.store.made("Old", songs("a"))
        runCurrent()
        val writes = tab.prefs.playlistWrites.size
        val page = page(tab, id)
        page.onRenameAsked()
        page.onRename("   ")
        runCurrent()
        val shown = assertIs<PlaylistPageState.Showing>(page.state.value)
        assertEquals("Old", shown.name)
        assertTrue(shown.hasName)
        assertEquals(writes, tab.prefs.playlistWrites.size)
    }

    /** R3-33. Process death: the page is restored before the phone's storage has answered; it waits, then shows, and never backs out. */
    @Test fun aPlaylistPageRestoredBeforeTheFirstReadWaitsAndThenShows() = runTest {
        val answer = CompletableDeferred<Unit>()
        val prefs = object : MemoryPreferences(playlists = stored(2)) {
            override suspend fun playlists(): StoredPlaylists {
                answer.await()
                return super.playlists()
            }
        }
        val tab = tab(prefs)
        val page = page(tab, PlaylistId("p1"))
        val seen = mutableListOf<PlaylistPageState>()
        backgroundScope.launch { page.state.collect { seen += it } }
        runCurrent()
        assertEquals(PlaylistPageState.Loading, page.state.value)
        answer.complete(Unit)
        runCurrent()
        assertEquals("List 1", assertIs<PlaylistPageState.Showing>(page.state.value).name)
        assertTrue(seen.none { it == PlaylistPageState.Gone }, "the page never said Gone, so it never backed out: $seen")
    }

    /** R3-31. Deleting the playing playlist from its page: the page goes, and Next is the speaker's own. */
    @Test fun deletingThePlayingPlaylistBacksThePageOutAndHandsNextToTheSpeaker() = runTest {
        val tab = tab()
        val id = tab.store.made("L", songs("a", "b", "c"))
        val page = page(tab, id)
        page.onPlay(0)
        tab.speaker.current = com.r1control.app.testing.speakerSnapshot(
            com.r1control.app.testing.media(MediaStatus.Playing, "a", queue = songs("a", "b", "c")),
        )
        tab.feed.refresh()
        runCurrent()
        assertTrue(assertIs<PlaylistPageState.Showing>(page.state.value).playingHere)
        page.onDeleteAsked()
        page.onDelete()
        runCurrent()
        assertEquals(PlaylistPageState.Gone, page.state.value)
        tab.speaker.current = com.r1control.app.testing.speakerSnapshot(
            com.r1control.app.testing.media(MediaStatus.Playing, "a", queue = songs("a", "b", "c")),
        )
        tab.feed.refresh()
        runCurrent()
        tab.speaker.calls.clear()
        tab.store.next()
        assertEquals(listOf("next"), tab.speaker.calls)
    }

    /** R3-29. A refused Shuffle is said on the page; the saved order is untouched. */
    @Test fun aRefusedShuffleIsSaidOnThePage() = runTest {
        val tab = tab()
        val id = tab.store.made("L", songs("a", "b", "c"))
        val page = page(tab, id)
        tab.speaker.commandFailure = com.r1control.app.testing.unreachable
        page.onShuffle()
        runCurrent()
        val shown = assertIs<PlaylistPageState.Showing>(page.state.value)
        assertIs<Problem.Failed>(shown.problem)
        assertTrue(!shown.playingHere)
        assertEquals(listOf("a", "b", "c"), shown.songs.tracks.map { it.id })
    }

    /** R3-32. A playlist's page says "Playing from this playlist" only while the speaker plays from it. */
    @Test fun R3B_02_aPlaylistPageStillSaysPlayingFromHereAfterTheSpeakerMovedToAVoiceRequest() = runTest {
        val tab = tab()
        val id = tab.store.made("L", songs("a", "b"))
        val page = page(tab, id)
        page.onPlay(0)
        runCurrent()
        tab.speaker.current = com.r1control.app.testing.speakerSnapshot(
            com.r1control.app.testing.media(MediaStatus.Playing, "voice1", queue = songs("voice1", "voice2")),
        )
        tab.feed.refresh()
        runCurrent()
        assertTrue(
            !assertIs<PlaylistPageState.Showing>(page.state.value).playingHere,
            "the speaker plays a song asked for by voice, yet the page says it plays from this playlist",
        )
    }

    /**
     * R3-37. The stored playlists could not be read or kept aside: nothing can be saved, yet the sheet
     * still offers "New playlist", and picking it closes the sheet with the songs in no playlist and no word said.
     */
    @Test fun R3B_03_addingToANewPlaylistWhileThePlaylistsAreUnreadableLosesTheSongsSilently() = runTest {
        val tab = tab(MemoryPreferences(unreadablePlaylists = true, unreadableBackedUp = false))
        assertEquals(ShelfState.Unreadable, tab.music.state.value.shelf)
        searchShows(tab)
        tab.music.onAddToPlaylist(listOf(track("a")))
        runCurrent()
        val offered = tab.music.state.value.picker is Picker.Open
        if (offered) {
            tab.music.onPickNew("Mine")
            runCurrent()
            val saved = (tab.store.state.value.playlists as? Playlists.Readable)?.all.orEmpty().any { it.name == "Mine" }
            val stillAsking = tab.music.state.value.picker is Picker.Open
            val told = tab.music.state.value.problem is Problem.Failed
            assertTrue(saved || stillAsking || told, "the sheet took the name and closed; the song is in no playlist and nothing says why")
        }
        assertTrue(tab.prefs.playlistWrites.isEmpty(), "and nothing was written over the unreadable value")
    }

    /** R3-39. A save that fails is said under the shelf, and the change is still shown. */
    @Test fun aFailedSaveIsSaidUnderTheShelf() = runTest {
        val tab = tab(MemoryPreferences().apply { failPlaylistWrites = true })
        tab.store.made("Mine", songs("a"))
        runCurrent()
        val ready = assertIs<ShelfState.Ready>(tab.music.state.value.shelf)
        assertTrue(ready.saveFailed)
        assertEquals(listOf("Mine"), ready.shelf.cards.map { it.name })
    }

    /** R3-35. A playlist that could not be read but was kept aside: an empty shelf that says so, and it can be used. */
    @Test fun keptAsidePlaylistsStartAnEmptyShelfThatSaysSoAndCanBeUsed() = runTest {
        val tab = tab(MemoryPreferences(unreadablePlaylists = true, unreadableBackedUp = true))
        val ready = assertIs<ShelfState.Ready>(tab.music.state.value.shelf)
        assertTrue(ready.keptAside)
        assertTrue(ready.shelf.isEmpty)
        assertTrue(tab.prefs.playlistWrites.isEmpty(), "the empty start is not saved over the old value")
        tab.store.made("Fresh", songs("a"))
        runCurrent()
        assertEquals(listOf("Fresh"), tab.prefs.playlistWrites.last().map { it.name })
    }

    /** R3-34. A new search replaces the results on screen; the results still playing stay with the store. */
    @Test fun aNewSearchKeepsTheResultsStillPlayingEditable() = runTest {
        val tab = tab()
        searchShows(tab)
        tab.music.play(0)
        runCurrent()
        val playing = (tab.store.state.value.playing as com.r1control.app.domain.model.Playing.FromList).ref
        tab.speaker.searchAnswers = { songs("x", "y") }
        searchShows(tab)
        assertEquals(listOf("x", "y"), tab.music.state.value.results.tracks.map { it.id })
        assertEquals(listOf("a", "b", "c"), tab.store.state.value.songs(playing).tracks.map { it.id }, "the list still playing is kept")
    }
}
