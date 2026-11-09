package com.r1control.app.ui

import com.r1control.app.domain.model.ChatMessage
import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.Sender
import com.r1control.app.domain.usecase.SendChat
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.testing.FakeSpeaker
import com.r1control.app.testing.media
import com.r1control.app.testing.musicStore
import com.r1control.app.testing.speakerSnapshot
import com.r1control.app.testing.track
import com.r1control.app.testing.unreachable
import com.r1control.app.ui.chat.ChatLog
import com.r1control.app.ui.chat.ChatViewModel
import com.r1control.app.ui.music.MiniPlayerViewModel
import com.r1control.app.ui.music.MiniState
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChatViewModelTest {
    private val hello = ChatMessage(Sender.User, "hi", 1)
    private val reply = ChatMessage(Sender.Assistant, "hello", 2)

    private fun TestScope.chat(speaker: FakeSpeaker): ChatViewModel {
        val vm = ChatViewModel(SpeakerFeed(speaker, backgroundScope), SendChat(speaker), backgroundScope)
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    @Test fun isAskingUntilTheFirstReadingSoEmptyIsNotShownForStillAsking() = runTest {
        val vm = chat(FakeSpeaker())
        assertEquals(ChatLog.Asking, vm.current().log)
        runCurrent()
        assertEquals(ChatLog.Lines(emptyList()), vm.current().log)
    }

    /** Opening the tab flashed a spinner: the screen started from "asking" although the feed already held the log. */
    @Test fun aTabOpenedAfterTheFeedKnowsStartsFromTheLogNotFromAsking() = runTest {
        val speaker = FakeSpeaker().apply { current = speakerSnapshot(chat = listOf(hello)) }
        val feed = SpeakerFeed(speaker, backgroundScope)
        val vm = ChatViewModel(feed, SendChat(speaker), backgroundScope)
        backgroundScope.launch { feed.view.collect {} }
        runCurrent()
        assertEquals(ChatLog.Lines(listOf(hello)), vm.current().log, "no collector of the chat itself yet")
    }

    @Test fun showsTheConversationOldestFirstAndFollowsNewOnes() = runTest {
        val speaker = FakeSpeaker().apply { current = speakerSnapshot(chat = listOf(hello)) }
        val vm = chat(speaker)
        runCurrent()
        assertEquals(listOf(hello), vm.current().messages)
        speaker.current = speakerSnapshot(chat = listOf(hello, reply))
        advanceTimeBy(3_100)
        assertEquals(listOf(hello, reply), vm.current().messages)
    }

    @Test fun sendingClearsTheFieldAndSendsTheTrimmedLine() = runTest {
        val speaker = FakeSpeaker()
        val vm = chat(speaker)
        vm.onDraft("  bật nhạc  ")
        vm.send()
        assertEquals("", vm.draft.value)
        runCurrent()
        assertEquals(listOf("say bật nhạc"), speaker.calls)
    }

    @Test fun aBlankDraftIsNotSentAndStaysInTheField() = runTest {
        val speaker = FakeSpeaker()
        val vm = chat(speaker)
        vm.onDraft("   ")
        vm.send()
        runCurrent()
        assertTrue(speaker.calls.isEmpty())
        assertEquals("   ", vm.draft.value)
    }

    @Test fun aRefusedMessageComesBackToTheFieldWithABanner() = runTest {
        val speaker = FakeSpeaker().apply { commandFailure = unreachable }
        val vm = chat(speaker)
        runCurrent()
        vm.onDraft("hello")
        vm.send()
        runCurrent()
        assertEquals("hello", vm.draft.value)
        assertEquals(Problem.Failed(unreachable), vm.current().sendProblem)
    }

    /**
     * B-03: with the keyboard up the only sign of a failed send was a banner at the top of the page.
     * The failure is the send's own, shown at the field, even while readings fail too.
     */
    @Test fun aFailedSendIsItsOwnProblemEvenWhileReadingsFail() = runTest {
        val speaker = FakeSpeaker().apply { current = speakerSnapshot(chat = listOf(hello)) }
        val vm = chat(speaker)
        runCurrent()
        speaker.pollFailure = unreachable
        speaker.commandFailure = unreachable
        advanceTimeBy(3_100)
        vm.onDraft("offline test two")
        vm.send()
        runCurrent()
        assertEquals(Problem.Failed(unreachable), vm.current().sendProblem)
        assertEquals("offline test two", vm.draft.value)
    }

    /** T-14 / U-06: "still asking" and "cannot ask" looked the same, a spinner for ever. */
    @Test fun aSpeakerUnreachableFromTheStartIsTheFailureInPlaceOfTheSpinner() = runTest {
        val vm = chat(FakeSpeaker().apply { pollFailure = unreachable })
        runCurrent()
        assertEquals(ChatLog.Unreachable(unreachable), vm.current().log)
        assertEquals(Problem.None, vm.current().problem, "the failure is shown once, in place of the list")
        assertFalse(vm.current().canSend)
    }

    @Test fun tryAgainReadsAtOnceAndTheConversationReplacesTheFailure() = runTest {
        val speaker = FakeSpeaker().apply { pollFailure = unreachable }
        val vm = chat(speaker)
        runCurrent()
        speaker.pollFailure = null
        speaker.current = speakerSnapshot(chat = listOf(hello))
        vm.retry()
        runCurrent()
        assertEquals(ChatLog.Lines(listOf(hello)), vm.current().log)
        assertTrue(vm.current().canSend)
    }

    /** The shared CommandProblem rule: up for at least 4 s, then gone at the first reading after that. */
    @Test fun aFailedSendStaysReadableThenGoesWithTheNextReading() = runTest {
        val speaker = FakeSpeaker().apply { current = speakerSnapshot(chat = listOf(hello)) }
        val vm = chat(speaker)
        runCurrent()
        speaker.commandFailure = unreachable
        vm.onDraft("xin chào")
        vm.send()
        runCurrent()
        assertEquals(Problem.Failed(unreachable), vm.current().sendProblem)

        speaker.commandFailure = null
        speaker.current = speakerSnapshot(chat = listOf(hello, reply))
        advanceTimeBy(3_500)
        assertEquals(Problem.Failed(unreachable), vm.current().sendProblem, "a reading came back, but too soon to have read the banner")
        advanceTimeBy(2_700)
        assertEquals(Problem.None, vm.current().sendProblem)
        assertEquals("xin chào", vm.draft.value, "the unsent text stays in the field")
    }

    @Test fun aFailedSendOutlastsAFailingPollAndGoesWithTheSpeaker() = runTest {
        val speaker = FakeSpeaker().apply { current = speakerSnapshot(chat = listOf(hello)) }
        val vm = chat(speaker)
        runCurrent()
        val refused = com.r1control.app.domain.repository.SpeakerException.Refused("busy")
        speaker.commandFailure = refused
        vm.onDraft("xin chào")
        vm.send()
        runCurrent()
        assertEquals(Problem.Failed(refused), vm.current().sendProblem)

        speaker.pollFailure = unreachable
        advanceTimeBy(3_100)
        assertEquals(Problem.Failed(unreachable), vm.current().problem)
        assertEquals(Problem.Failed(refused), vm.current().sendProblem)
        speaker.pollFailure = null
        advanceTimeBy(5_100)
        assertEquals(Problem.None, vm.current().problem)
        assertEquals(Problem.None, vm.current().sendProblem, "the old refusal does not stay once the speaker answers")
    }
}

class MiniPlayerViewModelTest {
    private val queue = listOf(track("a"))

    private fun TestScope.mini(speaker: FakeSpeaker): MiniPlayerViewModel {
        val feed = SpeakerFeed(speaker, backgroundScope)
        val vm = MiniPlayerViewModel(feed, speaker, musicStore(speaker, feed, backgroundScope), backgroundScope)
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    @Test fun hiddenWhenNothingPlaysShownWhenMusicDoes() = runTest {
        val speaker = FakeSpeaker()
        val vm = mini(speaker)
        runCurrent()
        assertEquals(MiniState.Hidden, vm.state.value)

        speaker.current = speakerSnapshot(media = media(MediaStatus.Playing, "a", "Song", 1, 100, queue = queue))
        advanceTimeBy(3_100)
        val shown = assertIs<MiniState.Showing>(vm.state.value)
        assertEquals("Song", shown.title)
        assertTrue(shown.playing)
    }

    @Test fun toggleFollowsThePhaseAndAPausedSongIsShownAsNotPlaying() = runTest {
        val speaker = FakeSpeaker().apply {
            current = speakerSnapshot(media = media(MediaStatus.Paused, "a", "Song", 1, 100, queue = queue))
        }
        val vm = mini(speaker)
        runCurrent()
        assertEquals(false, assertIs<MiniState.Showing>(vm.state.value).playing)
        vm.onPlayPause()
        runCurrent()
        assertEquals(listOf("resume"), speaker.calls)
    }

    @Test fun aDownloadingSongCannotBeToggled() = runTest {
        val speaker = FakeSpeaker().apply {
            current = speakerSnapshot(media = media(MediaStatus.Downloading, "a", "Song", downloadPercent = 10))
        }
        val vm = mini(speaker)
        runCurrent()
        assertEquals(false, assertIs<MiniState.Showing>(vm.state.value).canToggle)
        vm.onPlayPause()
        runCurrent()
        assertTrue(speaker.calls.isEmpty())
    }

    @Test fun nextIsDrawnOnlyWhenTheQueueHasOneAndTheSongHasStarted() = runTest {
        val speaker = FakeSpeaker().apply {
            current = speakerSnapshot(media = media(MediaStatus.Playing, "a", "Song", 1, 100, queue = listOf(track("a"), track("b"))))
        }
        val vm = mini(speaker)
        runCurrent()
        assertTrue(assertIs<MiniState.Showing>(vm.state.value).canNext)

        speaker.current = speakerSnapshot(media = media(MediaStatus.Playing, "b", "Song", 1, 100, queue = listOf(track("a"), track("b"))))
        advanceTimeBy(3_100)
        assertEquals(false, assertIs<MiniState.Showing>(vm.state.value).canNext, "the last song has no next")

        speaker.current = speakerSnapshot(media = media(MediaStatus.Downloading, "a", "Song", downloadPercent = 10, queue = listOf(track("a"), track("b"))))
        advanceTimeBy(3_100)
        assertEquals(false, assertIs<MiniState.Showing>(vm.state.value).canNext, "nothing to skip while it is still downloading")
    }

    @Test fun nextAsksTheSpeakerForTheNextSongAndAnAbsentNextAsksNothing() = runTest {
        val speaker = FakeSpeaker().apply {
            current = speakerSnapshot(media = media(MediaStatus.Playing, "a", "Song", 1, 100, queue = listOf(track("a"), track("b"))))
        }
        val vm = mini(speaker)
        runCurrent()
        vm.onNext()
        runCurrent()
        assertEquals(listOf("next"), speaker.calls)

        speaker.calls.clear()
        speaker.current = speakerSnapshot(media = media(MediaStatus.Playing, "b", "Song", 1, 100, queue = listOf(track("a"), track("b"))))
        advanceTimeBy(3_100)
        vm.onNext()
        runCurrent()
        assertTrue(speaker.calls.isEmpty())
    }

    @Test fun thePositionAndLengthTravelWithEveryReading() = runTest {
        val speaker = FakeSpeaker().apply {
            current = speakerSnapshot(media = media(MediaStatus.Playing, "a", "Song", 83, 245, queue = queue))
        }
        val vm = mini(speaker)
        runCurrent()
        val shown = assertIs<MiniState.Showing>(vm.state.value)
        assertEquals(83, shown.positionS)
        assertEquals(245, shown.durationS)

        speaker.current = speakerSnapshot(media = media(MediaStatus.Playing, "a", "Song", 86, 245, queue = queue))
        advanceTimeBy(3_100)
        assertEquals(86, assertIs<MiniState.Showing>(vm.state.value).positionS)
    }
}
