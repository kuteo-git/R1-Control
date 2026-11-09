package com.r1control.app.ui.music

import com.r1control.app.domain.model.ListEdit
import com.r1control.app.domain.model.ListRef
import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.PlayRequest
import com.r1control.app.domain.model.PlaylistId
import com.r1control.app.domain.model.Playlists
import com.r1control.app.domain.model.RecentSearch
import com.r1control.app.domain.model.Shelf
import com.r1control.app.domain.model.SongList
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.model.cleanKeywords
import com.r1control.app.domain.model.forgetSearch
import com.r1control.app.domain.model.keywordsIn
import com.r1control.app.domain.model.keywordsText
import com.r1control.app.domain.model.shelfOf
import com.r1control.app.domain.repository.PreferencesRepository
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.usecase.Created
import com.r1control.app.domain.usecase.FailedKeyword
import com.r1control.app.domain.usecase.MusicLists
import com.r1control.app.domain.usecase.MusicStore
import com.r1control.app.domain.usecase.SearchMusic
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.domain.usecase.SpeakerView
import com.r1control.app.ui.Problem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MusicPhase {
    /** No search yet (or cleared): the shelf and the recent searches show. */
    data object Idle : MusicPhase
    data object Searching : MusicPhase

    /** The songs are the store's ([MusicState.results]), so an X or a drag on a result edits the list the speaker may be playing. */
    data class Done(val ref: ListRef.Results, val failed: List<FailedKeyword>, val allFailed: Boolean) : MusicPhase
}

/** A sheet row. [hasAll]: every song being added is already there, so picking it does nothing; the row says so with a check. */
data class PickerRow(val id: PlaylistId, val name: String, val hasName: Boolean, val count: Int, val hasAll: Boolean)

/** The "add to playlist" sheet over the Music tab. */
sealed interface Picker {
    data object Closed : Picker

    /** [songs]: one row's song, or every result. [playlists]: the sheet's rows, newest first. */
    data class Open(val songs: List<Track>, val playlists: List<PickerRow>) : Picker
}

/** The shelf above the recent searches. */
sealed interface ShelfState {
    data object Loading : ShelfState

    /** The phone could not read its playlists and could not keep them aside; nothing is offered, and the store will not save over them. */
    data object Unreadable : ShelfState

    /** [saveFailed]: a change may not survive a restart. [keptAside]: the stored playlists did not read and were kept aside. */
    data class Ready(val shelf: Shelf, val saveFailed: Boolean, val keptAside: Boolean) : ShelfState
}

data class MusicState(
    val phase: MusicPhase,
    /** The songs of [phase] when it is Done, read from the store; Empty otherwise. */
    val results: SongList,
    val recents: List<RecentSearch>,
    val shelf: ShelfState,
    val picker: Picker,
    val request: PlayRequest,
) {
    /**
     * Whether songs can go into a playlist: only while the playlists are read.
     * While they are locked (or still loading) no add, "Add all" or "New
     * playlist" is drawn, because the store would make nothing.
     */
    val canAdd: Boolean get() = shelf is ShelfState.Ready

    val problem: Problem
        get() = when (request) {
            is PlayRequest.Failed -> Problem.Failed(request.error)
            PlayRequest.None, is PlayRequest.Starting -> Problem.None
        }
}

/** What a result row shows over its cover: the song being started, the one the speaker plays, or why it did not start. */
sealed interface SongMark {
    data object None : SongMark
    /** Asked for, or still downloading on the speaker. */
    data class Starting(val trackId: String) : SongMark
    /** Playing or paused on the speaker. */
    data class OnSpeaker(val trackId: String) : SongMark
    data class Failed(val trackId: String, val error: SpeakerException) : SongMark
}

/** Whether the song on the speaker (playing or paused) is [trackId]: that row is the lit one. */
fun SongMark.isOnSpeaker(trackId: String): Boolean = this is SongMark.OnSpeaker && this.trackId == trackId

/** The request wins while there is one; otherwise the speaker's own song. */
fun songMarkOf(request: PlayRequest, view: SpeakerView): SongMark {
    when (request) {
        is PlayRequest.Starting -> return SongMark.Starting(request.trackId)
        is PlayRequest.Failed -> return SongMark.Failed(request.trackId, request.error)
        PlayRequest.None -> Unit
    }
    val media = when (view) {
        is SpeakerView.Online -> view.snapshot.media
        is SpeakerView.Stale -> view.snapshot.media
        SpeakerView.Connecting, is SpeakerView.Offline -> return SongMark.None
    }
    return when (media.status) {
        MediaStatus.Downloading -> SongMark.Starting(media.videoId)
        MediaStatus.Playing, MediaStatus.Paused -> SongMark.OnSpeaker(media.videoId)
        MediaStatus.Idle, MediaStatus.Stopped, MediaStatus.Unknown -> SongMark.None
    }
}

/**
 * The Music tab. The search field's text is its own flow so a keystroke is never
 * routed through anything that could hand an older text back to the field.
 * Several keywords are one text, split on commas ([keywordsIn]). The songs, the
 * playlists and every play go through the [MusicStore]; this keeps the query,
 * the phase, the recents and the sheet.
 */
