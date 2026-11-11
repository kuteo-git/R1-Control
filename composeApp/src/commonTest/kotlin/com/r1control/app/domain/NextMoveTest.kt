package com.r1control.app.domain

import com.r1control.app.domain.model.ListRef
import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.NextMove
import com.r1control.app.domain.model.PlayOrder
import com.r1control.app.domain.model.PlayQueue
import com.r1control.app.domain.model.Playing
import com.r1control.app.domain.model.SongList
import com.r1control.app.domain.model.nextMove
import com.r1control.app.domain.model.playSequence
import com.r1control.app.domain.model.playsFrom
import com.r1control.app.domain.model.queueAround
import com.r1control.app.domain.model.upcomingAfter
import com.r1control.app.testing.media
import com.r1control.app.testing.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every row of the Next table: what a press does for the list as it is now and the speaker as it reads. */
class NextMoveTest {
    private val ref = ListRef.Results(1)
    private fun list(vararg ids: String) = SongList.of(ids.map { track(it) })
    private fun from(vararg sent: String, order: PlayOrder = PlayOrder.Listed) = Playing.FromList(ref, order, sent.toList())
    private fun reading(videoId: String, vararg queue: String, status: MediaStatus = MediaStatus.Playing) =
        media(status, videoId, queue = queue.map { track(it) })
    private fun resend(songs: List<String>, start: Int) = NextMove.Resend(PlayQueue(songs.map { track(it) }, start))

    @Test fun downloadingOffersNoNext() {
        assertEquals(NextMove.None, nextMove(from("a", "b"), list("a", "b"), reading("a", "a", "b", status = MediaStatus.Downloading)))
        assertEquals(NextMove.None, nextMove(Playing.None, SongList.Empty, reading("a", "a", "b", status = MediaStatus.Downloading)))
    }

    @Test fun stoppedIdleAndUnknownOfferNoNext() {
        for (status in listOf(MediaStatus.Stopped, MediaStatus.Idle, MediaStatus.Unknown)) {
            assertEquals(NextMove.None, nextMove(from("a", "b"), list("a", "b"), reading("a", "a", "b", status = status)), "$status")
        }
    }

    @Test fun noAppListFallsBackToTheSpeakersQueue() {
        assertEquals(NextMove.Speaker, nextMove(Playing.None, SongList.Empty, reading("a", "a", "b")))
        assertEquals(NextMove.None, nextMove(Playing.None, SongList.Empty, reading("b", "a", "b")))
        assertEquals(NextMove.None, nextMove(Playing.None, SongList.Empty, reading("a")))
    }

    @Test fun pausedOffersNextLikePlaying() {
        assertEquals(NextMove.Speaker, nextMove(from("a", "b"), list("a", "b"), reading("a", "a", "b", status = MediaStatus.Paused)))
    }

    @Test fun aSongTheAppDidNotSendFallsBackToTheSpeaker() {
        val songs = list("a", "b")
        assertEquals(NextMove.Speaker, nextMove(from("a", "b"), songs, reading("x", "x", "y")))
        assertEquals(NextMove.None, nextMove(from("a", "b"), songs, reading("y", "x", "y")))
    }

    @Test fun anUneditedListUsesTheSpeakersNext() {
        assertEquals(NextMove.Speaker, nextMove(from("a", "b", "c"), list("a", "b", "c"), reading("a", "a", "b", "c")))
        assertEquals(NextMove.Speaker, nextMove(from("a", "b", "c"), list("a", "b", "c"), reading("b", "a", "b", "c")))
    }

    @Test fun aSongRemovedAfterThisOneResendsFromTheNextKept() {
        val move = nextMove(from("a", "b", "c"), list("a", "c"), reading("a", "a", "b", "c"))
        assertEquals(resend(listOf("a", "c"), 1), move)
    }

