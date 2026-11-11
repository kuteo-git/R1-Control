package com.r1control.app.domain

import com.r1control.app.domain.model.ListEdit
import com.r1control.app.domain.model.ListRef
import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.usecase.CommandOutcome
import com.r1control.app.domain.usecase.Created
import com.r1control.app.domain.usecase.MusicStore
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.testing.FakeSpeaker
import com.r1control.app.testing.MemoryPreferences
import com.r1control.app.testing.media
import com.r1control.app.testing.speakerSnapshot
import com.r1control.app.testing.track
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * "Khi ở playlist, xoá songs kế tiếp, khi song hiện tại phát xong, nó vẫn phát
 * song đã xoá." The speaker keeps the list it was last sent and walks it on
 * its own when a song ends (xiaozhi-server `_play_queue`); the phone is not
 * asked. A song removed on the phone must not be the one it walks to.
 */
class QueueEditTest {
    /** The speaker as the server runs it: the list it was sent, walked at each song's end. */
    private open class QueueSpeaker : FakeSpeaker() {
        var held: List<Track> = emptyList()
        val heard = mutableListOf<String>()

        override suspend fun play(tracks: List<Track>, startIndex: Int) {
            super.play(tracks, startIndex)
            held = tracks.drop(startIndex)
            show()
        }

        fun songEnds() {
            held = held.drop(1)
            show()
        }

        override suspend fun queue(tracks: List<Track>) {
            super.queue(tracks)
            held = held.take(1) + tracks
        }

        private fun show() {
            val song = held.firstOrNull() ?: return
            heard += song.id
            current = speakerSnapshot(media(MediaStatus.Playing, song.id, queue = held))
        }
    }

    private class Rig(val speaker: QueueSpeaker, scope: kotlinx.coroutines.CoroutineScope) {
        val feed = SpeakerFeed(speaker, scope)
        private var clock = 1_000L
        val store = MusicStore(speaker, MemoryPreferences(), feed, scope, Random(1)) { clock++ }
    }

    private fun TestScope.rig(speaker: QueueSpeaker = QueueSpeaker()) = Rig(speaker, backgroundScope).also { runCurrent() }

    /** A playlist of [ids], playing from its first song, as the speaker reports it. */
    private fun TestScope.playing(rig: Rig, vararg ids: String): ListRef {
        val id = assertIs<Created.Made>(rig.store.create("Nhạc", ids.map { track(it) })).id
        val ref = ListRef.Saved(id)
        launch { rig.store.play(ref, 0) }
        runCurrent()
        rig.feed.refresh(); runCurrent()
        return ref
    }

    private fun TestScope.songEnds(rig: Rig) {
        rig.speaker.songEnds()
        rig.feed.refresh(); runCurrent()
    }

    @Test fun aSongRemovedAfterTheOnePlayingIsNotPlayedWhenItEnds() = runTest {
        val rig = rig()
        val ref = playing(rig, "a", "b", "c", "d")
        assertEquals(listOf("a"), rig.speaker.heard)

        rig.store.edit(ref, ListEdit.Remove("b"))
        runCurrent()
        assertEquals(listOf("a"), rig.speaker.heard, "the song playing is not interrupted")
        assertEquals("queue [c, d]", rig.speaker.calls.last())

        songEnds(rig)
        assertEquals(listOf("a", "c"), rig.speaker.heard, "b was removed")
        songEnds(rig)
        assertEquals(listOf("a", "c", "d"), rig.speaker.heard)
    }

    @Test fun movedAndAddedSongsPlayInTheNewOrder() = runTest {
        val rig = rig()
        val ref = playing(rig, "a", "b", "c")
        rig.store.edit(ref, ListEdit.Move(2, 1))
        rig.store.edit(ref, ListEdit.Add(listOf(track("x"))))
        runCurrent()
        repeat(3) { songEnds(rig) }
        assertEquals(listOf("a", "c", "b", "x"), rig.speaker.heard)
    }

    @Test fun aBurstOfEditsSendsWhatTheyAddUpTo() = runTest {
        val rig = rig()
        val ref = playing(rig, "a", "b", "c", "d")
        rig.store.edit(ref, ListEdit.Remove("b"))
        rig.store.edit(ref, ListEdit.Remove("c"))
        runCurrent()
        assertEquals("queue [d]", rig.speaker.calls.last(), "${rig.speaker.calls}")
    }

    @Test fun theSongPlayingRemovedIsFollowedByTheOneAfterIt() = runTest {
        val rig = rig()
        val ref = playing(rig, "a", "b", "c")
        rig.store.edit(ref, ListEdit.Remove("a"))
        runCurrent()
        assertEquals(listOf("a"), rig.speaker.heard, "it plays on")
        songEnds(rig)
        assertEquals(listOf("a", "b"), rig.speaker.heard)
    }

    @Test fun anEditToAListThatIsNotPlayingSendsNothing() = runTest {
        val rig = rig()
        playing(rig, "a", "b")
        val other = ListRef.Saved(assertIs<Created.Made>(rig.store.create("Khác", listOf(track("x"), track("y")))).id)
        val sent = rig.speaker.calls.size
        rig.store.edit(other, ListEdit.Remove("x"))
        runCurrent()
        assertEquals(sent, rig.speaker.calls.size, "${rig.speaker.calls}")
    }

    @Test fun nothingIsSentWhenTheSpeakerHasStopped() = runTest {
        val rig = rig()
        val ref = playing(rig, "a", "b", "c")
        rig.speaker.current = speakerSnapshot(media(MediaStatus.Stopped, "a", queue = emptyList()))
        rig.feed.refresh(); runCurrent()
        val sent = rig.speaker.calls.size
        rig.store.edit(ref, ListEdit.Remove("b"))
        runCurrent()
        assertEquals(sent, rig.speaker.calls.size, "${rig.speaker.calls}")
    }

    /** An R1 built before /api/media/queue answers 404: nothing is said, and Next still skips the removed song. */
    @Test fun anOlderSpeakerRefusesQuietlyAndNextStillFollowsTheEdit() = runTest {
        val old = object : QueueSpeaker() {
            override suspend fun queue(tracks: List<Track>) = throw SpeakerException.Refused("404")
        }
        val rig = rig(old)
        val ref = playing(rig, "a", "b", "c")
        rig.store.edit(ref, ListEdit.Remove("b"))
        runCurrent()
        assertIs<CommandOutcome.Done>(rig.store.next())
        assertEquals(listOf("a", "c"), old.heard)
    }
}