class MusicViewModel(
    private val searchMusic: SearchMusic,
    private val preferences: PreferencesRepository,
    private val store: MusicStore,
    private val feed: SpeakerFeed,
    private val scope: CoroutineScope,
) {
    private sealed interface Ask {
        data object None : Ask
        data class Adding(val songs: List<Track>) : Ask
    }

    private data class Own(val phase: MusicPhase, val recents: List<RecentSearch>, val ask: Ask)

    private val queryFlow = MutableStateFlow("")
    private val focusAsked = MutableStateFlow(false)
    private val own = MutableStateFlow(Own(MusicPhase.Idle, emptyList(), Ask.None))
    private var searching: Job? = null

    val query: StateFlow<String> = queryFlow.asStateFlow()

    /** The search field is to take focus: an empty playlist's "Find songs" sent the person here. */
    val focusSearch: StateFlow<Boolean> = focusAsked.asStateFlow()

    val state: StateFlow<MusicState> = combine(own, store.state) { own, lists -> stateOf(own, lists) }
        .stateIn(scope, SharingStarted.Eagerly, stateOf(own.value, store.state.value))

    val mark: StateFlow<SongMark> = combine(store.state, feed.view) { lists, view -> songMarkOf(lists.request, view) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SongMark.None)

    init {
        scope.launch { loadRecents() }
    }

    private fun stateOf(own: Own, lists: MusicLists): MusicState {
        val phase = own.phase
        val results = if (phase is MusicPhase.Done) lists.songs(phase.ref) else SongList.Empty
        val shelf = when (val playlists = lists.playlists) {
            Playlists.Loading -> ShelfState.Loading
            Playlists.Unreadable -> ShelfState.Unreadable
            is Playlists.Readable -> ShelfState.Ready(shelfOf(playlists.all), playlists.saveFailed, playlists.keptAside)
        }
        val picker = when (val ask = own.ask) {
            Ask.None -> Picker.Closed
            is Ask.Adding -> Picker.Open(
                ask.songs,
                ((lists.playlists as? Playlists.Readable)?.all.orEmpty()).map { playlist ->
                    PickerRow(playlist.id, playlist.name, playlist.hasName, playlist.songs.size, ask.songs.all { playlist.songs.contains(it.id) })
                },
            )
        }
        return MusicState(phase, results, own.recents, shelf, picker, lists.request)
    }

    fun onQuery(text: String) {
        queryFlow.value = text
    }

    val canSearch: Boolean get() = keywordsIn(queryFlow.value).isNotEmpty()

    fun search() = run(keywordsIn(queryFlow.value))

    /** A recent search fills the field and runs again. */
    fun searchAgain(recent: RecentSearch) {
        queryFlow.value = keywordsText(recent.keywords)
        run(recent.keywords)
    }

    fun forget(recent: RecentSearch) {
        val kept = forgetSearch(own.value.recents, recent)
        own.value = own.value.copy(recents = kept)
        scope.launch {
            try {
                preferences.saveRecentSearches(forgetSearch(preferences.recentSearches(), recent))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Gone from the screen; a phone that cannot write keeps it for the next start.
            }
        }
    }

    fun clearResults() {
        searching?.cancel()
        store.clearFailedPlay()
        own.value = own.value.copy(phase = MusicPhase.Idle)
    }

    /** Plays the results on screen from [index]. The tapped row is marked at once; a later tap wins over this one's answer. */
    fun play(index: Int) {
        val phase = own.value.phase as? MusicPhase.Done ?: return
        // Undispatched: the store marks the row before the first suspension, so the mark is there at once.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            store.play(phase.ref, index)
            feed.refresh()
        }
    }

    /** The handle's release, on the results on screen. */
    fun onMove(from: Int, to: Int) {
        val phase = own.value.phase as? MusicPhase.Done ?: return
        store.edit(phase.ref, ListEdit.Move(from, to))
    }

    fun onRemove(track: Track) {
        val phase = own.value.phase as? MusicPhase.Done ?: return
        store.edit(phase.ref, ListEdit.Remove(track.id))
    }

    /** A row's add, or "Add all" with every result: opens the sheet. */
    fun onAddToPlaylist(songs: List<Track>) {
        if (songs.isEmpty() || store.state.value.playlists !is Playlists.Readable) return
        own.value = own.value.copy(ask = Ask.Adding(songs))
    }

    /** A sheet row: the songs are added (those already there are skipped by the list) and the sheet closes. */
    fun onPick(id: PlaylistId) {
        val ask = own.value.ask as? Ask.Adding ?: return
        store.edit(ListRef.Saved(id), ListEdit.Add(ask.songs))
        own.value = own.value.copy(ask = Ask.None)
    }

    /** "New playlist" then a name: a playlist holding the songs being added. A name the store refuses keeps the sheet open. */
    fun onPickNew(name: String) {
        val ask = own.value.ask as? Ask.Adding ?: return
        when (store.create(name, ask.songs)) {
            is Created.Made -> own.value = own.value.copy(ask = Ask.None)
            Created.Refused -> Unit
        }
    }

    fun onPickerDismiss() {
        own.value = own.value.copy(ask = Ask.None)
    }

    /** The shelf's "New playlist": an empty playlist named [name]; what was made is handed back so its page can open. */
    fun onNewPlaylist(name: String): Created = store.create(name, emptyList())

    fun askForSearch() {
        focusAsked.value = true
    }

    fun onSearchFocused() {
        focusAsked.value = false
    }

    // The last question wins: a new search cancels the one in flight.
    private fun run(keywords: List<String>) {
        if (cleanKeywords(keywords).isEmpty()) return
        searching?.cancel()
        own.value = own.value.copy(phase = MusicPhase.Searching)
        searching = scope.launch {
            val outcome = searchMusic(keywords)
            val ref = store.showResults(outcome.tracks)
            own.value = own.value.copy(phase = MusicPhase.Done(ref, outcome.failed, outcome.allFailed))
            loadRecents()
        }
    }

    private suspend fun loadRecents() {
        try {
            own.value = own.value.copy(recents = preferences.recentSearches())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A phone that cannot read its history still searches.
        }
    }
}
