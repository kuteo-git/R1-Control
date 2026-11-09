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

class MiniPlayerFailureTest {
    private val playing = speakerSnapshot(media = media(MediaStatus.Playing, "a", "Song", 1, 100, queue = listOf(track("a"))))

    private fun TestScope.mini(speaker: FakeSpeaker): MiniPlayerViewModel {
        val feed = SpeakerFeed(speaker, backgroundScope)
        val vm = MiniPlayerViewModel(feed, speaker, musicStore(speaker, feed, backgroundScope), backgroundScope)
        backgroundScope.launch { vm.state.collect {} }
        return vm
    }

    @Test fun aFailedPauseIsShownOnTheBarAndGoesOnceTheSpeakerAnswers() = runTest {
        val speaker = FakeSpeaker().apply { current = playing }
        val vm = mini(speaker)
        runCurrent()
        speaker.commandFailure = unreachable
        vm.onPlayPause()
        runCurrent()
        assertEquals(listOf("pause"), speaker.calls)
        assertEquals(Problem.Failed(unreachable), assertIs<MiniState.Showing>(vm.state.value).problem)

        speaker.commandFailure = null
        advanceTimeBy(10_000)
        assertEquals(Problem.None, assertIs<MiniState.Showing>(vm.state.value).problem)
    }

    @Test fun aDownloadingSongIsShownAsDownloading() = runTest {
        val speaker = FakeSpeaker().apply {
            current = speakerSnapshot(media = media(MediaStatus.Downloading, "a", "Song", downloadPercent = 40))
        }
        val vm = mini(speaker)
        runCurrent()
        assertEquals(NowPlaying.Phase.Downloading(40), assertIs<MiniState.Showing>(vm.state.value).phase)
    }
}
