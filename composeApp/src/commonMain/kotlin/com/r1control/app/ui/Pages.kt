package com.r1control.app.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.r1control.app.domain.model.PlaylistId
import com.r1control.app.domain.model.Playlists
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.settings.SettingsPage
import com.r1control.app.ui.settings.title

// The app's one navigation model for everything that is not a tab. It replaces
// an ordinal of SettingsPage, which could carry no argument and knew only Settings.

/**
 * A page over the tabs. `SettingsPage` stays the enum it is, so its exhaustive
 * `when`s keep working, and the Music pages are new kinds beside it. Adding a
 * kind fails to compile until [token], [pageOf], [title], [keepsMiniPlayer]
 * and the shell's page host handle it.
 */
sealed interface Page {
    data class Settings(val page: SettingsPage) : Page
    data object AllPlaylists : Page
    data class PlaylistDetails(val id: PlaylistId) : Page
}

/**
 * The open pages, bottom first; empty shows the tabs. Back pops one, so Back
 * returns to the page somebody was on, wherever they came from: Advanced
 * opened from Control's "change address" backs out to Control. The depth the
 * slide needs is the stack's size.
 */
data class PageStack(val pages: List<Page>) {
    val top: Page? get() = pages.lastOrNull()
    val depth: Int get() = pages.size

    fun open(page: Page): PageStack = PageStack(pages + page)

    fun back(): PageStack = PageStack(pages.dropLast(1))

    /** Every entry that is [page] taken off: a playlist deleted here, or no longer there after a restore. */
    fun without(page: Page): PageStack = PageStack(pages.filter { it != page })

    companion object {
        val Tabs = PageStack(emptyList())
    }
}

/** A page as one string, for `rememberSaveable`. [pageOf] is its inverse. */
fun Page.token(): String = when (this) {
    is Page.Settings -> "settings:" + page.name
    Page.AllPlaylists -> "playlists"
    is Page.PlaylistDetails -> "playlist:" + id.value
}

/** The page [token] names, or null for one this build cannot name (a renamed settings page): it is dropped from a restored stack. */
fun pageOf(token: String): Page? = when {
    token == "playlists" -> Page.AllPlaylists
    token.startsWith("playlist:") -> token.removePrefix("playlist:").takeIf { it.isNotEmpty() }?.let { Page.PlaylistDetails(PlaylistId(it)) }
    token.startsWith("settings:") -> SettingsPage.entries.firstOrNull { it.name == token.removePrefix("settings:") }?.let { Page.Settings(it) }
    else -> null
}

val PageStackSaver: Saver<PageStack, Any> = listSaver(
    save = { stack -> stack.pages.map(Page::token) },
    restore = { tokens -> PageStack(tokens.mapNotNull(::pageOf)) },
)

/** The top bar's title. A playlist's page is titled by the playlist's name, read live. */
fun Page.title(strings: Strings, playlists: Playlists): String = when (this) {
    is Page.Settings -> page.title(strings)
    Page.AllPlaylists -> strings.playlists
    is Page.PlaylistDetails -> {
        val found = (playlists as? Playlists.Readable)?.all?.firstOrNull { it.id == id }
        when {
            found == null -> strings.playlist
            found.hasName -> found.name
            else -> strings.untitledPlaylist
        }
    }
}

/**
 * The Music pages keep the mini player, so a row tapped on a playlist shows
 * the song starting and Next stays where the list is being edited. Settings
 * pages keep the old behaviour, no bars. The tab bar is never drawn over a
 * page: pressing a tab under an open page would have to mean something.
 */
val Page.keepsMiniPlayer: Boolean
    get() = when (this) {
        is Page.Settings -> false
        Page.AllPlaylists, is Page.PlaylistDetails -> true
    }
