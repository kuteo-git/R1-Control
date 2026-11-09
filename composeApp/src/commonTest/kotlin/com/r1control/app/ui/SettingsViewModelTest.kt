package com.r1control.app.ui

import com.r1control.app.domain.model.TtsVoices
import com.r1control.app.testing.FakeSpeaker
import com.r1control.app.testing.FakeVoices
import com.r1control.app.testing.audioSettings
import com.r1control.app.testing.bluetooth
import com.r1control.app.testing.device
import com.r1control.app.testing.speakerSettings
import com.r1control.app.testing.unreachable
import com.r1control.app.ui.settings.AdvancedViewModel
import com.r1control.app.ui.settings.AudioViewModel
import com.r1control.app.ui.settings.BluetoothViewModel
import com.r1control.app.ui.settings.ForgetAsk
import com.r1control.app.ui.settings.NewsAction
import com.r1control.app.ui.settings.NewsViewModel
import com.r1control.app.ui.settings.RestartStep
import com.r1control.app.ui.settings.VoiceList
import com.r1control.app.ui.settings.VoiceViewModel
import com.r1control.app.ui.settings.title
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AudioViewModelTest {
    private fun speaker() = FakeSpeaker().apply { settings = speakerSettings() }

    @Test fun loadsTheAudioSettings() = runTest {
        val vm = AudioViewModel(speaker(), backgroundScope)
        assertEquals(Loadable.Loading, vm.state.value.audio)
        runCurrent()
        assertEquals(audioSettings(), assertIs<Loadable.Loaded<*>>(vm.state.value.audio).value)
    }

    @Test fun aFailedLoadIsShownAndCanBeRetried() = runTest {
        val s = speaker().apply { pollFailure = unreachable }
        val vm = AudioViewModel(s, backgroundScope)
        runCurrent()
        assertEquals(Loadable.Failed(unreachable), vm.state.value.audio)
        s.pollFailure = null
        vm.load()
        runCurrent()
        assertIs<Loadable.Loaded<*>>(vm.state.value.audio)
    }

    @Test fun dragsMoveTheScreenAtOnceAndWriteOnceAfterTheFingerPauses() = runTest {
        val s = speaker()
        val vm = AudioViewModel(s, backgroundScope, debounceMillis = 400)
        runCurrent()
        listOf(1200, 1300, 1400, 1550).forEach { vm.onLoudness(it); advanceTimeBy(100) }
        assertEquals(1500, (vm.state.value.audio as Loadable.Loaded).value.loudnessMb, "snapped to steps of 100")
        assertTrue(s.calls.none { it.startsWith("loudness") }, "nothing written while the finger moves")
        advanceTimeBy(500)
        assertEquals(listOf("loudness 1500"), s.calls)
    }

    @Test fun liftingTheFingerWritesAtOnceSoLeavingThePageLosesNothing() = runTest {
        val s = speaker()
        val vm = AudioViewModel(s, backgroundScope)
        runCurrent()
        vm.onLoudness(1800)
        vm.onLoudnessDone()
        runCurrent()
        assertEquals(listOf("loudness 1800"), s.calls)
        advanceTimeBy(1_000)
        assertEquals(1, s.calls.size, "the debounced write is cancelled, not repeated")
    }

    @Test fun loudnessStaysInItsRange() = runTest {
        val vm = AudioViewModel(speaker(), backgroundScope)
        runCurrent()
        vm.onLoudness(9_999)
        assertEquals(2000, (vm.state.value.audio as Loadable.Loaded).value.loudnessMb)
        vm.onLoudness(-500)
        assertEquals(0, (vm.state.value.audio as Loadable.Loaded).value.loudnessMb)
    }

    @Test fun theEqualiserSwitchWritesAtOnce() = runTest {
        val s = speaker()
        val vm = AudioViewModel(s, backgroundScope)
        runCurrent()
        vm.onEqEnabled(false)
        runCurrent()
        assertEquals(listOf("eq false"), s.calls)
        assertEquals(false, (vm.state.value.audio as Loadable.Loaded).value.eqEnabled)
    }

    @Test fun aBandWritesEveryBandsGainInOrderAndStaysInRange() = runTest {
        val s = speaker()
        val vm = AudioViewModel(s, backgroundScope)
        runCurrent()
        vm.onBand(0, 5_000)
        vm.onBandDone()
        runCurrent()
        assertEquals(listOf("bands [1500, 100]"), s.calls)
    }

    private fun AudioViewModel.audio() = (state.value.audio as Loadable.Loaded).value

    /** B-05: the switch stayed Off with the R1 still on, so the page said the equaliser was off. */
    @Test fun aFailedWriteIsABannerAndTheControlGoesBackToTheSpeakersValue() = runTest {
        val s = speaker()
        val vm = AudioViewModel(s, backgroundScope)
        runCurrent()
        s.commandFailure = unreachable
        vm.onEqEnabled(false)
        runCurrent()
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem)
        assertEquals(true, vm.audio().eqEnabled)
        vm.onLoudness(1800)
        vm.onLoudnessDone()
        runCurrent()
        assertEquals(1000, vm.audio().loudnessMb)
        vm.onBand(0, 700)
        vm.onBandDone()
        runCurrent()
        assertEquals(listOf(0, 100), vm.audio().bands.map { it.gainMb })
    }

    /** B-06: the page read the R1 once, on opening, and showed 15 dB for good after it went back to 10. */
    @Test fun theOpenPageFollowsTheSpeaker() = runTest {
        val s = speaker()
        val vm = AudioViewModel(s, backgroundScope)
        runCurrent()
        s.settings = speakerSettings(audio = audioSettings(loudness = 1500, eq = false))
        advanceTimeBy(3_001)
        runCurrent()
        assertEquals(1500, vm.audio().loudnessMb)
        assertEquals(false, vm.audio().eqEnabled)
    }

    @Test fun aReadingDoesNotMoveASliderUnderTheFingerOrOneJustSet() = runTest {
        val s = speaker()
        val vm = AudioViewModel(s, backgroundScope)
        runCurrent()
        vm.onLoudness(1800)
        advanceTimeBy(3_001)
        runCurrent()
        assertEquals(1800, vm.audio().loudnessMb, "a reading moved the slider under the finger")
        vm.onLoudnessDone()
        runCurrent()
        advanceTimeBy(1_000)
        assertEquals(1800, vm.audio().loudnessMb, "the value just set was replaced by an older reading")
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1000, vm.audio().loudnessMb, "after the hold the page shows what the speaker has")
    }
}

