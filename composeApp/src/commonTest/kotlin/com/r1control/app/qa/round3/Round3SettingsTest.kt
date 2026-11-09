package com.r1control.app.qa.round3

import com.r1control.app.domain.model.LanguageChoice
import com.r1control.app.domain.model.NewsCategory
import com.r1control.app.domain.model.NewsSettings
import com.r1control.app.domain.model.Playlists
import com.r1control.app.domain.model.SavedAddress
import com.r1control.app.testing.FakeSpeaker
import com.r1control.app.testing.FakeVoices
import com.r1control.app.testing.MemoryPreferences
import com.r1control.app.testing.speakerSettings
import com.r1control.app.testing.unreachable
import com.r1control.app.ui.AppViewModel
import com.r1control.app.ui.Loadable
import com.r1control.app.ui.Page
import com.r1control.app.ui.Problem
import com.r1control.app.ui.i18n.Language
import com.r1control.app.ui.i18n.resolve
import com.r1control.app.ui.settings.NewsViewModel
import com.r1control.app.ui.settings.SettingsPage
import com.r1control.app.ui.title
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The Language picker and the News categories' drag, as Settings shows them. */
class Round3SettingsTest {
    private fun AppViewModel.pageTitle(page: Page, device: Language) = page.title(language.value.resolve(device).strings, Playlists.Loading)

    /** R3-47. Switching language while on the Language page: the page it is on is retitled at once, and the choice is stored. */
    @Test fun aLanguageChosenOnItsPageRetitlesThatPageAtOnce() = runTest {
        val prefs = MemoryPreferences(address = SavedAddress.Unset)
        val app = AppViewModel(prefs, backgroundScope)
        runCurrent()
        val here = Page.Settings(SettingsPage.Language)
        assertEquals("Language", app.pageTitle(here, Language.English))
        app.chooseLanguage(LanguageChoice.Vietnamese)
        assertEquals("Ngôn ngữ", app.pageTitle(here, Language.English), "applied before any save")
        runCurrent()
        assertEquals(LanguageChoice.Vietnamese, prefs.language)
        app.chooseLanguage(LanguageChoice.FollowPhone)
        assertEquals("Ngôn ngữ", app.pageTitle(here, Language.Vietnamese), "following a Vietnamese phone")
        assertEquals("Language", app.pageTitle(here, Language.English), "following an English phone")
    }

    /** R3-48. A stored choice is the first frame's language on the next start. */
    @Test fun aStoredChoiceIsTheLanguageOfTheNextStart() = runTest {
        val app = AppViewModel(MemoryPreferences(language = LanguageChoice.English), backgroundScope)
        runCurrent()
        assertEquals("Language", app.pageTitle(Page.Settings(SettingsPage.Language), Language.Vietnamese))
    }

    private fun bulletin(enabled: Boolean) = NewsSettings(
        enabled, "07:00", "b",
        listOf("tech", "world", "society", "weather", "power").map { NewsCategory(it, true) },
    )

    /** R3-43. A drop the R1 refuses: the categories go back to the R1's order and the page says why. */
    @Test fun aRefusedDropShowsTheR1sOrderAgainAndSaysWhy() = runTest {
        val speaker = FakeSpeaker().apply { settings = speakerSettings(news = bulletin(true)) }
        val vm = NewsViewModel(speaker, FakeVoices(), backgroundScope)
        runCurrent()
        speaker.commandFailure = unreachable
        vm.onMoved(4, 0)
        runCurrent()
        val shown = assertIs<Loadable.Loaded<*>>(vm.state.value.draft)
        assertEquals(
            listOf("tech", "world", "society", "weather", "power"),
            (shown.value as com.r1control.app.ui.settings.NewsDraft).news.categories.map { it.key },
        )
        assertIs<Problem.Failed>(vm.state.value.problem)
    }

    /** R3-44. An R1 category this app has no label for keeps its place when another is dragged past it. */
    @Test fun anUnknownCategoryKeepsItsPlaceInTheDrop() = runTest {
        val news = bulletin(true).let { it.copy(categories = it.categories + NewsCategory("sports", false)) }
        val speaker = FakeSpeaker().apply { settings = speakerSettings(news = news) }
        val vm = NewsViewModel(speaker, FakeVoices(), backgroundScope)
        runCurrent()
        vm.onMoved(0, 2)
        runCurrent()
        advanceTimeBy(10)
        val keys = ((vm.state.value.draft as Loadable.Loaded).value).news.categories.map { it.key }
        assertEquals(listOf("world", "society", "tech", "weather", "power", "sports"), keys)
        assertEquals(1, speaker.calls.count { it.startsWith("saveNews") })
    }
}
