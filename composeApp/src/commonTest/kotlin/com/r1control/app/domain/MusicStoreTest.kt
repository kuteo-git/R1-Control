package com.r1control.app.domain

import com.r1control.app.domain.model.ListEdit
import com.r1control.app.domain.model.ListRef
import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.PlayOrder
import com.r1control.app.domain.model.PlayRequest
import com.r1control.app.domain.model.Playing
import com.r1control.app.domain.model.Playlists
import com.r1control.app.domain.model.PlaylistId
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.usecase.CommandOutcome
import com.r1control.app.domain.usecase.Created
import com.r1control.app.domain.usecase.MusicLists
import com.r1control.app.domain.usecase.MusicStore
import com.r1control.app.domain.usecase.PlaylistLookup
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.testing.made
import com.r1control.app.testing.FakeSpeaker
import com.r1control.app.testing.MemoryPreferences
import com.r1control.app.testing.media
import com.r1control.app.testing.speakerSnapshot
import com.r1control.app.testing.track
import com.r1control.app.testing.unreachable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MusicStoreTest {
    /** A speaker that answers a play or a next the way the R1 does: it goes to downloading. */
    private class Responsive : FakeSpeaker() {
        override suspend fun play(tracks: List<Track>, startIndex: Int) {
            super.play(tracks, startIndex)
            current = speakerSnapshot(media(MediaStatus.Downloading, tracks[startIndex].id, queue = tracks, downloadPercent = 0))
        }

        override suspend fun next() {
            super.next()
            current = speakerSnapshot(media(MediaStatus.Downloading, "after", queue = emptyList(), downloadPercent = 0))
        }
    }

    private class Rig(scope: CoroutineScope, val speaker: FakeSpeaker, val prefs: MemoryPreferences, seed: Int = 1) {
        val feed = SpeakerFeed(speaker, scope)
        var clock = 1_000L
        val store = MusicStore(speaker, prefs, feed, scope, Random(seed)) { clock++ }

        val lists: MusicLists get() = store.state.value
        val playlists get() = assertIs<Playlists.Readable>(lists.playlists)
    }

    private fun TestScope.rig(
        speaker: FakeSpeaker = FakeSpeaker(),
        prefs: MemoryPreferences = MemoryPreferences(),
    ) = Rig(backgroundScope, speaker, prefs).also { runCurrent() }

    private fun Rig.reading(status: MediaStatus, videoId: String, vararg queue: String) {
        speaker.current = speakerSnapshot(media(status, videoId, queue = queue.map { track(it) }))
        feed.refresh()
    }

    private fun songs(vararg ids: String) = ids.map { track(it) }
    private fun Rig.ids(ref: ListRef) = lists.songs(ref).tracks.map { it.id }

    // Storage.

    @Test fun theShelfIsLoadingUntilTheFirstReadAndThenReadable() = runTest {
        val rig = Rig(backgroundScope, FakeSpeaker(), MemoryPreferences())
        assertEquals(Playlists.Loading, rig.lists.playlists)
        runCurrent()
        assertEquals(Playlists.Readable(emptyList(), saveFailed = false, keptAside = false), rig.lists.playlists)
        assertTrue(rig.prefs.playlistWrites.isEmpty(), "the loaded value is not written back")
    }

    /** One bad read followed by one save would delete every playlist. */
    @Test fun anUnreadableStoredValueIsNeverOverwritten() = runTest {
        val rig = rig(prefs = MemoryPreferences(unreadablePlaylists = true))
        assertEquals(Playlists.Unreadable, rig.lists.playlists)
        assertEquals(Created.Refused, rig.store.create("Nhạc xưa", songs("a")))
        rig.store.edit(ListRef.Saved(PlaylistId("x")), ListEdit.Add(songs("b")))
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(Playlists.Unreadable, rig.lists.playlists, "nothing could be created from a value that was not read")
        assertTrue(rig.prefs.playlistWrites.isEmpty())
    }

    @Test fun anUnreadableValueThatWasCopiedAsideStartsAnEmptyEditableShelfThatSaysSo() = runTest {
        val rig = rig(prefs = MemoryPreferences(unreadablePlaylists = true, unreadableBackedUp = true))
        assertEquals(Playlists.Readable(emptyList(), saveFailed = false, keptAside = true), rig.lists.playlists)
        assertTrue(rig.prefs.playlistWrites.isEmpty(), "reading alone saves nothing")
        rig.store.made("Mới", songs("a"))
        runCurrent()
        assertEquals(1, rig.prefs.playlistWrites.size)
        assertTrue(rig.playlists.keptAside, "the banner stays for the run")
    }

    @Test fun aStorageThatThrowsOnReadIsTreatedAsUnreadable() = runTest {
        val prefs = object : MemoryPreferences() {
            override suspend fun playlists() = error("disk")
        }
        assertEquals(Playlists.Unreadable, rig(prefs = prefs).lists.playlists)
    }

    @Test fun editsAreShownAtOnceAndTheLatestIsSaved() = runTest {
        val rig = rig()
        val id = rig.store.made("A", songs("a", "b", "c", "d"))
        val ref = ListRef.Saved(id)
        rig.store.edit(ref, ListEdit.Move(0, 3))
        rig.store.edit(ref, ListEdit.Remove("b"))
        rig.store.edit(ref, ListEdit.Add(listOf(track("e"))))
        assertEquals(listOf("c", "d", "a", "e"), rig.ids(ref), "shown before anything was saved")
        assertTrue(rig.prefs.playlistWrites.isEmpty())
        runCurrent()
        assertEquals(listOf("c", "d", "a", "e"), rig.prefs.playlistWrites.last().single().songs.tracks.map { it.id })
        assertEquals(rig.playlists.all, rig.prefs.playlists)
    }

    @Test fun aFailedSaveIsSaidAndTheEditStaysUntilASaveWorks() = runTest {
        val rig = rig(prefs = MemoryPreferences().apply { failPlaylistWrites = true })
        val id = rig.store.made("A", songs("a", "b"))
        runCurrent()
        assertTrue(rig.playlists.saveFailed)
        assertEquals(listOf("a", "b"), rig.ids(ListRef.Saved(id)))
        rig.prefs.failPlaylistWrites = false
        rig.store.rename(id, "B")
        runCurrent()
        assertFalse(rig.playlists.saveFailed)
        assertEquals("B", rig.prefs.playlists.single().name)
    }

    @Test fun anEditThatChangesNothingSavesNothing() = runTest {
        val rig = rig()
        val id = rig.store.made("A", songs("a"))
        runCurrent()
        val writes = rig.prefs.playlistWrites.size
        val before = rig.lists
        rig.store.edit(ListRef.Saved(id), ListEdit.Remove("a"))
        rig.store.edit(ListRef.Saved(id), ListEdit.Add(songs("a")))
        rig.store.edit(ListRef.Saved(id), ListEdit.Move(0, 0))
        runCurrent()
        assertSame(before, rig.lists)
        assertEquals(writes, rig.prefs.playlistWrites.size)
    }

    // Playlists.

    @Test fun playlistsCreateNewestFirstRenameAndDelete() = runTest {
        val rig = rig()
        val first = rig.store.made("Một", songs("a"))
        val second = rig.store.made("Hai", emptyList())
        assertEquals(listOf(second, first), rig.playlists.all.map { it.id })
        assertTrue(first != second)
        rig.store.rename(first, "Một nữa")
        rig.store.rename(first, "")
        assertEquals("Một nữa", (rig.lists.playlist(first) as PlaylistLookup.Found).playlist.name)
        rig.store.delete(second)
        assertEquals(PlaylistLookup.Gone, rig.lists.playlist(second))
        runCurrent()
        assertEquals(listOf("Một nữa"), rig.prefs.playlists.map { it.name })
    }

    @Test fun aPlaylistPageAskedBeforeTheFirstReadWaitsInsteadOfBackingOut() = runTest {
        val rig = Rig(backgroundScope, FakeSpeaker(), MemoryPreferences())
        assertEquals(PlaylistLookup.Loading, rig.lists.playlist(PlaylistId("p")))
    }

    @Test fun editingResultsIsNotSavedAndAGoneListIsLeftAlone() = runTest {
        val rig = rig()
        val ref = rig.store.showResults(songs("a", "b", "c"))
        rig.store.edit(ref, ListEdit.Remove("b"))
        assertEquals(listOf("a", "c"), rig.ids(ref))
        rig.store.edit(ListRef.Results(99), ListEdit.Add(songs("z")))
        assertEquals(listOf(1), rig.lists.results.keys.toList())
        runCurrent()
        assertTrue(rig.prefs.playlistWrites.isEmpty())
    }

    // The speaker.

    @Test fun playRecordsTheWindowItSentAndMarksTheRequestOnTheWay() = runTest {
        val rig = rig(Responsive())
        val ref = rig.store.showResults(songs("a", "b", "c"))
        val outcome = rig.store.play(ref, 1)
        assertEquals(CommandOutcome.Done, outcome)
        assertEquals(listOf("play [a, b, c] from 1"), rig.speaker.calls)
        assertEquals(Playing.FromList(ref, PlayOrder.Listed, listOf("a", "b", "c")), rig.lists.playing)
        assertEquals(PlayRequest.None, rig.lists.request)
        assertEquals(ref, (rig.lists.playing as Playing.FromList).ref)
    }

    @Test fun aLongListIsSentInThirtyAndThePointerHoldsOnlyThatWindow() = runTest {
        val rig = rig()
        val ref = rig.store.showResults(List(45) { track("s$it") })
        rig.store.play(ref, 40)
        val pointer = assertIs<Playing.FromList>(rig.lists.playing)
        assertEquals(30, pointer.sentIds.size)
        assertEquals("s44", pointer.sentIds.last())
    }

    @Test fun aFailedPlayKeepsTheOldPointerAndNamesTheSongThatDidNotStart() = runTest {
        val rig = rig()
        val first = rig.store.showResults(songs("a", "b"))
        rig.store.play(first, 0)
        val before = rig.lists.playing
        rig.speaker.commandFailure = unreachable
        val outcome = rig.store.play(first, 1)
        assertIs<CommandOutcome.Failed>(outcome)
        assertEquals(before, rig.lists.playing, "the speaker still plays what it played")
        assertEquals(PlayRequest.Failed("b", unreachable), rig.lists.request)
    }

    @Test fun aLaterTapWinsOverAnEarlierOnesAnswer() = runTest {
        val speaker = object : FakeSpeaker() {
            override suspend fun play(tracks: List<Track>, startIndex: Int) {
                kotlinx.coroutines.delay(100)
                super.play(tracks, startIndex)
            }
        }
        val rig = rig(speaker)
        val ref = rig.store.showResults(songs("a", "b"))
        val firstTap = launch { rig.store.play(ref, 0) }
        runCurrent()
        assertEquals(PlayRequest.Starting("a"), rig.lists.request)
        val secondTap = launch { rig.store.play(ref, 1) }
        runCurrent()
        assertEquals(PlayRequest.Starting("b"), rig.lists.request)
        advanceTimeBy(1_000)
        firstTap.join(); secondTap.join()
        assertEquals(PlayRequest.None, rig.lists.request)
        assertEquals(listOf("play [a, b] from 0", "play [a, b] from 1"), speaker.calls, "sent in the order tapped")
    }

    @Test fun nextSendsNextWhenTheListIsUneditedAndPlayWhenItWasEdited() = runTest {
        val rig = rig()
        val id = rig.store.made("L", songs("a", "b", "c"))
        val ref = ListRef.Saved(id)
        rig.store.play(ref, 0)
        rig.speaker.calls.clear()

        rig.reading(MediaStatus.Playing, "a", "a", "b", "c")
        runCurrent()
        rig.store.next()
        assertEquals(listOf("next"), rig.speaker.calls)

        rig.speaker.calls.clear()
        rig.store.edit(ref, ListEdit.Remove("b"))
        rig.reading(MediaStatus.Playing, "a", "a", "b", "c")
        runCurrent()
        rig.store.next()
        // The edit was sent as what follows; the reading still shows the old queue, so Next re-sends, which is always right.
        assertEquals(listOf("queue [c]", "play [a, c] from 1"), rig.speaker.calls)
        assertEquals(listOf("a", "c"), assertIs<Playing.FromList>(rig.lists.playing).sentIds, "the window sent is what the speaker now holds")
    }

    @Test fun anEditIsSentAsWhatFollowsAndNeverInterruptsTheSongPlaying() = runTest {
        val rig = rig()
        val id = rig.store.made("L", songs("a", "b", "c"))
        rig.store.play(ListRef.Saved(id), 0)
        rig.reading(MediaStatus.Playing, "a", "a", "b", "c")
        runCurrent()
        rig.speaker.calls.clear()
        rig.store.edit(ListRef.Saved(id), ListEdit.Move(2, 1))
        rig.store.edit(ListRef.Saved(id), ListEdit.Remove("b"))
        runCurrent()
        assertEquals(listOf("queue [c]"), rig.speaker.calls, "what the two edits add up to, once; never a play, next or stop")
    }

    @Test fun anEditThatLeavesWhatFollowsAsSentSendsNothing() = runTest {
        val rig = rig()
        val id = rig.store.made("L", songs("a", "b", "c"))
        rig.store.play(ListRef.Saved(id), 0)
        rig.reading(MediaStatus.Playing, "b", "a", "b", "c")
        runCurrent()
        rig.speaker.calls.clear()
        rig.store.edit(ListRef.Saved(id), ListEdit.Remove("a"))
        runCurrent()
        assertTrue(rig.speaker.calls.isEmpty(), "a played song removed changes nothing ahead: ${rig.speaker.calls}")
    }

    /** The reading shows an edit only after the song ends; an edit undone meanwhile must still be sent. */
    @Test fun anEditUndoneBeforeTheSongEndsIsSentBack() = runTest {
        val rig = rig()
        val id = rig.store.made("L", songs("a", "b", "c"))
        rig.store.play(ListRef.Saved(id), 0)
        rig.reading(MediaStatus.Playing, "a", "a", "b", "c")
        runCurrent()
        rig.speaker.calls.clear()
        rig.store.edit(ListRef.Saved(id), ListEdit.Remove("b"))
        runCurrent()
        rig.store.edit(ListRef.Saved(id), ListEdit.Add(songs("b")))
        runCurrent()
        assertEquals(listOf("queue [c]", "queue [c, b]"), rig.speaker.calls)
    }

    @Test fun nextOverNothingSendsNothing() = runTest {
        val rig = rig()
        assertEquals(CommandOutcome.Done, rig.store.next())
        rig.reading(MediaStatus.Downloading, "a", "a", "b")
        runCurrent()
        rig.store.next()
        assertTrue(rig.speaker.calls.isEmpty())
    }

    @Test fun aFailedNextIsReportedAndLeavesThePointerAlone() = runTest {
        val rig = rig()
        val id = rig.store.made("L", songs("a", "b", "c"))
        rig.store.play(ListRef.Saved(id), 0)
        rig.store.edit(ListRef.Saved(id), ListEdit.Remove("b"))
        rig.reading(MediaStatus.Playing, "a", "a", "b", "c")
        runCurrent()
        val before = rig.lists.playing
        rig.speaker.commandFailure = unreachable
        assertIs<CommandOutcome.Failed>(rig.store.next())
        assertEquals(before, rig.lists.playing)
    }

    @Test fun aDoubleTapOnNextSkipsOneSong() = runTest {
        val rig = rig(Responsive())
        val id = rig.store.made("L", songs("a", "b", "c"))
        rig.store.play(ListRef.Saved(id), 0)
        rig.store.edit(ListRef.Saved(id), ListEdit.Remove("b"))
        rig.speaker.calls.clear()
        rig.reading(MediaStatus.Playing, "a", "a", "b", "c")
        runCurrent()
        val one = launch { rig.store.next() }
        val two = launch { rig.store.next() }
        advanceTimeBy(5_000)
        one.join(); two.join()
        assertEquals(listOf("play [a, c] from 1"), rig.speaker.calls, "the second press read the speaker downloading")
    }

    @Test fun deletingThePlayingPlaylistHandsNextToTheSpeaker() = runTest {
        val rig = rig()
        val id = rig.store.made("L", songs("a", "b", "c"))
        rig.store.play(ListRef.Saved(id), 0)
        rig.store.delete(id)
        assertEquals(Playing.None, rig.lists.playing)
        rig.reading(MediaStatus.Playing, "a", "a", "b", "c")
        runCurrent()
        rig.speaker.calls.clear()
        rig.store.next()
        assertEquals(listOf("next"), rig.speaker.calls)
    }

    @Test fun deletingAnotherPlaylistLeavesThePointer() = runTest {
        val rig = rig()
        val playing = rig.store.made("L", songs("a", "b"))
        val other = rig.store.made("M", songs("x"))
        rig.store.play(ListRef.Saved(playing), 0)
        rig.store.delete(other)
        assertEquals(ListRef.Saved(playing), (rig.lists.playing as Playing.FromList).ref)
    }

    @Test fun aNewSearchKeepsTheResultsStillPlayingAndNoOthers() = runTest {
        val rig = rig()
        val first = rig.store.showResults(songs("a", "b"))
        val second = rig.store.showResults(songs("c", "d"))
        assertEquals(setOf(2), rig.lists.results.keys, "nothing plays, so only the one on screen is kept")
        rig.store.play(second, 0)
        val third = rig.store.showResults(songs("e", "f"))
        assertEquals(setOf(2, 3), rig.lists.results.keys)
        assertEquals(listOf("c", "d"), rig.ids(second), "the one playing can still be read, and Next follows it")
        assertEquals(listOf("e", "f"), rig.ids(third))
        val fourth = rig.store.showResults(songs("g"))
        assertEquals(setOf(2, 4), rig.lists.results.keys)
        assertEquals(emptyList(), rig.ids(first))
        assertEquals(4, fourth.serial)
    }

    @Test fun aNewSearchLetsGoOfAFailedMarkThatNamedTheOldList() = runTest {
        val rig = rig(FakeSpeaker().apply { commandFailure = unreachable })
        val ref = rig.store.showResults(songs("a", "b"))
        rig.store.play(ref, 0)
        assertIs<PlayRequest.Failed>(rig.lists.request)
        rig.store.showResults(songs("c"))
        assertEquals(PlayRequest.None, rig.lists.request)
    }

    @Test fun shuffleIsPinnedBySeedAndLeavesThePlaylistOrderAlone() = runTest {
        val ids = (1..8).map { "s$it" }
        suspend fun shuffledWith(seed: Int): List<String> {
            val rig = Rig(backgroundScope, FakeSpeaker(), MemoryPreferences(), seed)
            runCurrent()
            val id = rig.store.made("L", ids.map { track(it) })
            rig.store.shuffle(ListRef.Saved(id))
            val sent = assertIs<Playing.FromList>(rig.lists.playing).sentIds
            assertEquals(ids, rig.ids(ListRef.Saved(id)), "the saved order is untouched")
            return sent
        }
        val a = shuffledWith(5)
        assertEquals(a, shuffledWith(5), "the same seed gives the same order")
        assertEquals(ids.sorted(), a.sorted())
        assertTrue(a != ids, "an order of eight songs that is the order they had is not a shuffle")
    }

    @Test fun playFromTakesAPositionOfTheOrderItIsPlayingInAndKeepsTheShuffle() = runTest {
        val rig = rig()
        val id = rig.store.made("L", songs("a", "b", "c", "d"))
        rig.store.shuffle(ListRef.Saved(id))
        val order = assertIs<PlayOrder.Shuffled>(assertIs<Playing.FromList>(rig.lists.playing).order)
        rig.speaker.calls.clear()
        rig.store.playFrom(ListRef.Saved(id), order, 2)
        assertEquals("play ${order.ids} from 2", rig.speaker.calls.single())
        assertEquals(order, assertIs<Playing.FromList>(rig.lists.playing).order, "the same shuffle, not a new draw")
    }

    @Test fun shuffleStartsFromTheFirstOfTheShuffledOrderAndNextFollowsIt() = runTest {
        val rig = rig()
        val id = rig.store.made("L", songs("a", "b", "c", "d"))
        rig.store.shuffle(ListRef.Saved(id))
        val pointer = assertIs<Playing.FromList>(rig.lists.playing)
        val order = assertIs<PlayOrder.Shuffled>(pointer.order).ids
        assertEquals("play $order from 0", rig.speaker.calls.single())
        rig.speaker.calls.clear()
        rig.reading(MediaStatus.Playing, order[1], *order.toTypedArray())
        runCurrent()
        rig.store.next()
        assertEquals(listOf("next"), rig.speaker.calls)
    }

    @Test fun shufflingNothingSendsNothing() = runTest {
        val rig = rig()
        val id = rig.store.made("L", emptyList())
        assertEquals(CommandOutcome.Done, rig.store.shuffle(ListRef.Saved(id)))
        assertTrue(rig.speaker.calls.isEmpty())
    }

    @Test fun aSongRemovedFromAShuffledPlaylistDropsOutOfTheNextWindowButAddedOnesGoLast() = runTest {
        val rig = rig()
        val id = rig.store.made("L", songs("a", "b", "c", "d"))
        val ref = ListRef.Saved(id)
        rig.store.shuffle(ref)
        val order = assertIs<PlayOrder.Shuffled>(assertIs<Playing.FromList>(rig.lists.playing).order).ids
        rig.store.edit(ref, ListEdit.Remove(order[1]))
        rig.store.edit(ref, ListEdit.Add(listOf(track("z"))))
        rig.speaker.calls.clear()
        rig.reading(MediaStatus.Playing, order[0], *order.toTypedArray())
        runCurrent()
        rig.store.next()
        val expected = listOf(order[0]) + order.drop(2) + "z"
        assertEquals(listOf("play $expected from 1"), rig.speaker.calls)
    }

    @Test fun speakerChangedForgetsWhatWasSent() = runTest {
        val rig = rig(FakeSpeaker().apply { commandFailure = null })
        val ref = rig.store.showResults(songs("a", "b"))
        rig.store.play(ref, 0)
        rig.store.speakerChanged()
        assertEquals(Playing.None, rig.lists.playing)
        assertEquals(PlayRequest.None, rig.lists.request)
    }

    @Test fun canNextFollowsTheListAndTheReading() = runTest {
        val rig = rig()
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch { rig.store.canNext.collect { seen += it } }
        runCurrent()
        assertFalse(rig.store.canNext.value, "no reading yet")

        val id = rig.store.made("L", songs("a", "b"))
        rig.store.play(ListRef.Saved(id), 0)
        rig.reading(MediaStatus.Playing, "a", "a", "b")
        runCurrent()
        assertTrue(rig.store.canNext.value)

        rig.store.edit(ListRef.Saved(id), ListEdit.Remove("b"))
        runCurrent()
        assertFalse(rig.store.canNext.value, "the list now ends at this song")

        rig.reading(MediaStatus.Downloading, "a", "a", "b")
        rig.store.edit(ListRef.Saved(id), ListEdit.Add(songs("c")))
        runCurrent()
        assertFalse(rig.store.canNext.value, "nothing to skip while a song is still downloading")
    }

    /** R3B-06. One rule for a playlist's name, whichever path made or renamed it: blank is refused, the rest is cleaned. */
    @Test fun R3B_06_aBlankNameMakesNoPlaylistAndRenamesNothing() = runTest {
        val rig = rig()
        assertEquals(Created.Refused, rig.store.create("   ", songs("a")))
        runCurrent()
        assertTrue(rig.playlists.all.isEmpty(), "a blank name made a playlist: ${rig.playlists.all}")

        rig.store.create("  Evening   songs ", songs("a"))
        runCurrent()
        val made = rig.playlists.all.single()
        assertEquals("Evening songs", made.name, "the store cleans the name it is given")

        rig.store.rename(made.id, " \t ")
        rig.store.rename(made.id, "  Night  ")
        runCurrent()
        assertEquals(listOf("Night"), rig.playlists.all.map { it.name })
    }

    /** R3B-05, on Next's own send: a list deleted while a re-send is on the wire is not named by the pointer it leaves. */
    @Test fun aPlaylistDeletedWhileNextResendsIsLetGo() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val speaker = object : FakeSpeaker() {
            var holdPlays = false
            override suspend fun play(tracks: List<Track>, startIndex: Int) {
                if (holdPlays) gate.await()
                super.play(tracks, startIndex)
            }
        }
        val rig = rig(speaker)
        val id = rig.store.made("L", songs("a", "b", "c"))
        rig.store.play(ListRef.Saved(id), 0)
        rig.store.edit(ListRef.Saved(id), ListEdit.Remove("b"))
        rig.reading(MediaStatus.Playing, "a", "a", "b", "c")
        runCurrent()
        speaker.holdPlays = true
        val press = launch { rig.store.next() }
        runCurrent()
        rig.store.delete(id)
        gate.complete(Unit)
        press.join()
        assertEquals(Playing.None, rig.lists.playing)
    }
}
