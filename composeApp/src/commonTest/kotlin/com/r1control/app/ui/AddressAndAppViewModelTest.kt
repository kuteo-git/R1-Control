package com.r1control.app.ui

import com.r1control.app.domain.model.AddressInput
import com.r1control.app.domain.model.R1Address
import com.r1control.app.domain.model.SavedAddress
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.usecase.SaveAddress
import com.r1control.app.testing.FakeSpeaker
import com.r1control.app.testing.MemoryPreferences
import com.r1control.app.testing.unreachable
import com.r1control.app.ui.address.AddressStatus
import com.r1control.app.ui.address.AddressViewModel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AddressViewModelTest {
    private val saved = mutableListOf<R1Address>()

    private fun TestScope.address(speaker: FakeSpeaker, prefs: MemoryPreferences = MemoryPreferences(), initial: String = "") =
        AddressViewModel(initial, SaveAddress(speaker, prefs), backgroundScope) { saved += it }

    @Test fun typingClearsAnOldMessage() = runTest {
        val vm = address(FakeSpeaker())
        vm.onText("nonsense words")
        vm.save()
        runCurrent()
        assertIs<AddressStatus.Invalid>(vm.status.value)
        vm.onText("10.0.0.1")
        assertEquals(AddressStatus.Idle, vm.status.value)
    }

    @Test fun anInvalidAddressNamesTheReasonAndProbesNothing() = runTest {
        val speaker = FakeSpeaker()
        val vm = address(speaker)
        vm.onText("10.0.0.1:99999")
        vm.save()
        runCurrent()
        assertEquals(AddressStatus.Invalid(AddressInput.Reason.BadPort), vm.status.value)
        assertEquals(emptyList(), speaker.calls)
        assertEquals(emptyList(), saved)
    }

    @Test fun anEmptyFieldIsInvalid() = runTest {
        val vm = address(FakeSpeaker())
        vm.save()
        runCurrent()
        assertEquals(AddressStatus.Invalid(AddressInput.Reason.Empty), vm.status.value)
    }

    @Test fun nothingAnsweringIsNotSaved() = runTest {
        val prefs = MemoryPreferences()
        val vm = address(FakeSpeaker().apply { commandFailure = unreachable }, prefs)
        vm.onText("10.0.0.9")
        vm.save()
        runCurrent()
        val status = assertIs<AddressStatus.Unreachable>(vm.status.value)
        assertEquals("10.0.0.9:8088", status.address.toString())
        assertEquals(SavedAddress.Unset, prefs.address)
        assertEquals(emptyList(), saved)
    }

    @Test fun anAnsweringSpeakerIsSavedWithTheDefaultPortAndReported() = runTest {
        val prefs = MemoryPreferences()
        val vm = address(FakeSpeaker(), prefs)
        vm.onText("10.25.113.209")
        vm.save()
        runCurrent()
        assertEquals(AddressStatus.Saved(R1Address("10.25.113.209", 8088)), vm.status.value)
        assertEquals("10.25.113.209:8088", vm.text.value)
        assertEquals(SavedAddress.Set(R1Address("10.25.113.209", 8088)), prefs.address)
        assertEquals(listOf(R1Address("10.25.113.209", 8088)), saved)
    }
}

/**
 * B-01: on a fresh iPhone the first check meets the Local Network question and fails while it is
 * on screen. Answering it hands the app back, and the check runs again.
 */
class AddressRecheckTest {
    private fun TestScope.address(speaker: FakeSpeaker) =
        AddressViewModel("10.0.0.9", SaveAddress(speaker, MemoryPreferences()), backgroundScope) {}

    @Test fun anUnreachableCheckRunsAgainWhenTheAppComesBack() = runTest {
        val speaker = FakeSpeaker().apply { commandFailure = unreachable }
        val vm = address(speaker)
        vm.save()
        runCurrent()
        assertIs<AddressStatus.Unreachable>(vm.status.value)
        speaker.commandFailure = null
        vm.onReturned()
        runCurrent()
        assertEquals(AddressStatus.Saved(R1Address("10.0.0.9", 8088)), vm.status.value)
    }

