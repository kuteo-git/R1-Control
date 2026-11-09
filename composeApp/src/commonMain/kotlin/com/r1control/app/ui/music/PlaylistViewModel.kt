package com.r1control.app.ui.music

import com.r1control.app.domain.model.ListEdit
import com.r1control.app.domain.model.ListRef
import com.r1control.app.domain.model.PlaylistId
import com.r1control.app.domain.model.SongList
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.model.playsFrom
import com.r1control.app.domain.usecase.MusicStore
import com.r1control.app.domain.usecase.PlaylistLookup
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.domain.usecase.SpeakerView
import com.r1control.app.ui.CommandProblem
import com.r1control.app.ui.Problem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What is asked over a playlist's page. */
sealed interface PlaylistDialog {
    data object None : PlaylistDialog
    data object Rename : PlaylistDialog
    data object ConfirmDelete : PlaylistDialog
}

sealed interface PlaylistPageState {
    /** The phone's first read of its playlists is still on its way: nothing is drawn, and the page does not back out. */
    data object Loading : PlaylistPageState

    /** Deleted (here, or before a restore), or the playlists cannot be read: the screen calls `onGone` once. */
    data object Gone : PlaylistPageState

    data class Showing(
        val name: String,
        val hasName: Boolean,
        val songs: SongList,
        /** The speaker plays from this playlist now ([playsFrom]): the header says so. */
        val playingHere: Boolean,
        val dialog: PlaylistDialog,
        /** A play or shuffle the speaker refused, or a reading that stopped coming (the policy of the mini player). */
        val problem: Problem,
    ) : PlaylistPageState {
        /** Play and Shuffle are drawn only with songs to play; an empty playlist says how to fill it. */
        val canPlay: Boolean get() = !songs.isEmpty
    }
}

/**
 * One visit of a playlist's page. It holds only this visit's dialog and
 * banner; the playlist itself is the store's and outlives the page, so an X
 * or a drag here edits the very list the speaker may be playing from.
 */
class PlaylistViewModel(
    private val id: PlaylistId,
    private val store: MusicStore,
    private val feed: SpeakerFeed,
    private val scope: CoroutineScope,
) {
    private val ref = ListRef.Saved(id)
    private val commandProblem = CommandProblem(feed, scope)
    private val dialog = MutableStateFlow<PlaylistDialog>(PlaylistDialog.None)

    val state: StateFlow<PlaylistPageState> = combine(store.state, dialog, commandProblem.flow, feed.view) { lists, dialog, command, view ->
        val page: PlaylistPageState = when (val found = lists.playlist(id)) {
            PlaylistLookup.Loading -> PlaylistPageState.Loading
            PlaylistLookup.Gone -> PlaylistPageState.Gone
            is PlaylistLookup.Found -> PlaylistPageState.Showing(
                name = found.playlist.name,
                hasName = found.playlist.hasName,
                songs = found.playlist.songs,
                playingHere = when (view) {
                    is SpeakerView.Online -> playsFrom(lists.playing, ref, view.snapshot.media)
                    is SpeakerView.Stale -> playsFrom(lists.playing, ref, view.snapshot.media)
                    SpeakerView.Connecting, is SpeakerView.Offline -> false
                },
                dialog = dialog,
                problem = if (view is SpeakerView.Stale) Problem.Failed(view.error) else command,
            )
        }
        page
    }.stateIn(scope, SharingStarted.Eagerly, PlaylistPageState.Loading)

    /** The same derivation as the Music tab: one request, one function. */
    val mark: StateFlow<SongMark> = combine(store.state, feed.view) { lists, view -> songMarkOf(lists.request, view) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SongMark.None)

    /** A row: plays this playlist from it, in its order. */
    fun onPlay(index: Int) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            commandProblem.report(store.play(ref, index))
            feed.refresh()
        }
    }

    /** The prominent Shuffle: a random order from the first song. The playlist's own order is untouched. */
    fun onShuffle() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            commandProblem.report(store.shuffle(ref))
            feed.refresh()
        }
    }

    fun onMove(from: Int, to: Int) = store.edit(ref, ListEdit.Move(from, to))
    fun onRemove(track: Track) = store.edit(ref, ListEdit.Remove(track.id))

    fun onRenameAsked() {
        dialog.value = PlaylistDialog.Rename
    }

    fun onRename(name: String) {
        store.rename(id, name)
        dialog.value = PlaylistDialog.None
    }

    fun onDeleteAsked() {
        dialog.value = PlaylistDialog.ConfirmDelete
    }

    /** The store lets go of the pointer if this was playing; the state becomes Gone and the page backs out. */
    fun onDelete() {
        dialog.value = PlaylistDialog.None
        store.delete(id)
    }

    fun onDialogDismissed() {
        dialog.value = PlaylistDialog.None
    }
}
