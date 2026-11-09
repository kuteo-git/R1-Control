package com.r1control.app.ui

import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.VoiceActivity
import com.r1control.app.domain.model.VoiceControls
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.testing.FakeSpeaker
import com.r1control.app.testing.media
import com.r1control.app.testing.musicStore
import com.r1control.app.testing.speakerSnapshot
import com.r1control.app.testing.track
import com.r1control.app.testing.unreachable
import com.r1control.app.ui.control.ControlViewModel
import com.r1control.app.ui.control.EndControl
import com.r1control.app.ui.control.VoiceRow
import com.r1control.app.ui.control.voiceRowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ControlVoiceTest {
    private val queue = listOf(track("a"), track("b"))
    private val present = VoiceControls.Present(wakeWordOn = true)
    private val session = speakerSnapshot(voice = VoiceActivity.Listening, voiceAwake = true, voiceControls = present)
    private val song = speakerSnapshot(
        media(MediaStatus.Playing, "a", "Song", 5, 100, queue = queue),
        voice = VoiceActivity.Speaking, voiceAwake = true, voiceControls = present,
    )

    private fun TestScope.control(speaker: FakeSpeaker): ControlViewModel {
        val feed = SpeakerFeed(speaker, backgroundScope)
        val vm = ControlViewModel(feed, speaker, musicStore(speaker, feed, backgroundScope), backgroundScope)
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    @Test fun absentControlsDrawNothing() {
        val old = session.copy(voiceControls = VoiceControls.Absent)
        assertEquals(VoiceRow.Hidden, voiceRowOf(old, fresh = true, Held.Free, Held.Free))
    }

    @Test fun endIsNeverOfferedOverMusicWhateverTheR1ReportsAsSpeakingAwake() {
        assertEquals(VoiceRow.Shown(wakeWordOn = true, end = EndControl.Hidden), voiceRowOf(song, fresh = true, Held.Free, Held.Free))
    }

    @Test fun endIsOfferedForTheAssistantAndForListeningBesideStopped() {
        assertEquals(VoiceRow.Shown(true, EndControl.Offered), voiceRowOf(session, fresh = true, Held.Free, Held.Free))
        val beside = speakerSnapshot(media(MediaStatus.Stopped, queue = queue), voice = VoiceActivity.Listening, voiceAwake = true, voiceControls = present)
        assertEquals(VoiceRow.Shown(true, EndControl.Offered), voiceRowOf(beside, fresh = true, Held.Free, Held.Free))
    }

    @Test fun aPressedEndSaysEndingWhileTheHoldLastsAndTheSessionStillShows() {
        assertEquals(VoiceRow.Shown(true, EndControl.Ending), voiceRowOf(session, fresh = true, Held.Free, Held.Holding(true)))
        assertEquals(VoiceRow.Shown(true, EndControl.Ending), voiceRowOf(session, fresh = false, Held.Free, Held.Holding(true)), "the press is ours, whatever the wifi")
    }

    @Test fun aPressedEndGoesAsSoonAsAReadingShowsTheSessionClosed() {
        val asleep = session.copy(voiceAwake = false)
        assertEquals(VoiceRow.Shown(true, EndControl.Hidden), voiceRowOf(asleep, fresh = true, Held.Free, Held.Holding(true)))
    }

    @Test fun endIsNotOfferedOverAStaleReading() {
        assertEquals(VoiceRow.Shown(true, EndControl.Hidden), voiceRowOf(session, fresh = false, Held.Free, Held.Free))
    }

    @Test fun theSwitchShowsTheFingersValueUntilTheR1HasCaughtUp() {
        assertEquals(VoiceRow.Shown(false, EndControl.Offered), voiceRowOf(session, fresh = true, Held.Holding(false), Held.Free))
    }

    @Test fun theWakeSwitchHoldsTheFingersValueAndTheR1TakesIt() = runTest {
        val speaker = FakeSpeaker().apply { current = session }
        val vm = control(speaker)
        runCurrent()
        assertEquals(VoiceRow.Shown(true, EndControl.Offered), vm.state.value.voice)

        vm.onWakeWord(false)
        runCurrent()
        assertEquals(listOf("wake word off"), speaker.calls)
        speaker.current = session.copy(voiceControls = VoiceControls.Present(false))
        advanceTimeBy(1_700)
        assertEquals(VoiceRow.Shown(false, EndControl.Offered), vm.state.value.voice, "the R1 has caught up and so has the hold")
        advanceTimeBy(1_700)
        assertEquals(VoiceRow.Shown(false, EndControl.Offered), vm.state.value.voice)
    }

    @Test fun theWakeSwitchSnapsBackAtOnceWhenTheR1RefusesAndTheBannerSaysWhy() = runTest {
        val speaker = FakeSpeaker().apply { current = session; commandFailure = unreachable }
        val vm = control(speaker)
        runCurrent()
        vm.onWakeWord(false)
        runCurrent()
        assertEquals(VoiceRow.Shown(true, EndControl.Offered), vm.state.value.voice)
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem)
    }

    @Test fun endSendsTheRouteOnceAndTheButtonStaysAwayUntilTheHoldIsOver() = runTest {
        val speaker = FakeSpeaker().apply { current = session }
        val vm = control(speaker)
        runCurrent()
        vm.onEndSession()
        runCurrent()
        assertEquals(listOf("end session"), speaker.calls)
        assertEquals(EndControl.Ending, (vm.state.value.voice as VoiceRow.Shown).end, "the row says it is ending")
        vm.onEndSession()
        runCurrent()
        assertEquals(listOf("end session"), speaker.calls, "a second press while it is ending sends nothing")
        advanceTimeBy(4_100)
        assertTrue((vm.state.value.voice as VoiceRow.Shown).canEndSession, "the session is still open on the R1, so the button is back")
    }

    @Test fun aRefusedEndBringsTheButtonBackAtOnce() = runTest {
        val speaker = FakeSpeaker().apply { current = session; commandFailure = unreachable }
        val vm = control(speaker)
        runCurrent()
        vm.onEndSession()
        runCurrent()
        assertTrue((vm.state.value.voice as VoiceRow.Shown).canEndSession)
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem)
    }

    @Test fun aStalePressOverMusicStopsNothing() = runTest {
        val speaker = FakeSpeaker().apply { current = song }
        val vm = control(speaker)
        runCurrent()
        vm.onEndSession()
        runCurrent()
        assertTrue(speaker.calls.isEmpty(), "ending the session would stop the song: ${speaker.calls}")
    }

    @Test fun anOlderR1HasNoControlsAndAPressDoesNothing() = runTest {
        val speaker = FakeSpeaker().apply { current = session.copy(voiceControls = VoiceControls.Absent) }
        val vm = control(speaker)
        runCurrent()
        assertEquals(VoiceRow.Hidden, vm.state.value.voice)
        vm.onEndSession()
        runCurrent()
        assertTrue(speaker.calls.isEmpty())
    }

    /**
     * R3B-07. Wifi drops and the card goes stale. The last good reading showed a session, but music may
     * have started since (a voice request), and the R1's sleep path would flush it: End is not drawn,
     * and a press sends nothing.
     */
    @Test fun R3B_07_endIsNotOfferedOverAStaleReading() = runTest {
        val speaker = FakeSpeaker().apply { current = session }
        val vm = control(speaker)
        runCurrent()
        assertTrue((vm.state.value.voice as VoiceRow.Shown).canEndSession, "set up: offered over a fresh reading")

        speaker.pollFailure = unreachable
        advanceTimeBy(10_000)
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem, "set up: the reading is stale")
        assertTrue(!(vm.state.value.voice as VoiceRow.Shown).canEndSession, "End is drawn over a stale reading")
        vm.onEndSession()
        runCurrent()
        assertTrue(speaker.calls.isEmpty(), "a press over a stale reading sent ${speaker.calls}")
    }
}
