package com.r1control.app.qa.round3

import com.r1control.app.domain.model.ListEdit
import com.r1control.app.domain.model.ListRef
import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.usecase.MusicStore
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.testing.made
import com.r1control.app.testing.FakeSpeaker
import com.r1control.app.testing.MemoryPreferences
import com.r1control.app.testing.media
import com.r1control.app.testing.speakerSnapshot
import com.r1control.app.testing.track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A play the speaker was asked for: the ids sent and the song it was told to start on. */
internal data class Sent(val ids: List<String>, val startId: String)

/**
 * A speaker that records what it was told to play and, when [answers] is on,
 * moves to downloading the way the R1 does after a play or a next. [gate], when
 * set, holds a play on the wire until the test completes it.
 */
internal class RecordingSpeaker(private val answers: Boolean = false) : FakeSpeaker() {
    val plays = mutableListOf<Sent>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun play(tracks: List<Track>, startIndex: Int) {
        gate?.await()
        super.play(tracks, startIndex)
        plays += Sent(tracks.map { it.id }, tracks[startIndex].id)
        if (answers) current = speakerSnapshot(media(MediaStatus.Downloading, tracks[startIndex].id, queue = tracks, downloadPercent = 0))
    }

    override suspend fun next() {
        super.next()
        if (answers) current = speakerSnapshot(media(MediaStatus.Downloading, "after", queue = emptyList(), downloadPercent = 0))
    }
}

internal class StoreRig(scope: CoroutineScope, val speaker: RecordingSpeaker, val prefs: MemoryPreferences = MemoryPreferences()) {
    val feed = SpeakerFeed(speaker, scope)
    private var clock = 1_000L
    val store = MusicStore(speaker, prefs, feed, scope, Random(7)) { clock++ }

    init {
        scope.launch { store.canNext.collect {} }
    }

    /** The speaker now reports [status] on [videoId] with [queue] as its own queue. */
    fun reading(status: MediaStatus, videoId: String, queue: List<String>) {
        speaker.current = speakerSnapshot(media(status, videoId, queue = queue.map { track(it) }))
        feed.refresh()
    }
}

internal fun songs(vararg ids: String) = ids.map { track(it) }

/**
 * The owner's Next: an edit applies from the next song, the song playing is
 * never interrupted, and Next is drawn whenever a next song exists.
 */
class Round3NextTest {
    private fun TestScope.rig(answers: Boolean = false) = StoreRig(backgroundScope, RecordingSpeaker(answers)).also { runCurrent() }

    /** R3-13. A song added after the press, then the playing one X'd: the added song is still next. */
    @Test fun R3B_01_xingThePlayingSongHidesNextAlthoughASongAddedAfterThePressIsStillToCome() = runTest {
        val rig = rig()
        val ref = ListRef.Saved(rig.store.made("L", songs("a", "b")))
        rig.store.play(ref, 1)
        rig.reading(MediaStatus.Playing, "b", listOf("a", "b"))
        rig.store.edit(ref, ListEdit.Add(songs("c")))
        rig.store.edit(ref, ListEdit.Remove("b"))
        runCurrent()

        assertTrue(rig.store.canNext.value, "c is after the X'd song, but Next is not drawn")
        rig.speaker.plays.clear()
        rig.store.next()
        assertEquals("c", rig.speaker.plays.singleOrNull()?.startId, "Next should start the song after the removed one: ${rig.speaker.calls}")
    }

    /** R3-14. A list of 40: the speaker reached the window's last song, which is then X'd; the list goes on at s30. */
    @Test fun R3B_01_xingTheLastSongOfTheThirtySongWindowHidesNextAlthoughTheListGoesOn() = runTest {
        val rig = rig()
        val all = (0 until 40).map { "s$it" }
        val ref = ListRef.Saved(rig.store.made("Long", all.map { track(it) }))
        rig.store.play(ref, 0)
        assertEquals(all.take(30), rig.speaker.plays.single().ids, "set up: the first window is s0..s29")
        rig.reading(MediaStatus.Playing, "s29", all.take(30))
        rig.store.edit(ref, ListEdit.Remove("s29"))
        runCurrent()

        assertTrue(rig.store.canNext.value, "ten songs are still to come, but Next is not drawn")
        rig.speaker.plays.clear()
        rig.store.next()
        assertEquals("s30", rig.speaker.plays.singleOrNull()?.startId, "Next should send the window from s30: ${rig.speaker.calls}")
    }

