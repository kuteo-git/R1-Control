package com.r1control.app.domain

import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.NewsTime
import com.r1control.app.domain.model.NowPlaying
import com.r1control.app.domain.model.coverAtSize
import com.r1control.app.domain.model.formatDb
import com.r1control.app.domain.model.formatHz
import com.r1control.app.domain.usecase.PollResult
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.domain.usecase.SpeakerView
import com.r1control.app.domain.usecase.Transport
import com.r1control.app.domain.usecase.after
import com.r1control.app.domain.usecase.CommandOutcome
import com.r1control.app.testing.FakeSpeaker
import com.r1control.app.testing.media
import com.r1control.app.testing.speakerSnapshot
import com.r1control.app.testing.unreachable
import com.r1control.app.ui.Held
import com.r1control.app.ui.HeldValue
import com.r1control.app.ui.or
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class NewsTimeTest {
    @Test fun parsesAndFormatsWithTwoDigits() {
        assertEquals(NewsTime(9, 5), NewsTime.parse("9:05"))
        assertEquals("09:05", NewsTime.parse("09:05").toString())
    }

    @Test fun badTextIsTheDefaultNotACrash() {
        listOf("", "25:00", "10:61", "ten", "10", "1:2:3").forEach { assertEquals(NewsTime.Default, NewsTime.parse(it), it) }
    }

    @Test fun stepsWrapWithoutCarrying() {
        assertEquals(NewsTime(0, 30), NewsTime(23, 30).plusHours(1))
        assertEquals(NewsTime(23, 30), NewsTime(0, 30).plusHours(-1))
        assertEquals(NewsTime(7, 0), NewsTime(7, 55).plusMinutes(5))
        assertEquals(NewsTime(7, 55), NewsTime(7, 0).plusMinutes(-5))
    }

    @Test fun aTimeOfDayCannotBeBuiltOutOfRange() {
        assertFailsWith<IllegalArgumentException> { NewsTime(24, 0) }
    }
}

class FormatTest {
    @Test fun decibels() {
        assertEquals("0 dB", formatDb(0))
        assertEquals("10 dB", formatDb(1000))
        assertEquals("-6 dB", formatDb(-600))
        assertEquals("+8 dB", formatDb(800, signed = true))
        assertEquals("0 dB", formatDb(0, signed = true))
        assertEquals("-1.5 dB", formatDb(-150))
        assertEquals("-0.5 dB", formatDb(-50))
        assertEquals("2.5 dB", formatDb(250))
    }

    @Test fun frequencies() {
        assertEquals("60 Hz", formatHz(60))
        assertEquals("910 Hz", formatHz(910))
        assertEquals("1 kHz", formatHz(1000))
        assertEquals("3.6 kHz", formatHz(3600))
        assertEquals("14 kHz", formatHz(14000))
    }

    @Test fun coverSizeIsRewrittenOnlyWhereTheHostTakesIt() {
        assertEquals(
            "https://yt3.googleusercontent.com/abc=w640-h640-l90-rj",
            coverAtSize("https://yt3.googleusercontent.com/abc=w120-h120-l90-rj", 640),
        )
        assertEquals("https://i.ytimg.com/vi/x/mqdefault.jpg", coverAtSize("https://i.ytimg.com/vi/x/mqdefault.jpg", 640))
        assertEquals("", coverAtSize("", 640))
    }
}

class TransportTest {
    @Test fun toggleDependsOnThePhase() = runTest {
        val s = FakeSpeaker()
        val t = Transport(s)
        t.toggle(NowPlaying.Phase.Playing)
        t.toggle(NowPlaying.Phase.Paused)
        assertEquals(CommandOutcome.Done, t.toggle(NowPlaying.Phase.Downloading(10)))
        assertEquals(listOf("pause", "resume"), s.calls)
    }

    @Test fun seekNeverGoesBelowZeroAndFailuresAreOutcomes() = runTest {
        val s = FakeSpeaker()
        val t = Transport(s)
        t.seek(-5)
        assertEquals(listOf("seek 0"), s.calls)
        s.commandFailure = unreachable
        assertEquals(CommandOutcome.Failed(unreachable), t.stop())
    }
}

class SpeakerFeedTest {
    @Test fun afterIsAPureTransition() {
        val snap = speakerSnapshot()
        val online = SpeakerView.Connecting.after(PollResult.Fresh(snap))
        assertEquals(SpeakerView.Online(snap), online)
        assertEquals(SpeakerView.Stale(snap, unreachable), online.after(PollResult.Failed(unreachable)))
        assertEquals(SpeakerView.Stale(snap, unreachable), online.after(PollResult.Failed(unreachable)).after(PollResult.Failed(unreachable)))
        assertEquals(SpeakerView.Offline(unreachable), SpeakerView.Connecting.after(PollResult.Failed(unreachable)))
        assertEquals(SpeakerView.Online(snap), SpeakerView.Offline(unreachable).after(PollResult.Fresh(snap)))
    }