class VoiceViewModelTest {
    private fun speaker(host: String = "http://tts") = FakeSpeaker().apply { settings = speakerSettings(ttsHost = host) }

    @Test fun listsTheServersVoicesWithTheCurrentOne() = runTest {
        val voices = FakeVoices(TtsVoices("b", listOf("a", "b", "c")))
        val vm = VoiceViewModel(speaker(), voices, backgroundScope)
        runCurrent()
        val list = assertIs<VoiceList.Voices>((vm.state.value.content as Loadable.Loaded).value)
        assertEquals("b", list.voices.current)
        assertEquals("http://tts", voices.askedHost)
    }

    @Test fun noVoiceServerIsSaidNotFailed() = runTest {
        val vm = VoiceViewModel(speaker(host = ""), FakeVoices(), backgroundScope)
        runCurrent()
        assertEquals(VoiceList.NoServer, (vm.state.value.content as Loadable.Loaded).value)
    }

    @Test fun choosingMovesTheCheckAndTellsTheServer() = runTest {
        val voices = FakeVoices(TtsVoices("a", listOf("a", "b")))
        val vm = VoiceViewModel(speaker(), voices, backgroundScope)
        runCurrent()
        vm.choose("b")
        runCurrent()
        assertEquals(listOf("b"), voices.chosen)
        assertEquals("b", assertIs<VoiceList.Voices>((vm.state.value.content as Loadable.Loaded).value).voices.current)
    }