    /** R3-23. The pointer must name a list that exists; a delete while the play is on the wire leaves it naming a gone one. */
    @Test fun R3B_05_aPlaylistDeletedWhileItsPlayIsOnTheWayLeavesTheCardsWithoutNext() = runTest {
        val rig = rig()
        val id = rig.store.made("L", songs("a", "b", "c"))
        val gate = CompletableDeferred<Unit>()
        rig.speaker.gate = gate
        val tap = launch { rig.store.play(ListRef.Saved(id), 0) }
        runCurrent()
        rig.store.delete(id)
        gate.complete(Unit)
        tap.join()
        rig.reading(MediaStatus.Playing, "a", listOf("a", "b", "c"))
        runCurrent()

        assertTrue(rig.store.canNext.value, "the speaker's own queue has b next, but Next is not drawn")
        rig.speaker.calls.clear()
        rig.store.next()
        assertEquals(listOf("next"), rig.speaker.calls, "a deleted playlist hands Next to the speaker")
    }

    /** R3-23, the same root cause on the Music tab: a new search lands while the tap's play is still on its way. */
    @Test fun R3B_05_aSearchThatLandsWhileATapsPlayIsOnTheWayLeavesTheCardsWithoutNext() = runTest {
        val rig = rig()
        val first = rig.store.showResults(songs("a", "b", "c"))
        val gate = CompletableDeferred<Unit>()
        rig.speaker.gate = gate
        val tap = launch { rig.store.play(first, 0) }
        runCurrent()
        rig.store.showResults(songs("x", "y"))
        gate.complete(Unit)
        tap.join()
        rig.reading(MediaStatus.Playing, "a", listOf("a", "b", "c"))
        runCurrent()

        assertTrue(rig.store.canNext.value, "the speaker plays a, b, c and b is next, but Next is not drawn")
    }

    /** R3-18. A song the phone never sent (asked for by voice) has the speaker's own Next, whatever the phone's list says. */
    @Test fun nextAfterAVoiceRequestIsTheSpeakersOwn() = runTest {
        val rig = rig()
        val ref = ListRef.Saved(rig.store.made("L", songs("a", "b", "c")))
        rig.store.play(ref, 0)
        rig.store.edit(ref, ListEdit.Remove("b"))
        rig.reading(MediaStatus.Playing, "v1", listOf("v1", "v2"))
        runCurrent()
        assertTrue(rig.store.canNext.value)
        rig.speaker.calls.clear()
        rig.store.next()
        assertEquals(listOf("next"), rig.speaker.calls, "the edited list must not take over a voice request")

        rig.reading(MediaStatus.Playing, "v2", listOf("v1", "v2"))
        runCurrent()
        assertTrue(!rig.store.canNext.value, "the voice queue's last song has no next")
    }

    /** R3-17. A double tap on an unedited list: the second press reads the speaker downloading and sends nothing. */
    @Test fun aDoubleTapOnAnUneditedListSendsOneNext() = runTest {
        val rig = rig(answers = true)
        val ref = ListRef.Saved(rig.store.made("L", songs("a", "b", "c")))
        rig.store.play(ref, 0)
        rig.reading(MediaStatus.Playing, "a", listOf("a", "b", "c"))
        runCurrent()
        rig.speaker.calls.clear()
        val one = launch { rig.store.next() }
        val two = launch { rig.store.next() }
        advanceTimeBy(3_000)
        one.join(); two.join()
        assertEquals(listOf("next"), rig.speaker.calls)
    }

