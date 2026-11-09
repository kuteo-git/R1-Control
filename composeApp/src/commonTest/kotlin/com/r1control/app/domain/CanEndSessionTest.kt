package com.r1control.app.domain

import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.VoiceActivity
import com.r1control.app.domain.model.canEndSession
import com.r1control.app.testing.bluetooth
import com.r1control.app.testing.media
import com.r1control.app.testing.speakerSnapshot
import com.r1control.app.testing.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "End the conversation" stops music on the R1, so it is offered only in a session, never over a song. */
class CanEndSessionTest {
    private val queue = listOf(track("a"), track("b"))

    @Test fun neverOverMusicOfAnyPhaseEvenThoughTheR1ReportsSpeakingAwakeForTheWholeSong() {
        for (status in listOf(MediaStatus.Playing, MediaStatus.Paused, MediaStatus.Downloading)) {
            val snapshot = speakerSnapshot(media(status, "a", queue = queue), voice = VoiceActivity.Speaking, voiceAwake = true)
            assertFalse(canEndSession(snapshot), "$status")
        }
    }

    @Test fun neverOverBluetoothIn() {
        val snapshot = speakerSnapshot(bluetooth = bluetooth(incoming = "F4:4E"), voice = VoiceActivity.Listening, voiceAwake = true)
        assertFalse(canEndSession(snapshot))
    }

    @Test fun offeredForTheAssistantListeningOrSpeaking() {
        assertTrue(canEndSession(speakerSnapshot(voice = VoiceActivity.Listening, voiceAwake = true)))
        assertTrue(canEndSession(speakerSnapshot(voice = VoiceActivity.Speaking, voiceAwake = true)))
    }

    @Test fun offeredForListeningBesideAStoppedPlayerWhichTheCardShowsAsNothing() {
        val snapshot = speakerSnapshot(media(MediaStatus.Stopped, queue = queue), voice = VoiceActivity.Listening, voiceAwake = true)
        assertEquals(com.r1control.app.domain.model.NowPlaying.Nothing, snapshot.nowPlaying)
        assertTrue(canEndSession(snapshot))
    }

    @Test fun notWhenTheR1IsAsleepOrIdle() {
        assertFalse(canEndSession(speakerSnapshot()))
        assertFalse(canEndSession(speakerSnapshot(media(MediaStatus.Stopped), voice = VoiceActivity.Listening, voiceAwake = false)))
        assertFalse(canEndSession(speakerSnapshot(media(MediaStatus.Stopped), voice = VoiceActivity.Idle, voiceAwake = true)))
    }
}
