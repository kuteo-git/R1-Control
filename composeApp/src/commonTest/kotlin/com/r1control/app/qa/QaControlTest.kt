package com.r1control.app.qa

import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.NowPlaying
import com.r1control.app.domain.model.SpeakerSnapshot
import com.r1control.app.domain.model.Volume
import com.r1control.app.domain.model.VolumeScale
import com.r1control.app.domain.usecase.SendChat
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.testing.FakeSpeaker
import com.r1control.app.testing.media
import com.r1control.app.testing.musicStore
import com.r1control.app.testing.speakerSnapshot
import com.r1control.app.testing.track
import com.r1control.app.testing.unreachable
import com.r1control.app.domain.model.ChatMessage
import com.r1control.app.domain.model.Sender
import com.r1control.app.ui.Problem
import com.r1control.app.ui.chat.ChatViewModel
import com.r1control.app.ui.control.CardState
import com.r1control.app.ui.control.ControlViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A speaker whose reads take a set time each, and answer with what was true when the read began. */
private class SlowReadSpeaker : FakeSpeaker() {
    val readDelays = ArrayDeque<Long>()

    override suspend fun snapshot(): SpeakerSnapshot {
        val atStart = current
        snapshots++
        delay(readDelays.removeFirstOrNull() ?: 0L)
        pollFailure?.let { throw it }
        return atStart
    }
}

class QaControlTest {
    private val queue = listOf(track("a"), track("b"))
    private fun playing(volume: Int = 40) = speakerSnapshot(
        media = media(MediaStatus.Playing, videoId = "a", title = "Song", positionS = 15, durationS = 200, queue = queue),
    ).copy(volume = Volume(volume, VolumeScale(15)))

    private fun paused() = speakerSnapshot(
        media = media(MediaStatus.Paused, videoId = "a", title = "Song", positionS = 15, durationS = 200, queue = queue),
    )

    private fun TestScope.control(speaker: FakeSpeaker): ControlViewModel {
        val feed = SpeakerFeed(speaker, backgroundScope)
        val vm = ControlViewModel(feed, speaker, musicStore(speaker, feed, backgroundScope), backgroundScope)
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    private fun ControlViewModel.music(): NowPlaying.Music =
        assertIs<NowPlaying.Music>(assertIs<CardState.Showing>(state.value.card).nowPlaying)

    /**
     * The volume was set to 60 from the app, then changed on the speaker by voice
     * (or the web panel). Dragging back to 60 must reach the speaker again.
     */
    @Test fun aLevelSentBeforeIsSentAgainAfterTheSpeakerMovedAway() = runTest {
        val speaker = FakeSpeaker().apply { current = playing(volume = 40) }
        val vm = control(speaker)
        runCurrent()
        vm.onVolumeDrag(60)
        vm.onVolumeRelease()
        advanceTimeBy(2_000)
        assertEquals(listOf(60), speaker.volumes)

        speaker.current = playing(volume = 20)
        advanceTimeBy(1_100)
        assertEquals(20, vm.state.value.volume.pct, "the poll shows the speaker's new level")

        vm.onVolumeDrag(60)
        vm.onVolumeRelease()
        advanceTimeBy(2_000)
        assertEquals(listOf(60, 60), speaker.volumes, "the second drag to 60 was never sent")
    }

    @Test fun volumeSnapsToBothEdgesAndTheReleaseValueIsTheLastSent() = runTest {
        val speaker = FakeSpeaker().apply { current = playing() }
        val vm = control(speaker)
        runCurrent()
        vm.onVolumeDrag(2)
        runCurrent()
        assertEquals(0, vm.state.value.volume.pct)
        advanceTimeBy(300)
        vm.onVolumeDrag(50)
        vm.onVolumeDrag(98)
        vm.onVolumeRelease()
        advanceTimeBy(500)
        assertEquals(100, vm.state.value.volume.pct)
        assertEquals(0, speaker.volumes.first())
        assertEquals(100, speaker.volumes.last())
        assertTrue(speaker.volumes.all { it == VolumeScale(15).snap(it) }, "only levels the hardware can hold: ${speaker.volumes}")
    }

    @Test fun theSeekBarHoldsTheFingersPositionForAMomentAfterReleaseThenFollowsTheSpeaker() = runTest {
        val speaker = FakeSpeaker().apply { current = playing() }
        val vm = control(speaker)
        runCurrent()
        vm.onSeekDrag(120)
        vm.onSeekRelease()
        runCurrent()
        assertEquals(120, vm.music().positionS)
        advanceTimeBy(1_400)
        assertEquals(120, vm.music().positionS, "held while the in-flight poll is older than the seek")
        advanceTimeBy(200)
        assertEquals(15, vm.music().positionS, "then the speaker's reading again")
        assertEquals(1, speaker.calls.count { it.startsWith("seek") })
    }

    @Test fun aRecoveredPollClearsTheBannerAndShowsTheNewCard() = runTest {
        val speaker = FakeSpeaker().apply { current = playing() }
        val vm = control(speaker)
        runCurrent()
        speaker.pollFailure = unreachable
        advanceTimeBy(1_100)
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem)

        speaker.pollFailure = null
        speaker.current = paused()
        advanceTimeBy(5_100)
        assertEquals(Problem.None, vm.state.value.problem)
        assertEquals(NowPlaying.Phase.Paused, vm.music().phase)
    }

