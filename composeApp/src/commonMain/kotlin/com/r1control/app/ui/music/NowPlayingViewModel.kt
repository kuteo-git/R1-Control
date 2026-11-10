package com.r1control.app.ui.music

import com.r1control.app.domain.model.ListEdit
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.usecase.MusicStore
import com.r1control.app.domain.usecase.SpeakerFeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Now playing page: which songs are playing, read from the store and the
 * feed. It holds nothing of its own; edits go to the list's owner, the store.
 */
class NowPlayingViewModel(
    private val store: MusicStore,
    private val feed: SpeakerFeed,
    private val scope: CoroutineScope,
) {
    // Started from the present reading, not Nothing: the page opens on the songs, with no empty frame first.
    val state: StateFlow<NowPlayingPage> = combine(store.state, feed.view) { lists, view -> nowPlayingPageOf(lists, view) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), nowPlayingPageOf(store.state.value, feed.view.value))

    /** The same lit row and spinner as every other song list. */
    val mark: StateFlow<SongMark> = combine(store.state, feed.view) { lists, view -> songMarkOf(lists.request, view) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SongMark.None)

    /** Read at the press, not from what was last drawn: the list may have moved on since. */
    private fun current(): NowPlayingPage.FromApp? = nowPlayingPageOf(store.state.value, feed.view.value) as? NowPlayingPage.FromApp

    /** A row of the playing list: plays from that place in the order the list is playing in. */
    fun onPlay(index: Int) {
        val page = current() ?: return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            store.playFrom(page.ref, page.order, index)
            feed.refresh()
        }
    }

    fun onRemove(track: Track) {
        val page = current() ?: return
        store.edit(page.ref, ListEdit.Remove(track.id))
    }

    /** Only a listed list offers the drag, so the indexes are the list's own. */
    fun onMove(from: Int, to: Int) {
        val page = current() ?: return
        if (!page.canReorder) return
        store.edit(page.ref, ListEdit.Move(from, to))
    }
}
