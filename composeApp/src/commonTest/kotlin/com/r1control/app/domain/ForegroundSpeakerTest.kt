package com.r1control.app.domain

import com.r1control.app.domain.usecase.Foreground
import com.r1control.app.domain.usecase.ForegroundSpeaker
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.testing.FakeSpeaker
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ForegroundSpeakerTest {
    @Test fun noScreenPollsTheSpeakerWhileTheAppIsHidden() = runTest {
        val r1 = FakeSpeaker()
        val foreground = Foreground()
        val speaker = ForegroundSpeaker(r1, foreground)
        val feed = SpeakerFeed(speaker, backgroundScope)
        backgroundScope.launch { feed.view.collect {} }
        backgroundScope.launch {
            while (true) {
                speaker.bluetooth()
                kotlinx.coroutines.delay(1_000)
            }
        }
        runCurrent()
        assertEquals(1, r1.snapshots)
        assertEquals(1, r1.bluetoothReads)

        foreground.set(false)
        advanceTimeBy(600_000)
        assertEquals(1, r1.snapshots, "the shared feed still has collectors, yet nothing reached the speaker")
        assertEquals(1, r1.bluetoothReads, "nor did a page's own loop")

        foreground.set(true)
        runCurrent()
        assertEquals(2, r1.snapshots, "back on screen, it reads at once")
        assertEquals(2, r1.bluetoothReads)
    }

    @Test fun commandsAreTapsAndGoWhateverTheLifecycleSays() = runTest {
        val r1 = FakeSpeaker()
        val foreground = Foreground().apply { set(false) }
        ForegroundSpeaker(r1, foreground).pause()
        assertEquals(listOf("pause"), r1.calls)
    }
}