    @Test fun choosingTheCurrentVoiceSendsNothing() = runTest {
        val voices = FakeVoices(TtsVoices("a", listOf("a", "b")))
        val vm = VoiceViewModel(speaker(), voices, backgroundScope)
        runCurrent()
        vm.choose("a")
        runCurrent()
        assertTrue(voices.chosen.isEmpty())
    }

    @Test fun aChoiceOvertakenWhileItWaitsIsNeverSent() = runTest {
        val slow = object : com.r1control.app.domain.repository.VoiceRepository {
            val sent = mutableListOf<String>()
            override suspend fun voices(ttsHost: String) = TtsVoices("a", listOf("a", "b", "c", "d"))
            override suspend fun setVoice(ttsHost: String, name: String) {
                kotlinx.coroutines.delay(500)
                sent += name
            }
        }
        val vm = VoiceViewModel(speaker(), slow, backgroundScope)
        runCurrent()
        vm.choose("b")
        advanceTimeBy(100)
        vm.choose("c")
        vm.choose("d")
        advanceTimeBy(5_000)
        assertEquals(listOf("b", "d"), slow.sent)
        assertEquals("d", assertIs<VoiceList.Voices>((vm.state.value.content as Loadable.Loaded).value).voices.current)
    }

    @Test fun aRefusalPutsTheCheckBack() = runTest {
        val voices = FakeVoices(TtsVoices("a", listOf("a", "b")))
        val vm = VoiceViewModel(speaker(), voices, backgroundScope)
        runCurrent()
        voices.failure = unreachable
        vm.choose("b")
        runCurrent()
        assertEquals("a", assertIs<VoiceList.Voices>((vm.state.value.content as Loadable.Loaded).value).voices.current)
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem)
    }
}

class NewsViewModelTest {
    private fun speaker() = FakeSpeaker().apply { settings = speakerSettings() }

    private fun NewsViewModel.news() = (state.value.draft as Loadable.Loaded).value.news

    @Test fun loadsTheBulletinAndOffersTheServersVoices() = runTest {
        val vm = NewsViewModel(speaker(), FakeVoices(TtsVoices("b", listOf("a", "b"))), backgroundScope)
        runCurrent()
        assertEquals("09:15", vm.news().time)
        assertEquals(listOf("a", "b"), (vm.state.value.draft as Loadable.Loaded).value.voices)
    }

    @Test fun aCurrentVoiceTheServerDoesNotListIsStillOffered() = runTest {
        val vm = NewsViewModel(speaker(), FakeVoices(TtsVoices("x", listOf("a"))), backgroundScope)
        runCurrent()
        assertEquals(listOf("b", "a"), (vm.state.value.draft as Loadable.Loaded).value.voices)
    }

    @Test fun anUnreachableVoiceServerStillLetsTheBulletinBeEdited() = runTest {
        val vm = NewsViewModel(speaker(), FakeVoices(failure = unreachable), backgroundScope)
        runCurrent()
        assertEquals(listOf("b"), (vm.state.value.draft as Loadable.Loaded).value.voices)
    }

    @Test fun steppersWrapAndKeepTwoDigits() = runTest {
        val vm = NewsViewModel(speaker(), FakeVoices(), backgroundScope)
        runCurrent()
        vm.onHour(15)
        assertEquals("00:15", vm.news().time)
        vm.onHour(-1)
        assertEquals("23:15", vm.news().time)
        vm.onMinute(-5)
        vm.onMinute(-5)
        vm.onMinute(-5)
        vm.onMinute(-5)
        assertEquals("23:55", vm.news().time)
    }