    @Test fun aSongMovedUpBehindThisOneResends() {
        val move = nextMove(from("a", "b", "c"), list("a", "c", "b"), reading("a", "a", "b", "c"))
        assertEquals(resend(listOf("a", "c", "b"), 1), move)
    }

    @Test fun theNextSongMovedAwayResends() {
        val move = nextMove(from("a", "b", "c"), list("b", "a", "c"), reading("b", "a", "b", "c"))
        assertEquals(resend(listOf("b", "a", "c"), 1), move)
    }

    @Test fun anEditBeforeThisSongStillUsesTheSpeakersNext() {
        assertEquals(NextMove.Speaker, nextMove(from("a", "b", "c"), list("b", "c"), reading("b", "a", "b", "c")))
    }

    @Test fun thePlayingSongRemovedResendsFromJustAfterTheSongSentBeforeIt() {
        val songs = list("a", "d")
        val move = nextMove(from("a", "b", "c", "d"), songs, reading("b", "a", "b", "c", "d"))
        assertEquals(resend(listOf("a", "d"), 1), move)
    }

    @Test fun thePlayingSongRemovedFollowedBySongsNeverSentStillHasThem() {
        val move = nextMove(from("a", "b"), list("a", "c"), reading("b", "a", "b"))
        assertEquals(resend(listOf("a", "c"), 1), move)
    }

    @Test fun thePlayingSongRemovedAtTheStartOfItsWindowResendsFromTheSongSentAfterIt() {
        // The window began at b; a, above it, was never sent and never played.
        val move = nextMove(from("b", "c", "d"), list("a", "c", "d"), reading("b", "b", "x"))
        assertEquals(resend(listOf("a", "c", "d"), 1), move)
    }

    @Test fun thePlayingSongRemovedWithEverySentSongGoneStartsFromTheTop() {
        val move = nextMove(from("a"), list("b", "c"), reading("a", "a"))
        assertEquals(resend(listOf("b", "c"), 0), move)
    }

    @Test fun thePlayingSongRemovedUsesTheSpeakersNextWhenItAlreadyGoesTheRightWay() {
        val move = nextMove(from("a", "b", "c", "d"), list("a", "c", "d"), reading("b", "a", "b", "c", "d"))
        assertEquals(NextMove.Speaker, move)
    }

    @Test fun thePlayingSongRemovedWithNothingSurvivingAfterItIsTheEnd() {
        assertEquals(NextMove.None, nextMove(from("a", "b", "c"), list("a"), reading("b", "a", "b", "c")))
    }

    @Test fun theLastSongOfTheListOffersNoNextEvenWithDeletedSongsQueued() {
        val move = nextMove(from("a", "b", "c", "x"), list("a", "b", "c"), reading("c", "a", "b", "c", "x"))
        assertEquals(NextMove.None, move)
    }

    @Test fun theEndOfTheThirtySongWindowResendsTheNextWindow() {
        val ids = List(45) { "s$it" }
        val songs = list(*ids.toTypedArray())
        val sent = queueAround(songs.tracks, 0).tracks.map { it.id }
        val move = nextMove(Playing.FromList(ref, PlayOrder.Listed, sent), songs, reading("s29", *sent.toTypedArray()))
        assertEquals(NextMove.Resend(queueAround(songs.tracks, 30)), move)
        move as NextMove.Resend
        assertEquals("s30", move.queue.tracks[move.queue.startIndex].id)
    }

    @Test fun aSpeakerQueueThatDoesNotEchoWhatWasSentResends() {
        val songs = list("a", "b", "c")
        assertEquals(resend(listOf("a", "b", "c"), 1), nextMove(from("a", "b", "c"), songs, reading("a")))
        assertEquals(resend(listOf("a", "b", "c"), 1), nextMove(from("a", "b", "c"), songs, reading("a", "a", "z")))
    }

