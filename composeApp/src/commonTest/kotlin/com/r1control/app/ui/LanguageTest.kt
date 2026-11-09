package com.r1control.app.ui

import com.r1control.app.domain.model.LanguageChoice
import com.r1control.app.domain.model.R1Address
import com.r1control.app.domain.model.SavedAddress
import com.r1control.app.testing.MemoryPreferences
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.Language
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.i18n.languageChoiceName
import com.r1control.app.ui.i18n.resolve
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LanguageTest {
    @Test fun resolveCoversAllChoicesOverBothDevices() {
        for (device in Language.entries) {
            assertEquals(device, LanguageChoice.FollowPhone.resolve(device))
            assertEquals(Language.Vietnamese, LanguageChoice.Vietnamese.resolve(device))
            assertEquals(Language.English, LanguageChoice.English.resolve(device))
        }
    }

    @Test fun eachLanguageIsNamedInItsOwnWordsWhateverLanguageTheAppIsIn() {
        for (strings in listOf(EnglishStrings, VietnameseStrings)) {
            assertEquals("Tiếng Việt", strings.languageChoiceName(LanguageChoice.Vietnamese))
            assertEquals("English", strings.languageChoiceName(LanguageChoice.English))
        }
        assertEquals("Follow the phone", EnglishStrings.languageChoiceName(LanguageChoice.FollowPhone))
        assertEquals("Theo điện thoại", VietnameseStrings.languageChoiceName(LanguageChoice.FollowPhone))
    }

    @Test fun theGateWaitsForTheLanguageSoNoFrameIsDrawnInTheWrongOne() = runTest {
        val prefs = MemoryPreferences(SavedAddress.Set(R1Address("10.0.0.5", 8088)), language = LanguageChoice.Vietnamese)
        val vm = AppViewModel(prefs, backgroundScope)
        assertEquals(Gate.Loading, vm.gate.value)
        runCurrent()
        assertIs<Gate.Ready>(vm.gate.value)
        assertEquals(LanguageChoice.Vietnamese, vm.language.value, "already read when the gate opened")
    }

    @Test fun aChoiceAppliesAtOnceAndIsSaved() = runTest {
        val prefs = MemoryPreferences()
        val vm = AppViewModel(prefs, backgroundScope)
        runCurrent()
        assertEquals(LanguageChoice.FollowPhone, vm.language.value)
        vm.chooseLanguage(LanguageChoice.English)
        assertEquals(LanguageChoice.English, vm.language.value, "before the save has run")
        runCurrent()
        assertEquals(LanguageChoice.English, prefs.language)
    }

    @Test fun aChoiceThatCouldNotBeSavedHoldsForThisRun() = runTest {
        val prefs = object : MemoryPreferences() {
            override suspend fun saveLanguage(choice: LanguageChoice) = error("disk full")
        }
        val vm = AppViewModel(prefs, backgroundScope)
        runCurrent()
        vm.chooseLanguage(LanguageChoice.Vietnamese)
        runCurrent()
        assertEquals(LanguageChoice.Vietnamese, vm.language.value)
    }

    @Test fun anUnreadableStoredChoiceFollowsThePhone() = runTest {
        val prefs = object : MemoryPreferences() {
            override suspend fun language(): LanguageChoice = error("unreadable")
        }
        val vm = AppViewModel(prefs, backgroundScope)
        runCurrent()
        assertEquals(LanguageChoice.FollowPhone, vm.language.value)
    }
}