    /** R3-19. Shuffle, then X a later song, then drag the playlist: Next follows the shuffled order without the X'd song. */
    @Test fun shuffleThenXThenDragFollowsTheShuffledOrderWithoutTheRemovedSong() = runTest {
        val rig = rig()
        val ids = listOf("a", "b", "c", "d", "e", "f")
        val ref = ListRef.Saved(rig.store.made("L", ids.map { track(it) }))
        rig.store.shuffle(ref)
        val order = rig.speaker.plays.single().ids
        assertEquals(ids.toSet(), order.toSet(), "set up: the whole playlist was sent")
        assertEquals(order.first(), rig.speaker.plays.single().startId, "shuffle starts from the first of its order")
        assertEquals(ids, rig.store.state.value.songs(ref).tracks.map { it.id }, "shuffle never rewrites the saved order")

        val removed = order[2]
        rig.store.edit(ref, ListEdit.Remove(removed))
        rig.store.edit(ref, ListEdit.Move(0, 4))
        rig.reading(MediaStatus.Playing, order[0], order)
        runCurrent()
        rig.speaker.plays.clear()
        rig.store.next()

        val sent = rig.speaker.plays.single()
        assertEquals(order[1], sent.startId, "the next song is the shuffled order's second")
        assertEquals(order - removed, sent.ids, "the shuffled order, the X'd song gone, the drag not applied to it")
    }

    /** R3-15. A list longer than 30, a song from beyond the window dragged right after the playing one. */
    @Test fun aSongDraggedInFromBeyondTheWindowIsWhatNextPlays() = runTest {
        val rig = rig()
        val all = (0 until 40).map { "s$it" }
        val ref = ListRef.Saved(rig.store.made("Long", all.map { track(it) }))
        rig.store.play(ref, 0)
        rig.reading(MediaStatus.Playing, "s0", all.take(30))
        rig.store.edit(ref, ListEdit.Move(35, 1))
        runCurrent()
        rig.speaker.plays.clear()
        rig.store.next()

        val sent = rig.speaker.plays.single()
        assertEquals("s35", sent.startId)
        assertTrue(sent.ids.size <= 30, "the R1's cap: ${sent.ids.size}")
        assertTrue(rig.speaker.calls.none { it == "next" })
    }

    /** R3-07. Nothing reaches the speaker on an edit; an edit before the playing song keeps the speaker's own next. */
    @Test fun anEditBeforeThePlayingSongSendsNothingAndNextIsTheSpeakers() = runTest {
        val rig = rig()
        val ref = ListRef.Saved(rig.store.made("L", songs("a", "b", "c", "d")))
        rig.store.play(ref, 0)
        rig.reading(MediaStatus.Playing, "c", listOf("a", "b", "c", "d"))
        rig.speaker.calls.clear()
        rig.store.edit(ref, ListEdit.Remove("a"))
        rig.store.edit(ref, ListEdit.Move(0, 0))
        runCurrent()
        assertTrue(rig.speaker.calls.isEmpty(), "an edit is never sent: ${rig.speaker.calls}")
        rig.store.next()
        assertEquals(listOf("next"), rig.speaker.calls)
    }

    /** R3-12. The playing song X'd while the speaker's queue already goes the right way: the speaker's own Next. */
    @Test fun xingThePlayingSongUsesTheSpeakersNextWhenItAlreadyGoesTheRightWay() = runTest {
        val rig = rig()
        val ref = ListRef.Saved(rig.store.made("L", songs("a", "b", "c")))
        rig.store.play(ref, 0)
        rig.reading(MediaStatus.Playing, "a", listOf("a", "b", "c"))
        rig.store.edit(ref, ListEdit.Remove("a"))
        runCurrent()
        rig.speaker.calls.clear()
        rig.store.next()
        assertEquals(listOf("next"), rig.speaker.calls)
    }

    /** R3-16. The last song of the list: Next is not drawn and a press sends nothing. */
    @Test fun theLastSongOfTheListHasNoNext() = runTest {
        val rig = rig()
        val ref = ListRef.Saved(rig.store.made("L", songs("a", "b")))
        rig.store.play(ref, 1)
        rig.reading(MediaStatus.Playing, "b", listOf("a", "b"))
        runCurrent()
        assertTrue(!rig.store.canNext.value)
        rig.speaker.calls.clear()
        rig.store.next()
        assertTrue(rig.speaker.calls.isEmpty())
    }
}