    @Test fun aDropReordersTheCategoriesAndSavesTheBulletinOnce() = runTest {
        val s = speaker()
        val vm = NewsViewModel(s, FakeVoices(), backgroundScope)
        runCurrent()
        vm.onCategory("power", true)
        runCurrent()
        s.calls.clear()
        assertTrue(vm.news().categories.last().enabled)
        vm.onMoved(2, 1)
        runCurrent()
        assertEquals(listOf("tech", "power", "world"), vm.news().categories.map { it.key })
        assertEquals(1, s.calls.size, "one drop, one save: ${s.calls}")
        assertTrue(s.calls.single().startsWith("saveNews"), s.calls.single())
    }

    @Test fun aDropOutsideTheListChangesNothingAndSavesNothing() = runTest {
        val s = speaker()
        val vm = NewsViewModel(s, FakeVoices(), backgroundScope)
        runCurrent()
        vm.onMoved(0, 7)
        vm.onMoved(1, 1)
        runCurrent()
        assertEquals(listOf("tech", "world", "power"), vm.news().categories.map { it.key })
        assertTrue(s.calls.isEmpty(), "${s.calls}")
    }

    @Test fun aCategoryThisAppHasNoLabelForIsARowLikeAnyOtherAndKeepsItsPlace() = runTest {
        val s = FakeSpeaker().apply {
            settings = speakerSettings(
                news = com.r1control.app.domain.model.NewsSettings(
                    true, "09:15", "b",
                    listOf(
                        com.r1control.app.domain.model.NewsCategory("tech", true),
                        com.r1control.app.domain.model.NewsCategory("sport", true),
                        com.r1control.app.domain.model.NewsCategory("world", true),
                    ),
                ),
            )
        }
        val vm = NewsViewModel(s, FakeVoices(), backgroundScope)
        runCurrent()
        vm.onMoved(0, 2)
        runCurrent()
        assertEquals(listOf("sport", "world", "tech"), vm.news().categories.map { it.key })
    }

    /** U-07: leaving the page lost every edit, because nothing was written until Save. */
    @Test fun anEditIsSentAtOnceAsTheWholeBulletin() = runTest {
        val s = speaker()
        val vm = NewsViewModel(s, FakeVoices(), backgroundScope)
        runCurrent()
        vm.onEnabled(false)
        runCurrent()
        assertTrue(s.calls.single().startsWith("saveNews NewsSettings(enabled=false, time=09:15"), "${s.calls}")
    }

    @Test fun aRunOfTapsEndsWithTheLastValueSent() = runTest {
        val s = SlowSaves(delayMillis = 300).apply { settings = speakerSettings() }
        val vm = NewsViewModel(s, FakeVoices(), backgroundScope)
        runCurrent()
        vm.onHour(1)
        runCurrent()
        repeat(4) { vm.onHour(1) }
        advanceTimeBy(5_000)
        assertTrue(s.calls.last().contains("time=14:15"), "the last tap was not the last write: ${s.calls}")
        assertTrue(s.calls.size < 5, "every tap was sent although the R1 was still busy with the first: ${s.calls}")
    }

    @Test fun anEditTakesTheTestNoteAway() = runTest {
        val vm = NewsViewModel(speaker(), FakeVoices(), backgroundScope)
        runCurrent()
        vm.test()
        runCurrent()
        assertEquals(NewsAction.Tested, vm.state.value.action)
        vm.onVoice("a")
        assertEquals(NewsAction.Idle, vm.state.value.action)
    }

    /** The panel saves before every test: the server reads nothing for a speaker it has no saved topics for. */
    @Test fun aTestSavesFirstEvenWithNothingChanged() = runTest {
        val s = speaker()
        val vm = NewsViewModel(s, FakeVoices(), backgroundScope)
        runCurrent()
        vm.test()
        runCurrent()
        assertEquals(2, s.calls.size, "${s.calls}")
        assertTrue(s.calls[0].startsWith("saveNews"))
        assertEquals("testNews", s.calls[1])
        assertEquals(NewsAction.Tested, vm.state.value.action)
    }

