package com.r1control.app.ui

import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.NowPlaying
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.testing.FakeSpeaker
import com.r1control.app.testing.media
import com.r1control.app.testing.musicStore
import com.r1control.app.testing.speakerSnapshot
import com.r1control.app.testing.track
import com.r1control.app.testing.unreachable
import com.r1control.app.ui.control.CardState
import com.r1control.app.ui.control.ControlViewModel
import com.r1control.app.ui.control.volumeAfterDrag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ControlViewModelTest {
    private val queue = listOf(track("a"), track("b"))
    private fun playing(position: Int = 15, id: String = "a") = speakerSnapshot(
        media = media(MediaStatus.Playing, videoId = id, title = "Song", positionS = position, durationS = 200, queue = queue),
    )

    private fun TestScope.control(speaker: FakeSpeaker): ControlViewModel {
        val scope: CoroutineScope = backgroundScope
        val feed = SpeakerFeed(speaker, scope)
        val vm = ControlViewModel(feed, speaker, musicStore(speaker, feed, scope), scope)
        scope.launch { vm.state.collect {} }
        return vm
    }

    private fun ControlViewModel.music(): NowPlaying.Music {
        val card = assertIs<CardState.Showing>(state.value.card)
        return assertIs<NowPlaying.Music>(card.nowPlaying)
    }

    @Test fun showsConnectingUntilTheFirstReading() = runTest {
        val vm = control(FakeSpeaker())
        assertEquals(CardState.Connecting, vm.state.value.card)
        assertEquals(false, vm.state.value.volume.enabled)
    }

    @Test fun cardFollowsWhatThePlayerReports() = runTest {
        val speaker = FakeSpeaker().apply { current = playing() }
        val vm = control(speaker)
        runCurrent()
        assertEquals("Song", vm.music().title)
        assertTrue(vm.state.value.canNext)

        speaker.current = speakerSnapshot()
        advanceTimeBy(1_100)
        assertEquals(CardState.Showing(NowPlaying.Nothing), vm.state.value.card)
    }

    @Test fun aSpeakerUnreachableFromTheStartIsSaidOnTheCardAndPollingGoesOn() = runTest {
        val speaker = FakeSpeaker().apply { pollFailure = unreachable }
        val vm = control(speaker)
        runCurrent()
        assertEquals(CardState.Unreachable(unreachable), vm.state.value.card)
        assertEquals(Problem.None, vm.state.value.problem, "the card says it; a banner would say it twice")
        assertEquals(false, vm.state.value.volume.enabled, "no level is known, so none is shown")

        speaker.pollFailure = null
        speaker.current = playing()
        advanceTimeBy(5_100)
        assertEquals(Problem.None, vm.state.value.problem)
        assertEquals("Song", vm.music().title)
    }

    @Test fun aDroppedPollKeepsTheLastCardAndSaysSo() = runTest {
        val speaker = FakeSpeaker().apply { current = playing() }
        val vm = control(speaker)
        runCurrent()
        speaker.pollFailure = unreachable
        advanceTimeBy(1_100)
        assertEquals("Song", vm.music().title)
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem)
    }

    @Test fun theShownVolumeIsTheFingersWhileDraggingAndForAMomentAfter() = runTest {
        val speaker = FakeSpeaker().apply { current = playing() }
        val vm = control(speaker)
        runCurrent()
        assertEquals(40, vm.state.value.volume.pct)

        vm.onVolumeDrag(60)
        runCurrent()
        assertEquals(60, vm.state.value.volume.pct)

        advanceTimeBy(3_000)
        assertEquals(60, vm.state.value.volume.pct, "polls must not overwrite a drag in progress")

        vm.onVolumeRelease()
        advanceTimeBy(1_400)
        assertEquals(60, vm.state.value.volume.pct, "held for the grace period after release")
        advanceTimeBy(200)
        assertEquals(40, vm.state.value.volume.pct, "then the speaker's own value is shown again")
    }

    @Test fun volumeIsSnappedToTheHardwareScaleAndTheLastSentIsTheReleaseValue() = runTest {
        val speaker = FakeSpeaker().apply { current = playing() }
        val vm = control(speaker)
        runCurrent()
        listOf(10, 20, 33, 47, 52).forEach { vm.onVolumeDrag(it); advanceTimeBy(40) }
        vm.onVolumeRelease()
        advanceTimeBy(1_000)
        assertEquals(53, vm.state.value.volume.pct)
        assertEquals(53, speaker.volumes.last())
    }

    @Test fun seekIsSentOnceOnReleaseNotWhileDragging() = runTest {
        val speaker = FakeSpeaker().apply { current = playing() }
        val vm = control(speaker)
        runCurrent()
        vm.onSeekDrag(50)
        vm.onSeekDrag(90)
        vm.onSeekDrag(120)
        runCurrent()
        assertEquals(120, vm.music().positionS)
        assertTrue(speaker.calls.none { it.startsWith("seek") })

        vm.onSeekRelease()
        runCurrent()
        assertEquals(listOf("seek 120"), speaker.calls.filter { it.startsWith("seek") })
    }

    @Test fun playPausePausesAPlayingSongAndResumesAPausedOne() = runTest {
        val speaker = FakeSpeaker().apply { current = playing() }
        val vm = control(speaker)
        runCurrent()
        vm.onPlayPause()
        runCurrent()
        assertEquals(listOf("pause"), speaker.calls)

        speaker.current = speakerSnapshot(media = media(MediaStatus.Paused, "a", "Song", 15, 200, queue = queue))
        advanceTimeBy(3_100)
        vm.onPlayPause()
        runCurrent()
        assertEquals(listOf("pause", "resume"), speaker.calls)
    }

    @Test fun playPauseDoesNothingWhileDownloadingOrWithNothingPlaying() = runTest {
        val speaker = FakeSpeaker().apply {
            current = speakerSnapshot(media = media(MediaStatus.Downloading, "a", "Song", downloadPercent = 30))
        }
        val vm = control(speaker)
        runCurrent()
        vm.onPlayPause()
        speaker.current = speakerSnapshot()
        advanceTimeBy(1_100)
        vm.onPlayPause()
        runCurrent()
        assertTrue(speaker.calls.isEmpty())
    }

    @Test fun nextAndStopAreSentAndAFailureIsShownNotThrown() = runTest {
        val speaker = FakeSpeaker().apply { current = playing() }
        val vm = control(speaker)
        runCurrent()
        vm.onNext()
        vm.onStop()
        runCurrent()
        assertEquals(listOf("next", "stop"), speaker.calls)
        assertEquals(Problem.None, vm.state.value.problem)

        speaker.commandFailure = unreachable
        vm.onNext()
        runCurrent()
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem)
    }

    @Test fun aCommandRefreshesTheCardWithoutWaitingForThePoll() = runTest {
        val speaker = FakeSpeaker().apply { current = playing() }
        val vm = control(speaker)
        runCurrent()
        speaker.current = speakerSnapshot(media = media(MediaStatus.Paused, "a", "Song", 15, 200, queue = queue))
        vm.onPlayPause()
        runCurrent()
        assertEquals(NowPlaying.Phase.Paused, vm.music().phase)
    }

    @Test fun aDragMovesTheLevelByHowFarTheFingerTravelsNotToWhereItLanded() {
        assertEquals(40, volumeAfterDrag(40, 0f, 400f), "a finger that has not moved sets nothing")
        assertEquals(65, volumeAfterDrag(40, 100f, 400f), "a quarter of the pill is a quarter of the range")
        assertEquals(15, volumeAfterDrag(40, -100f, 400f))
        assertEquals(100, volumeAfterDrag(40, 900f, 400f))
        assertEquals(0, volumeAfterDrag(40, -900f, 400f))
        assertEquals(40, volumeAfterDrag(40, 10f, 0f))
    }

    @Test fun tryAgainOnTheUnreachableCardReadsNowNotAtTheNextPoll() = runTest {
        val speaker = FakeSpeaker().apply { pollFailure = unreachable }
        val vm = control(speaker)
        runCurrent()
        speaker.pollFailure = null
        speaker.current = playing()
        vm.onRetry()
        runCurrent()
        assertEquals("Song", vm.music().title)
    }

    @Test fun aRefusalStaysLongEnoughToReadThenGoesWhileTheSpeakerAnswers() = runTest {
        val refused = com.r1control.app.domain.repository.SpeakerException.Refused("queue busy")
        val speaker = FakeSpeaker().apply { current = playing() }
        val vm = control(speaker)
        runCurrent()
        speaker.commandFailure = refused
        vm.onNext()
        runCurrent()
        advanceTimeBy(3_000)
        assertEquals(Problem.Failed(refused), vm.state.value.problem, "the refresh answered at once; the refusal is still readable")
        advanceTimeBy(2_000)
        assertEquals(Problem.None, vm.state.value.problem)
    }

    @Test fun aFailedCommandStaysWhileTheSpeakerStillDoesNotAnswer() = runTest {
        val speaker = FakeSpeaker().apply { current = playing() }
        val vm = control(speaker)
        runCurrent()
        speaker.commandFailure = unreachable
        speaker.pollFailure = unreachable
        vm.onNext()
        advanceTimeBy(20_000)
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem)
        speaker.pollFailure = null
        advanceTimeBy(5_100)
        assertEquals(Problem.None, vm.state.value.problem)
    }
}
