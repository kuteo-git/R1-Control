package com.r1control.app.qa.round3

import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.SpeakerSnapshot
import com.r1control.app.domain.model.VoiceActivity
import com.r1control.app.domain.model.VoiceControls
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.testing.FakeSpeaker
import com.r1control.app.testing.bluetooth
import com.r1control.app.testing.media
import com.r1control.app.testing.musicStore
import com.r1control.app.testing.speakerSnapshot
import com.r1control.app.testing.track
import com.r1control.app.testing.unreachable
import com.r1control.app.ui.Problem
import com.r1control.app.ui.control.ControlViewModel
import com.r1control.app.ui.control.EndControl
import com.r1control.app.ui.control.VoiceRow
import com.r1control.app.ui.music.MiniPlayerViewModel
import com.r1control.app.ui.music.MiniState
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The wake-word switch and "end the conversation", as the Control tab draws them over each reading. */
class Round3ControlTest {
    private val on = VoiceControls.Present(wakeWordOn = true)
    private val queue = listOf(track("a"), track("b"))

    private fun TestScope.control(speaker: FakeSpeaker): ControlViewModel {
        val feed = SpeakerFeed(speaker, backgroundScope)
        val vm = ControlViewModel(feed, speaker, musicStore(speaker, feed, backgroundScope), backgroundScope)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        return vm
    }

    private fun TestScope.rowOver(snapshot: SpeakerSnapshot): VoiceRow = control(FakeSpeaker().apply { current = snapshot }).state.value.voice

    private fun endShown(row: VoiceRow) = (row as? VoiceRow.Shown)?.canEndSession == true

    /** R3-50. `wake_enabled` absent, true, false. */
    @Test fun theWakeSwitchIsDrawnOnlyWhenTheR1ReportsItAndShowsItsValue() = runTest {
        val listening = speakerSnapshot(voice = VoiceActivity.Listening, voiceAwake = true)
        assertEquals(VoiceRow.Hidden, rowOver(listening.copy(voiceControls = VoiceControls.Absent)))
        assertEquals(true, assertIs<VoiceRow.Shown>(rowOver(listening.copy(voiceControls = VoiceControls.Present(true)))).wakeWordOn)
        assertEquals(false, assertIs<VoiceRow.Shown>(rowOver(listening.copy(voiceControls = VoiceControls.Present(false)))).wakeWordOn)
    }

    /** R3-51. Never over music: playing, paused or downloading, while the R1 reports SPEAKING, awake, for the song. */
    @Test fun endTheConversationIsNeverOfferedOverMusic() = runTest {
        for (status in listOf(MediaStatus.Playing, MediaStatus.Paused, MediaStatus.Downloading)) {
            for (voice in VoiceActivity.entries) {
                val snapshot = speakerSnapshot(media(status, "a", queue = queue), voice = voice, voiceAwake = true, voiceControls = on)
                assertTrue(!endShown(rowOver(snapshot)), "offered over $status music while $voice")
            }
        }
    }

    /** R3-52. Never while a phone streams in over Bluetooth, even with the R1 listening. */
    @Test fun endTheConversationIsNeverOfferedWithBluetoothIn() = runTest {
        val snapshot = speakerSnapshot(
            media(MediaStatus.Stopped), bluetooth(incoming = "F4:4E:FD:7A:0C:92"),
            voice = VoiceActivity.Listening, voiceAwake = true, voiceControls = on,
        )
        assertTrue(!endShown(rowOver(snapshot)))
    }

    /** R3-53. Listening beside a stopped player: the card shows nothing, the session is open, and End is offered. */
    @Test fun endTheConversationIsOfferedWhileListeningBesideAStoppedPlayer() = runTest {
        val snapshot = speakerSnapshot(media(MediaStatus.Stopped, queue = queue), voice = VoiceActivity.Listening, voiceAwake = true, voiceControls = on)
        assertTrue(endShown(rowOver(snapshot)))
    }