    @Test fun aFailedSaveIsABannerAndATestAfterItIsNotSent() = runTest {
        val s = speaker()
        val vm = NewsViewModel(s, FakeVoices(), backgroundScope)
        runCurrent()
        s.commandFailure = unreachable
        vm.onEnabled(false)
        runCurrent()
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem)
        vm.test()
        runCurrent()
        assertTrue("testNews" !in s.calls, "a test was sent with the bulletin unsaved: ${s.calls}")
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem)
        assertEquals(NewsAction.Idle, vm.state.value.action)
    }

    /** B-06: a bulletin changed elsewhere stayed as this page last saw it. */
    @Test fun theOpenPageFollowsTheSpeaker() = runTest {
        val s = speaker()
        val vm = NewsViewModel(s, FakeVoices(), backgroundScope)
        runCurrent()
        val elsewhere = vm.news().copy(time = "10:10", voice = "a")
        s.settings = speakerSettings(news = elsewhere)
        advanceTimeBy(3_001)
        runCurrent()
        assertEquals(elsewhere, vm.news())
    }

    @Test fun anEditIsHeldAgainstAReadingOlderThanIt() = runTest {
        val s = speaker()
        val vm = NewsViewModel(s, FakeVoices(), backgroundScope)
        runCurrent()
        advanceTimeBy(2_900)
        vm.onHour(1)
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertEquals("10:15", vm.news().time, "a reading put the old time back over the edit")
    }

    @Test fun aFailedSaveGoesBackToTheSpeakersBulletin() = runTest {
        val s = speaker()
        val vm = NewsViewModel(s, FakeVoices(), backgroundScope)
        runCurrent()
        s.commandFailure = unreachable
        vm.onHour(1)
        runCurrent()
        assertEquals("09:15", vm.news().time)
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem)
    }

    @Test fun aSaveThatWorksAgainClearsTheBanner() = runTest {
        val s = speaker()
        val vm = NewsViewModel(s, FakeVoices(), backgroundScope)
        runCurrent()
        s.commandFailure = unreachable
        vm.onEnabled(false)
        runCurrent()
        s.commandFailure = null
        vm.onEnabled(true)
        runCurrent()
        assertEquals(Problem.None, vm.state.value.problem)
    }
}

/** A speaker whose bulletin saves take [delayMillis], so taps pile up behind one. */
private class SlowSaves(private val delayMillis: Long) : FakeSpeaker() {
    override suspend fun saveNews(news: com.r1control.app.domain.model.NewsSettings) {
        kotlinx.coroutines.delay(delayMillis)
        super.saveNews(news)
    }
}

class BluetoothViewModelTest {
    private val kitchen = device("14:C1", "Kitchen")

    private fun speaker() = FakeSpeaker().apply { bluetoothState = bluetooth(devices = listOf(kitchen)) }

    @Test fun readsAtOnceAndKeepsPollingWhileOpen() = runTest {
        val s = speaker()
        val vm = BluetoothViewModel(s, backgroundScope, pollMillis = 1_000)
        runCurrent()
        assertIs<Loadable.Loaded<*>>(vm.state.value.bluetooth)
        assertEquals(1, s.bluetoothReads)
        advanceTimeBy(2_100)
        assertEquals(3, s.bluetoothReads)
    }

    @Test fun aFailedFirstReadIsAFailureWithRetryByPolling() = runTest {
        val s = speaker().apply { pollFailure = unreachable }
        val vm = BluetoothViewModel(s, backgroundScope, pollMillis = 1_000)
        runCurrent()
        assertEquals(Loadable.Failed(unreachable), vm.state.value.bluetooth)
        s.pollFailure = null
        advanceTimeBy(1_100)
        assertIs<Loadable.Loaded<*>>(vm.state.value.bluetooth)
    }