    @Test fun shuffledOrderIsFollowedAndAddedSongsGoLast() {
        val songs = list("a", "b", "c", "d", "e")
        val order = PlayOrder.Shuffled(listOf("c", "a", "d", "b"))
        assertEquals(listOf("c", "a", "d", "b", "e"), playSequence(songs, order).map { it.id })
        val playing = from("c", "a", "d", "b", order = order)
        assertEquals(NextMove.Speaker, nextMove(playing, songs, reading("c", "c", "a", "d", "b")))
        assertEquals(resend(listOf("c", "a", "d", "b", "e"), 4), nextMove(playing, songs, reading("b", "c", "a", "d", "b")))
    }

    @Test fun aSongRemovedFromAShuffledPlayDropsOutOfItsOrder() {
        val order = PlayOrder.Shuffled(listOf("c", "a", "d", "b"))
        assertEquals(listOf("c", "d", "b"), playSequence(list("b", "c", "d"), order).map { it.id })
    }

    @Test fun theListedOrderIsTheListsOwn() {
        assertEquals(listOf("a", "b"), playSequence(list("a", "b"), PlayOrder.Listed).map { it.id })
    }

    // "Playing from this playlist": the pointer and the reading together.

    @Test fun aListPlaysFromItsPageOnlyWhileTheSpeakerIsOnASongItWasSent() {
        val pointer = from("a", "b")
        for (status in listOf(MediaStatus.Playing, MediaStatus.Paused, MediaStatus.Downloading)) {
            assertTrue(playsFrom(pointer, ref, reading("a", "a", "b", status = status)), "$status")
        }
        for (status in listOf(MediaStatus.Stopped, MediaStatus.Idle, MediaStatus.Unknown)) {
            assertFalse(playsFrom(pointer, ref, reading("a", "a", "b", status = status)), "$status")
        }
        assertFalse(playsFrom(pointer, ref, reading("v1", "v1", "v2")), "a song asked for by voice")
        assertFalse(playsFrom(pointer, ListRef.Results(2), reading("a", "a", "b")), "another list")
        assertFalse(playsFrom(Playing.None, ref, reading("a", "a", "b")), "nothing sent")
    }

    // What the speaker is told follows the song playing.

    private fun upcoming(playing: Playing, songs: SongList, current: String) = upcomingAfter(playing, songs, current)?.map { it.id }

    @Test fun upcomingIsTheRestOfTheListAfterTheSongPlaying() {
        assertEquals(listOf("c", "d"), upcoming(from("a", "b", "c", "d"), list("a", "b", "c", "d"), "b"))
        assertEquals(listOf("c", "d"), upcoming(from("a", "b", "c", "d"), list("a", "c", "d"), "a"), "b removed")
        assertEquals(emptyList(), upcoming(from("a", "b"), list("a", "b"), "b"), "the last song: nothing after it")
    }

    @Test fun upcomingFollowsTheShuffleTheListPlaysIn() {
        val shuffled = PlayOrder.Shuffled(listOf("c", "a", "d", "b"))
        assertEquals(listOf("d", "b"), upcoming(from("c", "a", "d", "b", order = shuffled), list("a", "b", "c", "d"), "a"))
    }

    @Test fun upcomingAfterARemovedSongPlayingStartsWhereItWas() {
        assertEquals(listOf("c"), upcoming(from("a", "b", "c"), list("a", "c"), "b"))
    }

    @Test fun upcomingIsOnlyForTheAppsOwnList() {
        assertNull(upcoming(Playing.None, list("a", "b"), "a"))
        assertNull(upcoming(from("a", "b"), list("a", "b", "x"), "x"), "a song the phone did not send")
    }

    @Test fun upcomingIsAtMostAQueuesWorthAfterTheSongPlaying() {
        val ids = List(40) { "s$it" }
        val result = upcoming(from(*ids.take(30).toTypedArray()), SongList.of(ids.map { track(it) }), "s0")
        assertEquals(ids.drop(1).take(29), result)
    }
}
