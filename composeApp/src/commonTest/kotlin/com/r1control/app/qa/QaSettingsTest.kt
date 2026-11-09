package com.r1control.app.qa

import com.r1control.app.domain.model.TtsVoices
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.repository.VoiceRepository
import com.r1control.app.testing.FakeSpeaker
import com.r1control.app.testing.FakeVoices
import com.r1control.app.testing.bluetooth
import com.r1control.app.testing.device
import com.r1control.app.testing.speakerSettings
import com.r1control.app.testing.unreachable
import com.r1control.app.ui.Loadable
import com.r1control.app.ui.Problem
import com.r1control.app.ui.settings.BluetoothViewModel
import com.r1control.app.ui.settings.NewsViewModel
import com.r1control.app.ui.settings.VoiceList
import com.r1control.app.ui.settings.VoiceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class QaBluetoothTest {
    @Test fun aDroppedReadsBannerGoesAwayWhenTheSpeakerAnswersAgain() = runTest {
        val s = FakeSpeaker().apply { bluetoothState = bluetooth(devices = listOf(device("AA", "Kitchen"))) }
        val vm = BluetoothViewModel(s, backgroundScope, pollMillis = 1_000)
        runCurrent()
        s.pollFailure = unreachable
        advanceTimeBy(1_100)
        assertEquals(Problem.Failed(unreachable), vm.state.value.problem)

        s.pollFailure = null
        advanceTimeBy(5_000)
        assertIs<Loadable.Loaded<*>>(vm.state.value.bluetooth)
        assertEquals(Problem.None, vm.state.value.problem, "five good reads later the page still says unreachable")
    }

    @Test fun aFailedFirstReadRecoversByItselfThroughThePoll() = runTest {
        val s = FakeSpeaker().apply { pollFailure = unreachable }
        val vm = BluetoothViewModel(s, backgroundScope, pollMillis = 1_000)
        runCurrent()
        assertEquals(Loadable.Failed(unreachable), vm.state.value.bluetooth)
        s.pollFailure = null
        advanceTimeBy(1_100)
        assertIs<Loadable.Loaded<*>>(vm.state.value.bluetooth)
    }
}

class QaNewsTest {
    private fun speaker() = FakeSpeaker().apply { settings = speakerSettings() }

    /**
     * The panel saves before it tests (control.html, testNews): "testing must
     * reflect what's currently on screen", and a device the server has no
     * config for plays nothing at all.
     */
    @Test fun aTestPlaysWhatIsOnScreenSoTheDraftIsSavedFirst() = runTest {
        val s = speaker()
        val vm = NewsViewModel(s, FakeVoices(), backgroundScope)
        runCurrent()
        vm.onCategory("power", true)
        vm.test()
        runCurrent()
        val save = s.calls.indexOfFirst { it.startsWith("saveNews") && "NewsCategory(key=power, enabled=true)" in it }
        val test = s.calls.indexOf("testNews")
        assertTrue(save >= 0, "the edited draft was never saved: ${s.calls}")
        assertTrue(save < test, "saved after the test: ${s.calls}")
    }
}

/** A voice server whose answer to each name comes after its own delay, failing for [refused]. */
private class SlowVoices(private val refused: Set<String>, private val delays: Map<String, Long>) : VoiceRepository {
    override suspend fun voices(ttsHost: String) = TtsVoices("a", listOf("a", "b", "c"))
    override suspend fun setVoice(ttsHost: String, name: String) {
        delay(delays[name] ?: 0L)
        if (name in refused) throw SpeakerException.Refused("no")
    }
}

class QaVoiceTest {
    @Test fun aSlowRefusalDoesNotUndoALaterChoiceThatWorked() = runTest {
        val s = FakeSpeaker().apply { settings = speakerSettings(ttsHost = "http://tts:8002") }
        val vm = VoiceViewModel(s, SlowVoices(refused = setOf("b"), delays = mapOf("b" to 500L)), backgroundScope)
        runCurrent()
        vm.choose("b")
        advanceTimeBy(100)
        vm.choose("c")
        advanceTimeBy(1_000)
        val shown = assertIs<VoiceList.Voices>(assertIs<Loadable.Loaded<VoiceList>>(vm.state.value.content).value)
        assertEquals("c", shown.voices.current, "the server took c, the screen shows ${shown.voices.current}")
    }
}