    /**
     * The Control tab's banner has no close button (ControlScreen.kt:101), so a
     * failed command must stop being shown once the speaker answers again.
     */
    @Test fun aFailedCommandsBannerGoesAwayOnceTheSpeakerAnswersAgain() = runTest {
        val speaker = FakeSpeaker().apply { current = playing() }
        val vm = control(speaker)
        runCurrent()
        speaker.commandFailure = unreachable
        vm.onNext()
        runCurrent()
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem)

        speaker.commandFailure = null
        advanceTimeBy(30_000)
        assertEquals(Problem.None, vm.state.value.problem, "30 s of good readings later the banner still says unreachable")
    }

    /** A poll that left before the pause and lands after it must not put the old card back. */
    @Test fun aReadingStartedBeforeACommandDoesNotUndoIt() = runTest {
        val speaker = SlowReadSpeaker().apply { current = playing() }
        val vm = control(speaker)
        runCurrent()
        assertEquals(NowPlaying.Phase.Playing, vm.music().phase)

        speaker.readDelays.addAll(listOf(400L, 0L))
        advanceTimeBy(1_050)
        speaker.current = paused()
        vm.onPlayPause()
        runCurrent()
        assertEquals(NowPlaying.Phase.Paused, vm.music().phase)

        advanceTimeBy(400)
        assertEquals(NowPlaying.Phase.Paused, vm.music().phase, "the slow poll put Playing back on the card")
    }
}

class QaChatTest {
    private fun TestScope.chat(speaker: FakeSpeaker): ChatViewModel {
        val vm = ChatViewModel(SpeakerFeed(speaker, backgroundScope), SendChat(speaker), backgroundScope)
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    private val lines = listOf(
        ChatMessage(Sender.User, "Bật nhạc Thanh Ngân", 1),
        ChatMessage(Sender.Assistant, "Đang tải bài Thành Tâm Sám Hối của NSƯT Thanh Ngân", 2),
    )

    @Test fun aDroppedPollKeepsTheConversationAndRecoveryClearsTheBanner() = runTest {
        val speaker = FakeSpeaker().apply { current = speakerSnapshot(chat = lines) }
        val vm = chat(speaker)
        runCurrent()
        speaker.pollFailure = unreachable
        advanceTimeBy(3_100)
        assertEquals(lines, vm.current().messages)
        assertEquals(Problem.Failed(unreachable), vm.current().problem)

        speaker.pollFailure = null
        advanceTimeBy(5_100)
        assertEquals(Problem.None, vm.current().problem)
        assertEquals(lines, vm.current().messages)
    }

    @Test fun vietnameseIsSentTrimmedAndExactlyAsTyped() = runTest {
        val speaker = FakeSpeaker()
        val vm = chat(speaker)
        vm.onDraft("  Mấy giờ rồi?  ")
        vm.send()
        runCurrent()
        assertEquals(listOf("say Mấy giờ rồi?"), speaker.calls)
        assertEquals("", vm.draft.value)
    }

    /** The chat banner has no close button either (ChatScreen.kt:74). */
    @Test fun aFailedSendsBannerGoesAwayOnceTheSpeakerAnswersAgain() = runTest {
        val speaker = FakeSpeaker().apply { current = speakerSnapshot(chat = lines) }
        val vm = chat(speaker)
        runCurrent()
        speaker.commandFailure = unreachable
        vm.onDraft("xin chào")
        vm.send()
        runCurrent()
        assertEquals(Problem.Failed(unreachable), vm.current().sendProblem)

        speaker.commandFailure = null
        advanceTimeBy(30_000)
        assertEquals(Problem.None, vm.current().sendProblem, "30 s of good readings later the banner still says unreachable")
    }
}