    @Test fun somethingElseAnsweringOrNoCheckYetIsNotRetried() = runTest {
        val speaker = FakeSpeaker().apply { commandFailure = SpeakerException.Refused("HTTP 404") }
        val vm = address(speaker)
        vm.onReturned()
        runCurrent()
        assertEquals(emptyList(), speaker.calls, "nothing was asked, so nothing is asked again")
        vm.save()
        runCurrent()
        vm.onReturned()
        runCurrent()
        assertEquals(1, speaker.calls.size, "a wrong answer is not a permission: ${speaker.calls}")
    }
}

class AppViewModelTest {
    @Test fun noAddressMeansTheFirstRunPage() = runTest {
        val vm = AppViewModel(MemoryPreferences(), backgroundScope)
        assertEquals(Gate.Loading, vm.gate.value)
        runCurrent()
        assertEquals(Gate.NeedsAddress, vm.gate.value)
    }

    @Test fun aSavedAddressOpensTheTabs() = runTest {
        val address = R1Address("10.0.0.1", 8088)
        val vm = AppViewModel(MemoryPreferences(SavedAddress.Set(address)), backgroundScope)
        runCurrent()
        assertEquals(Gate.Ready(address), vm.gate.value)
    }

    @Test fun savingAnAddressOpensTheTabsAndAChangeReplacesIt() = runTest {
        val vm = AppViewModel(MemoryPreferences(), backgroundScope)
        runCurrent()
        vm.addressSaved(R1Address("10.0.0.2", 8088))
        assertEquals(Gate.Ready(R1Address("10.0.0.2", 8088)), vm.gate.value)
        vm.addressSaved(R1Address("10.0.0.3", 9000))
        assertEquals(Gate.Ready(R1Address("10.0.0.3", 9000)), vm.gate.value)
    }
}

/** T-23: a router or another web server at the address was reported as "nothing answered". */
class AddressMessageTest {
    private val address = com.r1control.app.domain.model.R1Address("10.0.0.1", 8088)
    private val strings = com.r1control.app.ui.i18n.EnglishStrings

    @Test fun somethingThatAnswersAndIsNotAnR1IsSaidSo() {
        for (error in listOf(SpeakerException.Refused("HTTP 404"), SpeakerException.BadReply("state"))) {
            val message = com.r1control.app.ui.address.addressMessage(AddressStatus.Unreachable(address, error), strings)
            assertEquals(strings.addressNotR1("10.0.0.1:8088"), message, "for ${error::class.simpleName}")
        }
    }

    @Test fun nothingAnsweringIsSaidWithWhatToCheck() {
        val message = com.r1control.app.ui.address.addressMessage(
            AddressStatus.Unreachable(address, SpeakerException.Unreachable(RuntimeException("x"))), strings,
        )
        assertEquals(strings.addressUnreachable("10.0.0.1:8088", onIphone = false), message)
    }

    /** B-01: on an iPhone a refused Local Network permission looks exactly like being off the wifi. */
    @Test fun onAnIphoneTheUnreachableWordsNameTheLocalNetworkSetting() {
        for (s in listOf(com.r1control.app.ui.i18n.EnglishStrings, com.r1control.app.ui.i18n.VietnameseStrings)) {
            val local = if (s === strings) "Local Network" else "Mạng cục bộ"
            assertTrue(local in s.addressUnreachable("10.0.0.1:8088", onIphone = true))
            assertTrue(local in s.errorUnreachable(onIphone = true))
            assertTrue(local in s.speakerOfflineHint(onIphone = true))
            assertTrue(local !in s.addressUnreachable("10.0.0.1:8088", onIphone = false), "an Android phone has no such setting")
            assertTrue(local !in s.errorUnreachable(onIphone = false))
            assertTrue("iPhone" !in s.speakerOfflineHint(onIphone = false))
        }
    }
}