    /** R3-54. Speaking a reply with nothing playing, and listening with nothing loaded. */
    @Test fun endTheConversationIsOfferedWhileTheAssistantSpeaksOrListens() = runTest {
        for (voice in listOf(VoiceActivity.Speaking, VoiceActivity.Listening)) {
            for (status in listOf(MediaStatus.Idle, MediaStatus.Stopped)) {
                val snapshot = speakerSnapshot(media(status), voice = voice, voiceAwake = true, voiceControls = on)
                if (status == MediaStatus.Stopped && voice == VoiceActivity.Listening) continue
                assertTrue(endShown(rowOver(snapshot)), "not offered while $voice beside $status")
            }
        }
    }

    /** R3-55. Asleep, the R1 has no session to end. */
    @Test fun endTheConversationIsNotOfferedWhileTheR1Sleeps() = runTest {
        for (voice in VoiceActivity.entries) {
            val snapshot = speakerSnapshot(media(MediaStatus.Idle), voice = voice, voiceAwake = false, voiceControls = on)
            assertTrue(!endShown(rowOver(snapshot)), "offered asleep while $voice")
        }
    }

    /** R3-56. A failed wake write: the switch shows the R1's value again at once and the banner says why; the next try is sent. */
    @Test fun aFailedWakeWriteSnapsBackSaysWhyAndCanBeTriedAgain() = runTest {
        val speaker = FakeSpeaker().apply {
            current = speakerSnapshot(voiceControls = VoiceControls.Present(false))
            commandFailure = unreachable
        }
        val vm = control(speaker)
        vm.onWakeWord(true)
        runCurrent()
        assertEquals(VoiceRow.Shown(wakeWordOn = false, end = EndControl.Hidden), vm.state.value.voice)
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem)

        speaker.commandFailure = null
        vm.onWakeWord(true)
        runCurrent()
        assertEquals(listOf("wake word on", "wake word on"), speaker.calls)
        assertEquals(true, assertIs<VoiceRow.Shown>(vm.state.value.voice).wakeWordOn, "the finger's value while the R1 catches up")
        assertEquals(Problem.None, vm.state.value.problem, "a write that worked clears the banner")
    }

    /** R3-57. A wake write that worked but a reading that still shows the old value: the switch does not flicker back inside the hold. */
    @Test fun aTakenWakeWriteHoldsAgainstAnOlderReading() = runTest {
        val speaker = FakeSpeaker().apply { current = speakerSnapshot(voiceControls = VoiceControls.Present(true)) }
        val vm = control(speaker)
        vm.onWakeWord(false)
        runCurrent()
        assertEquals(false, assertIs<VoiceRow.Shown>(vm.state.value.voice).wakeWordOn)
        advanceTimeBy(1_000)
        assertEquals(false, assertIs<VoiceRow.Shown>(vm.state.value.voice).wakeWordOn, "inside the hold, the older reading does not win")
    }

    /** R3-22. The Control card and the mini player draw Next from the one decision. */
    @Test fun theControlCardAndTheMiniPlayerAgreeOnNext() = runTest {
        val speaker = FakeSpeaker().apply { current = speakerSnapshot(media(MediaStatus.Playing, "a", queue = queue)) }
        val feed = SpeakerFeed(speaker, backgroundScope)
        val store = musicStore(speaker, feed, backgroundScope)
        val control = ControlViewModel(feed, speaker, store, backgroundScope)
        val mini = MiniPlayerViewModel(feed, speaker, store, backgroundScope)
        backgroundScope.launch { control.state.collect {} }
        backgroundScope.launch { mini.state.collect {} }
        runCurrent()
        assertEquals(true, control.state.value.canNext)
        assertEquals(true, assertIs<MiniState.Showing>(mini.state.value).canNext)

        speaker.current = speakerSnapshot(media(MediaStatus.Playing, "b", queue = queue))
        feed.refresh()
        runCurrent()
        assertEquals(false, control.state.value.canNext)
        assertEquals(false, assertIs<MiniState.Showing>(mini.state.value).canNext)
    }
}