    @Test fun aFailureAfterAGoodReadKeepsTheListAndShowsABanner() = runTest {
        val s = speaker()
        val vm = BluetoothViewModel(s, backgroundScope, pollMillis = 1_000)
        runCurrent()
        s.pollFailure = unreachable
        advanceTimeBy(1_100)
        assertIs<Loadable.Loaded<*>>(vm.state.value.bluetooth)
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem)
    }

    @Test fun everyActionIsACommandAndThenARead() = runTest {
        val s = speaker()
        val vm = BluetoothViewModel(s, backgroundScope, pollMillis = 60_000)
        runCurrent()
        val reads = s.bluetoothReads
        vm.onEnabled(true)
        vm.onAutoReconnect(false)
        vm.scan()
        vm.receive()
        vm.pair(kitchen)
        vm.connect(kitchen)
        vm.disconnect(kitchen)
        runCurrent()
        assertEquals(
            listOf("btEnabled true", "btAuto false", "btScan", "btReceive", "pair 14:C1", "connect 14:C1", "disconnect 14:C1"),
            s.calls,
        )
        assertEquals(reads + 7, s.bluetoothReads)
    }

    @Test fun forgetNeedsAConfirmationAndCancelSendsNothing() = runTest {
        val s = speaker()
        val vm = BluetoothViewModel(s, backgroundScope, pollMillis = 60_000)
        runCurrent()
        vm.askForget(kitchen)
        assertEquals(ForgetAsk.Ask(kitchen), vm.state.value.forget)
        vm.cancelForget()
        runCurrent()
        assertEquals(ForgetAsk.None, vm.state.value.forget)
        assertTrue(s.calls.isEmpty())

        vm.askForget(kitchen)
        vm.confirmForget()
        runCurrent()
        assertEquals(listOf("forget 14:C1"), s.calls)
        assertEquals(ForgetAsk.None, vm.state.value.forget)
    }

    /** T-16: Try again under a failed first read did nothing. */
    @Test fun tryAgainReadsAtOnce() = runTest {
        val s = speaker().apply { pollFailure = unreachable }
        val vm = BluetoothViewModel(s, backgroundScope, pollMillis = 60_000)
        runCurrent()
        assertEquals(Loadable.Failed(unreachable), vm.state.value.bluetooth)
        s.pollFailure = null
        vm.retry()
        runCurrent()
        assertIs<Loadable.Loaded<*>>(vm.state.value.bluetooth)
    }

    @Test fun aCommandThatCouldNotReachTheSpeakerGoesAtTheNextGoodPoll() = runTest {
        val s = speaker()
        val vm = BluetoothViewModel(s, backgroundScope, pollMillis = 1_000)
        runCurrent()
        s.commandFailure = unreachable
        vm.connect(kitchen)
        runCurrent()
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem)
        advanceTimeBy(1_100)
        assertEquals(Problem.None, vm.state.value.problem)
    }

    @Test fun aRefusalStaysUntilTheNextCommand() = runTest {
        val refused = com.r1control.app.domain.repository.SpeakerException.Refused("busy")
        val s = speaker()
        val vm = BluetoothViewModel(s, backgroundScope, pollMillis = 1_000)
        runCurrent()
        s.commandFailure = refused
        vm.pair(kitchen)
        advanceTimeBy(5_000)
        assertEquals(Problem.Failed(refused), vm.state.value.problem, "the speaker's answer to the tap went away by itself")
        s.commandFailure = null
        vm.connect(kitchen)
        runCurrent()
        assertEquals(Problem.None, vm.state.value.problem)
    }

    @Test fun aRefusedActionIsABanner() = runTest {
        val s = speaker()
        val vm = BluetoothViewModel(s, backgroundScope, pollMillis = 60_000)
        runCurrent()
        s.commandFailure = unreachable
        vm.connect(kitchen)
        runCurrent()
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem)
    }

    @Test fun leavingThePageStopsThePolling() = runTest {
        val s = speaker()
        val scope = kotlinx.coroutines.CoroutineScope(coroutineContext[kotlin.coroutines.ContinuationInterceptor]!! + kotlinx.coroutines.Job())
        BluetoothViewModel(s, scope, pollMillis = 1_000)
        runCurrent()
        scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
        val reads = s.bluetoothReads
        advanceTimeBy(5_000)
        assertEquals(reads, s.bluetoothReads)
    }
}

