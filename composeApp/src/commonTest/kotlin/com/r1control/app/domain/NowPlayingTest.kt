package com.r1control.app.domain

import com.r1control.app.domain.model.ChatMessage
import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.NowPlaying
import com.r1control.app.domain.model.Sender
import com.r1control.app.domain.model.VoiceActivity
import com.r1control.app.domain.model.formatClock
import com.r1control.app.domain.model.nowPlayingOf
import com.r1control.app.testing.bluetooth
import com.r1control.app.testing.device
import com.r1control.app.testing.media
import com.r1control.app.testing.speakerSnapshot
import com.r1control.app.testing.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NowPlayingTest {
    private val queue = listOf(track("a"), track("b"), track("c"))
    private val playing = media(MediaStatus.Playing, "b", "B", positionS = 15, durationS = 200, queue = queue)

    @Test fun nothingWhenEverythingIsIdle() {
        assertEquals(NowPlaying.Nothing, nowPlayingOf(speakerSnapshot()))
    }

    @Test fun stoppedAndIdleWithAKeptQueueAreNothing() {
        for (status in listOf(MediaStatus.Stopped, MediaStatus.Idle, MediaStatus.Unknown)) {
            assertEquals(NowPlaying.Nothing, nowPlayingOf(speakerSnapshot(media(status, queue = queue))), "$status")
        }
    }

    @Test fun playingMusic() {
        val np = assertIs<NowPlaying.Music>(nowPlayingOf(speakerSnapshot(playing)))
        assertEquals("B", np.title)
        assertEquals(NowPlaying.Phase.Playing, np.phase)
        assertEquals(NowPlaying.Output.ThisSpeaker, np.output)
        assertTrue(np.canSeek)
    }

    @Test fun pausedKeepsItsPosition() {
        val np = assertIs<NowPlaying.Music>(nowPlayingOf(speakerSnapshot(playing.copy(status = MediaStatus.Paused))))
        assertEquals(NowPlaying.Phase.Paused, np.phase)
        assertEquals(15, np.positionS)
    }

    @Test fun downloadingCarriesAClampedPercentAndCannotSeek() {
        val np = assertIs<NowPlaying.Music>(
            nowPlayingOf(speakerSnapshot(media(MediaStatus.Downloading, "a", "A", downloadPercent = -1, queue = queue))),
        )
        assertEquals(NowPlaying.Phase.Downloading(0), np.phase)
        assertFalse(np.canSeek)
        val half = assertIs<NowPlaying.Music>(
            nowPlayingOf(speakerSnapshot(media(MediaStatus.Downloading, "a", "A", downloadPercent = 55, queue = queue))),
        )
        assertEquals(NowPlaying.Phase.Downloading(55), half.phase)
    }

    @Test fun theSpeakersQueueHasANextOnlyAfterTheCurrentSong() {
        fun hasNext(videoId: String, q: List<com.r1control.app.domain.model.Track> = queue) =
            playing.copy(videoId = videoId, queue = q).hasNext
        assertTrue(hasNext("a"))
        assertTrue(hasNext("b"))
        assertFalse(hasNext("c"))
        assertFalse(hasNext("not-in-queue"))
        assertFalse(hasNext("a", emptyList()))
    }

    @Test fun positionIsHeldInsideTheDuration() {
        val np = assertIs<NowPlaying.Music>(nowPlayingOf(speakerSnapshot(playing.copy(positionS = 999))))
        assertEquals(200, np.positionS)
    }

    @Test fun playingOutToABluetoothSpeakerNamesIt() {
        val bt = bluetooth(connected = "14:C1", devices = listOf(device("14:C1", "Kitchen speaker")))
        val np = assertIs<NowPlaying.Music>(nowPlayingOf(speakerSnapshot(playing, bt)))
        assertEquals(NowPlaying.Output.Bluetooth("Kitchen speaker"), np.output)
    }

    @Test fun aPhoneStreamingInBeatsMusic() {
        val bt = bluetooth(incoming = "F4:4E", devices = listOf(device("F4:4E", "UGREEN-BT305")))
        assertEquals(NowPlaying.BluetoothIn("UGREEN-BT305"), nowPlayingOf(speakerSnapshot(playing, bt)))
    }

    @Test fun anUnlistedIncomingDeviceIsNamedByItsAddress() {
        assertEquals(NowPlaying.BluetoothIn("F4:4E"), nowPlayingOf(speakerSnapshot(bluetooth = bluetooth(incoming = "F4:4E"))))
    }

    @Test fun theAssistantNeedsToBeAwakeAndListeningOrSpeaking() {
        val chat = listOf(ChatMessage(Sender.User, "hi", 1), ChatMessage(Sender.Assistant, "hello", 2))
        assertEquals(
            NowPlaying.Assistant(NowPlaying.Activity.Speaking, "hello"),
            nowPlayingOf(speakerSnapshot(voice = VoiceActivity.Speaking, voiceAwake = true, chat = chat)),
        )
        assertEquals(
            NowPlaying.Assistant(NowPlaying.Activity.Listening, ""),
            nowPlayingOf(speakerSnapshot(voice = VoiceActivity.Listening, voiceAwake = true)),
        )
        assertEquals(NowPlaying.Nothing, nowPlayingOf(speakerSnapshot(voice = VoiceActivity.Speaking, voiceAwake = false)))
        assertEquals(NowPlaying.Nothing, nowPlayingOf(speakerSnapshot(voice = VoiceActivity.Idle, voiceAwake = true)))
    }

    @Test fun musicOutranksTheAssistant() {
        assertIs<NowPlaying.Music>(
            nowPlayingOf(speakerSnapshot(playing, voice = VoiceActivity.Speaking, voiceAwake = true)),
        )
    }

    @Test fun clockFormat() {
        assertEquals("0:00", formatClock(0))
        assertEquals("0:00", formatClock(-5))
        assertEquals("10:04", formatClock(604))
        assertEquals("1:01:05", formatClock(3665))
    }
}
