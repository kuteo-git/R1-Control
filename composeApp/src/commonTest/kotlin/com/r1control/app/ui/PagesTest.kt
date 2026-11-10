package com.r1control.app.ui

import com.r1control.app.domain.model.Playlist
import com.r1control.app.domain.model.PlaylistId
import com.r1control.app.domain.model.Playlists
import com.r1control.app.domain.model.SongList
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.settings.SettingsPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PagesTest {
    private val every = SettingsPage.entries.map { Page.Settings(it) } +
        Page.AllPlaylists + Page.NowPlaying + Page.PlaylistDetails(PlaylistId("p1700000000000a1f"))

    @Test fun everyPageRoundTripsThroughItsToken() {
        every.forEach { assertEquals(it, pageOf(it.token()), "$it") }
    }

    @Test fun aTokenThisBuildCannotNameIsDroppedFromARestoredStack() {
        assertNull(pageOf("settings:NoSuchPage"))
        assertNull(pageOf("playlist:"))
        assertNull(pageOf("who knows"))
        val restored = PageStackSaver.restore(listOf("settings:Home", "settings:Gone", "playlists"))
        assertEquals(PageStack(listOf(Page.Settings(SettingsPage.Home), Page.AllPlaylists)), restored)
    }

    @Test fun theStackSurvivesTheSaver() {
        val stack = PageStack(listOf(Page.AllPlaylists, Page.PlaylistDetails(PlaylistId("p1"))))
        val saved = with(PageStackSaver) { object : androidx.compose.runtime.saveable.SaverScope { override fun canBeSaved(value: Any) = true }.save(stack) }
        assertEquals(stack, PageStackSaver.restore(saved!!))
    }

    @Test fun backPopsToWhereItWasOpenedFromWhateverThePageIs() {
        val fromControl = PageStack.Tabs.open(Page.Settings(SettingsPage.Advanced))
        assertEquals(PageStack.Tabs, fromControl.back(), "Advanced opened from Control backs out to Control, not to Settings")

        val deep = PageStack.Tabs.open(Page.AllPlaylists).open(Page.PlaylistDetails(PlaylistId("p")))
        assertEquals(2, deep.depth)
        assertEquals(Page.AllPlaylists, deep.back().top)
        assertNull(deep.back().back().top)
        assertEquals(PageStack.Tabs, PageStack.Tabs.back(), "back from the tabs stays on the tabs")
    }

    @Test fun aPageThatIsGoneIsTakenOffWhereverItIs() {
        val gone = Page.PlaylistDetails(PlaylistId("p"))
        val stack = PageStack(listOf(Page.AllPlaylists, gone))
        assertEquals(PageStack(listOf(Page.AllPlaylists)), stack.without(gone))
        assertEquals(PageStack.Tabs, PageStack(listOf(gone)).without(gone))
    }

    @Test fun onlyTheMusicPagesKeepTheMiniPlayer() {
        assertTrue(Page.AllPlaylists.keepsMiniPlayer)
        assertTrue(Page.PlaylistDetails(PlaylistId("p")).keepsMiniPlayer)
        assertFalse(Page.Settings(SettingsPage.Home).keepsMiniPlayer)
    }

    @Test fun aPlaylistsPageIsTitledByItsNameLiveAndByAFallbackWhileItIsNotThere() {
        val named = Playlist(PlaylistId("p"), "Nhạc xưa", SongList.Empty, 1)
        val blank = named.copy(id = PlaylistId("q"), name = "")
        val lists = Playlists.Readable(listOf(named, blank), saveFailed = false, keptAside = false)
        assertEquals("Nhạc xưa", Page.PlaylistDetails(named.id).title(EnglishStrings, lists))
        assertEquals("Untitled playlist", Page.PlaylistDetails(blank.id).title(EnglishStrings, lists))
        assertEquals("Danh sách chưa đặt tên", Page.PlaylistDetails(blank.id).title(VietnameseStrings, lists))
        assertEquals("Playlist", Page.PlaylistDetails(named.id).title(EnglishStrings, Playlists.Loading))
        assertEquals("Playlists", Page.AllPlaylists.title(EnglishStrings, Playlists.Loading))
        assertEquals("Ngôn ngữ", Page.Settings(SettingsPage.Language).title(VietnameseStrings, Playlists.Loading))
    }
}