    @Test fun pollsOnlyWhileSomebodyWatchesAndStopsShortlyAfter() = runTest {
        val s = FakeSpeaker()
        val feed = SpeakerFeed(s, backgroundScope)
        runCurrent()
        assertEquals(0, s.snapshots, "nobody collects, nobody polls")

        val watcher = backgroundScope.launch { feed.view.collect {} }
        runCurrent()
        assertEquals(1, s.snapshots)
        advanceTimeBy(3_001)
        assertEquals(2, s.snapshots, "idle speaker, every 3 s")

        watcher.cancel()
        advanceTimeBy(3_500)
        val whileDraining = s.snapshots
        advanceTimeBy(10_000)
        assertEquals(whileDraining, s.snapshots, "stopped once the grace period is over")
    }

    @Test fun aQuickTabSwitchDoesNotRestartThePolling() = runTest {
        val s = FakeSpeaker()
        val feed = SpeakerFeed(s, backgroundScope)
        val first = backgroundScope.launch { feed.view.collect {} }
        runCurrent()
        first.cancel()
        advanceTimeBy(500)
        backgroundScope.launch { feed.view.collect {} }
        advanceTimeBy(500)
        assertEquals(1, s.snapshots)
    }

    @Test fun refreshReadsNowAndPublishesIt() = runTest {
        val s = FakeSpeaker()
        val feed = SpeakerFeed(s, backgroundScope)
        backgroundScope.launch { feed.view.collect {} }
        runCurrent()
        s.current = speakerSnapshot(media = media(MediaStatus.Paused, "a", "Song"))
        feed.refresh()
        runCurrent()
        val view = assertIs<SpeakerView.Online>(feed.view.value)
        assertEquals("Song", view.snapshot.media.title)
    }

    @Test fun aNewAddressDropsTheOldSpeakersReadingAndReadsTheNewOne() = runTest {
        val s = FakeSpeaker().apply { current = speakerSnapshot(media = media(MediaStatus.Playing, "a", "Old speaker's song")) }
        val feed = SpeakerFeed(s, backgroundScope)
        backgroundScope.launch { feed.view.collect {} }
        runCurrent()
        s.current = speakerSnapshot()
        feed.restart()
        assertEquals(SpeakerView.Connecting, feed.view.value, "the old reading is not shown as the new speaker's")
        runCurrent()
        assertEquals(SpeakerView.Online(speakerSnapshot()), feed.view.value)
    }

    @Test fun aReadingThatLeftBeforeARestartIsDropped() = runTest {
        val s = object : FakeSpeaker() {
            var slow = false
            override suspend fun snapshot(): com.r1control.app.domain.model.SpeakerSnapshot {
                val answer = current
                if (slow) kotlinx.coroutines.delay(2_000)
                return answer
            }
        }
        s.current = speakerSnapshot(media = media(MediaStatus.Playing, "a", "Old"))
        val feed = SpeakerFeed(s, backgroundScope)
        backgroundScope.launch { feed.view.collect {} }
        runCurrent()
        s.slow = true
        advanceTimeBy(1_100)
        s.current = speakerSnapshot()
        feed.restart()
        advanceTimeBy(1_950)
        assertEquals(SpeakerView.Connecting, feed.view.value, "the old speaker's answer landed first and was dropped")
        advanceTimeBy(100)
        assertEquals(SpeakerView.Online(speakerSnapshot()), feed.view.value, "then the new speaker's own answer")
    }

    @Test fun aFailedRefreshIsLeftToTheNextPoll() = runTest {
        val s = FakeSpeaker()
        val feed = SpeakerFeed(s, backgroundScope)
        backgroundScope.launch { feed.view.collect {} }
        runCurrent()
        s.pollFailure = unreachable
        feed.refresh()
        runCurrent()
        assertIs<SpeakerView.Online>(feed.view.value)
    }
}

class HeldValueTest {
    @Test fun holdsAfterReleaseForTheGracePeriodOnly() = runTest {
        val held = HeldValue<Int>(backgroundScope, 1_500)
        assertEquals(40, held.flow.value.or(40))
        held.hold(70)
        assertEquals(70, held.flow.value.or(40))
        held.releaseSoon()
        advanceTimeBy(1_400)
        assertEquals(70, held.flow.value.or(40))
        advanceTimeBy(200)
        assertEquals(Held.Free, held.flow.value)
        assertEquals(40, held.flow.value.or(40))
    }

    @Test fun aNewDragCancelsAPendingRelease() = runTest {
        val held = HeldValue<Int>(backgroundScope, 1_500)
        held.hold(70)
        held.releaseSoon()
        advanceTimeBy(1_000)
        held.hold(80)
        advanceTimeBy(2_000)
        assertEquals(80, held.flow.value.or(40), "still the finger's: it never released")
    }
}