class AdvancedViewModelTest {
    @Test fun restartNeedsAConfirmation() = runTest {
        val s = FakeSpeaker()
        val vm = AdvancedViewModel(s, backgroundScope)
        vm.confirmRestart()
        runCurrent()
        assertTrue(s.calls.isEmpty(), "no question asked, nothing restarted")

        vm.askRestart()
        assertEquals(RestartStep.Asking, vm.state.value.restart)
        vm.cancelRestart()
        runCurrent()
        assertTrue(s.calls.isEmpty())

        vm.askRestart()
        vm.confirmRestart()
        runCurrent()
        assertEquals(listOf("restart"), s.calls)
        assertEquals(RestartStep.Restarted, vm.state.value.restart)
    }

    @Test fun aRefusedRestartIsABanner() = runTest {
        val s = FakeSpeaker().apply { commandFailure = unreachable }
        val vm = AdvancedViewModel(s, backgroundScope)
        vm.askRestart()
        vm.confirmRestart()
        runCurrent()
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem)
        assertEquals(RestartStep.Idle, vm.state.value.restart)
    }
}

class SettingsHomeViewModelTest {
    @Test fun eachRowGetsTheValueItWillOpen() = runTest {
        val s = FakeSpeaker().apply {
            settings = speakerSettings()
            bluetoothState = bluetooth(connected = "14:C1", devices = listOf(device("14:C1", "Kitchen").copy(connected = true)))
        }
        val vm = com.r1control.app.ui.settings.SettingsHomeViewModel(s, FakeVoices(TtsVoices("Lê Yến", listOf("Lê Yến"))), backgroundScope)
        runCurrent()
        val summary = assertIs<Loadable.Loaded<com.r1control.app.ui.settings.SettingsSummary>>(vm.state.value).value
        assertEquals("Lê Yến", summary.voice)
        assertEquals("09:15", summary.news.time)
        assertEquals("Playing through Kitchen", com.r1control.app.ui.settings.bluetoothLine(summary.bluetooth, com.r1control.app.ui.i18n.EnglishStrings))
    }

    @Test fun aVoiceServerThatDoesNotAnswerLeavesOnlyTheVoiceUnsaid() = runTest {
        val s = FakeSpeaker().apply { settings = speakerSettings() }
        val vm = com.r1control.app.ui.settings.SettingsHomeViewModel(s, FakeVoices(failure = unreachable), backgroundScope)
        runCurrent()
        val summary = assertIs<Loadable.Loaded<com.r1control.app.ui.settings.SettingsSummary>>(vm.state.value).value
        assertEquals("", summary.voice)
    }

    @Test fun bluetoothOffIsSaidAsOff() {
        val off = bluetooth(connected = "14:C1").copy(enabled = false)
        assertEquals("Off", com.r1control.app.ui.settings.bluetoothLine(off, com.r1control.app.ui.i18n.EnglishStrings))
    }
}

class SettingsPagesTest {
    private val pages = com.r1control.app.ui.settings.SettingsPage.entries

    @Test fun everyPageHasAName() {
        pages.forEach { page ->
            assertTrue(page.title(com.r1control.app.ui.i18n.EnglishStrings).isNotEmpty(), "$page")
            assertTrue(page.title(com.r1control.app.ui.i18n.VietnameseStrings).isNotEmpty(), "$page")
        }
    }
}
