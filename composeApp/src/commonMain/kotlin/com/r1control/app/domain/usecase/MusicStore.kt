package com.r1control.app.domain.usecase

import com.r1control.app.domain.model.ListEdit
import com.r1control.app.domain.model.ListRef
import com.r1control.app.domain.model.MediaState
import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.NextMove
import com.r1control.app.domain.model.PlayOrder
import com.r1control.app.domain.model.PlayRequest
import com.r1control.app.domain.model.Playing
import com.r1control.app.domain.model.Playlist
import com.r1control.app.domain.model.PlaylistId
import com.r1control.app.domain.model.Playlists
import com.r1control.app.domain.model.SongList
import com.r1control.app.domain.model.StoredPlaylists
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.model.cleanPlaylistName
import com.r1control.app.domain.model.nextMove
import com.r1control.app.domain.model.playSequence
import com.r1control.app.domain.model.queueAround
import com.r1control.app.domain.model.upcomingAfter
import com.r1control.app.domain.repository.PreferencesRepository
import com.r1control.app.domain.repository.SpeakerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * Everything [MusicStore] holds, as one value. Every screen derives from it;
 * nothing else keeps a copy.
 */
data class MusicLists(
    val playlists: Playlists,
    /**
     * Results lists by serial. Holds exactly the one on screen ([shownResults])
     * and, if different, the one still playing, pruned on every
     * [MusicStore.showResults] so it never grows. A results list that is
     * neither is gone, and a pointer never names a gone list.
     */
    val results: Map<Int, SongList>,
    /** 0 before the first search. */
    val shownResults: Int,
    val playing: Playing,
    /** The last tap's play call: one speaker, so one request, whichever page it came from. */
    val request: PlayRequest,
) {
    /** The songs [ref] names; Empty for a results serial that is gone or a playlist that is not (yet) readable. */
    fun songs(ref: ListRef): SongList = when (ref) {
        is ListRef.Results -> results[ref.serial] ?: SongList.Empty
        is ListRef.Saved -> (playlist(ref.id) as? PlaylistLookup.Found)?.playlist?.songs ?: SongList.Empty
    }

    /** The playlist [id], for its page. */
    fun playlist(id: PlaylistId): PlaylistLookup = when (playlists) {
        Playlists.Loading -> PlaylistLookup.Loading
        Playlists.Unreadable -> PlaylistLookup.Gone
        is Playlists.Readable -> playlists.all.firstOrNull { it.id == id }?.let { PlaylistLookup.Found(it) } ?: PlaylistLookup.Gone
    }

    /** Whether [ref] names a list the store holds: a results list still kept, or a playlist that is there. */
    fun holds(ref: ListRef): Boolean = when (ref) {
        is ListRef.Results -> ref.serial in results
        is ListRef.Saved -> playlist(ref.id) is PlaylistLookup.Found
    }

    /**
     * The pointer after a send to [ref] succeeded. The invariant (a pointer
     * names a list that exists) is checked here, when the send has landed, not
     * when it left: a delete or a new search can come while it is on the wire,
     * and a pointer to a gone list would read as an empty list, with no Next.
     */
    fun sentFrom(ref: ListRef, order: PlayOrder, sentIds: List<String>): MusicLists =
        copy(playing = if (holds(ref)) Playing.FromList(ref, order, sentIds) else Playing.None)

    companion object {
        val Initial = MusicLists(Playlists.Loading, emptyMap(), 0, Playing.None, PlayRequest.None)
    }
}

/** What a create did. */
sealed interface Created {
    data class Made(val id: PlaylistId) : Created

    /** Nothing was made: a blank name, or playlists that are not readable (still loading, or locked). */
    data object Refused : Created
}

sealed interface PlaylistLookup {
    /** The first read of the phone's storage is still on its way: a page restored after process death waits rather than backing out. */
    data object Loading : PlaylistLookup

    data class Found(val playlist: Playlist) : PlaylistLookup

    /** Deleted, never existed, or the store cannot read its playlists. */
    data object Gone : PlaylistLookup
}

/**
 * Song lists on the phone, and the speaker's playing list as far as the phone
 * knows it. The R1's counterpart of mytube's cast queue: one long-lived store,
 * edits shown at once, the slow parts (the phone's storage, the speaker) behind it.
 *
 * What it hides, and why it is one class:
 * - **Storage.** Playlists load once at construction; every change is shown at
 *   once and saved by one collector that writes the *latest* value, conflated,
 *   so two quick edits can never land in the wrong order and a save is
 *   idempotent. Nothing re-reads storage per action. An [Playlists.Unreadable]
 *   read is never overwritten.
 * - **The pointer.** [play], [shuffle] and [next] record what was sent, through
 *   [MusicLists.sentFrom]; it, [delete] and [showResults] keep the invariant
 *   that the pointer names a list that exists.
 * - **Next.** [canNext] and [next] read one pure function, [nextMove], over the
 *   pointer, its list and the latest reading. The mini player and the Control
 *   card hold no Next logic of their own, and nothing else may call
 *   `SpeakerRepository.next` (`OneNextGuardTest`).
 *
 * The public non-suspend methods are called from the main thread and update
 * [state] atomically; speaker sends are serialized by one [Mutex], so the
 * pointer always describes the last send that succeeded.
 */
class MusicStore(
    private val speaker: SpeakerRepository,
    private val preferences: PreferencesRepository,
    private val feed: SpeakerFeed,
    private val scope: CoroutineScope,
    private val random: Random,
    private val nowMillis: () -> Long,
) {
    private val sending = Mutex()
    private val lists = MutableStateFlow(MusicLists.Initial)

    val state: StateFlow<MusicLists> = lists.asStateFlow()

    /**
     * Whether a Next would do anything, from the latest reading: what both
     * cards draw Next from. A stale reading counts (the card still shows the
     * song); connecting and offline are false.
     */
    val canNext: StateFlow<Boolean> = combine(feed.view, lists) { view, held ->
        mediaOf(view)?.let { moveFor(held, it) != NextMove.None } ?: false
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        scope.launch { load() }
        scope.launch { saveLatest() }
    }

    // Lists.

    /** A finished search's songs become the results on screen. A failed play mark is let go: it named the old list. */
    fun showResults(tracks: List<Track>): ListRef.Results {
        val serial = lists.value.shownResults + 1
        lists.update { held ->
            val playingSerial = ((held.playing as? Playing.FromList)?.ref as? ListRef.Results)?.serial
            val keep = setOfNotNull(serial, playingSerial)
            held.copy(
                results = (held.results + (serial to SongList.of(tracks))).filterKeys { it in keep },
                shownResults = serial,
                request = if (held.request is PlayRequest.Failed) PlayRequest.None else held.request,
            )
        }
        return ListRef.Results(serial)
    }

    /** The failed play mark names a row of a list that is no longer on screen: let it go. */
    fun clearFailedPlay() {
        lists.update { if (it.request is PlayRequest.Failed) it.copy(request = PlayRequest.None) else it }
    }

    /**
     * Applies [edit] to [ref]'s songs. A playlist edit is saved, a results edit
     * is not (results are never stored). An edit to the list playing is sent to
     * the speaker as what follows the current song ([sendUpcoming]), which it
     * takes when that song ends: the song playing is never interrupted, and a
     * removed song is not played when it ends on its own.
     * An edit that changes nothing ([SongList.edited] returned the same
     * instance) changes nothing at all.
     */
    fun edit(ref: ListRef, edit: ListEdit) {
        val before = lists.value
        lists.update { held ->
            if (ref is ListRef.Results && ref.serial !in held.results) return@update held
            val songs = held.songs(ref)
            val edited = songs.edited(edit)
            if (edited === songs) return@update held
            when (ref) {
                is ListRef.Results -> held.copy(results = held.results + (ref.serial to edited))
                is ListRef.Saved -> held.withPlaylist(ref.id) { it.copy(songs = edited) }
            }
        }
        val after = lists.value
        if (after !== before && (after.playing as? Playing.FromList)?.ref == ref) scope.launch { sendUpcoming() }
    }

    // Playlists.

    /**
     * A new playlist holding [songs], newest first. [typed] is the name as the
     * person typed it: every create and rename cleans it here, by
     * [cleanPlaylistName], and a blank one makes nothing.
     */
    fun create(typed: String, songs: List<Track>): Created {
        val name = cleanPlaylistName(typed)
        if (name.isEmpty() || lists.value.playlists !is Playlists.Readable) return Created.Refused
        val now = nowMillis()
        val id = PlaylistId("p$now${random.nextInt(0x10000).toString(16)}")
        lists.update { held ->
            val playlists = held.playlists as? Playlists.Readable ?: return@update held
            held.copy(playlists = playlists.copy(all = listOf(Playlist(id, name, SongList.of(songs), now)) + playlists.all))
        }
        return Created.Made(id)
    }

    /** [typed] is cleaned as [create] cleans it; a blank one keeps the old name. */
    fun rename(id: PlaylistId, typed: String) {
        val name = cleanPlaylistName(typed)
        if (name.isEmpty()) return
        lists.update { held -> held.withPlaylist(id) { it.copy(name = name) } }
    }

    /** Gone from the phone. If it was the playing list, the pointer is let go: Next becomes the speaker's own. */
    fun delete(id: PlaylistId) {
        lists.update { held ->
            val playlists = held.playlists as? Playlists.Readable ?: return@update held
            val pointsHere = (held.playing as? Playing.FromList)?.ref == ListRef.Saved(id)
            held.copy(
                playlists = playlists.copy(all = playlists.all.filter { it.id != id }),
                playing = if (pointsHere) Playing.None else held.playing,
            )
        }
    }

    // The speaker.

    /**
     * Plays [ref] from row [index] in its own order, windowed by [queueAround].
     * Marks [PlayRequest.Starting] at once; a later tap wins over this one's
     * answer. On success the pointer is `FromList(ref, Listed, sentIds)`; on
     * failure it is left as it was, because the speaker still plays what it played.
     */
    suspend fun play(ref: ListRef, index: Int): CommandOutcome = start(ref, PlayOrder.Listed, index)

    /**
     * Plays [ref] from position [index] of [order], the order it already plays in:
     * a row tapped on the Now playing page continues the same shuffle, it does not
     * draw a new one. The pointer keeps [order].
     */
    suspend fun playFrom(ref: ListRef, order: PlayOrder, index: Int): CommandOutcome = start(ref, order, index)

    /** Plays [ref] in a new random order from its first song. The list itself is not reordered. */
    suspend fun shuffle(ref: ListRef): CommandOutcome {
        val songs = lists.value.songs(ref)
        if (songs.isEmpty) return CommandOutcome.Done
        return start(ref, PlayOrder.Shuffled(songs.tracks.map { it.id }.shuffled(random)), 0)
    }

    /**
     * The Next button, wherever it is drawn. Decides from the reading in hand
     * when the lock is taken, not the one the button was drawn from:
     * - [NextMove.None]: nothing is sent.
     * - [NextMove.Speaker]: `speaker.next()`; the pointer is unchanged, its
     *   window still holds the song the speaker moves to.
     * - [NextMove.Resend]: `speaker.play(queue)`; the pointer's `sentIds` become the new window.
     *
     * A press that sent something waits for the reading that follows it, still
     * under the lock, so a second press decides over the speaker's answer
     * (downloading, which is [NextMove.None]) and a double tap skips one song.
     */
    suspend fun next(): CommandOutcome = sending.withLock {
        val held = lists.value
        val media = mediaOf(feed.view.value) ?: return@withLock CommandOutcome.Done
        when (val move = moveFor(held, media)) {
            NextMove.None -> CommandOutcome.Done
            NextMove.Speaker -> command { speaker.next() }.also { settle(it) }
            is NextMove.Resend -> {
                val outcome = command { speaker.play(move.queue.tracks, move.queue.startIndex) }
                val pointer = held.playing
                if (outcome is CommandOutcome.Done && pointer is Playing.FromList) {
                    lists.update { it.sentFrom(pointer.ref, pointer.order, move.queue.tracks.map { track -> track.id }) }
                }
                settle(outcome)
                outcome
            }
        }
    }

    /** A new address is a new speaker: what was sent to the old one says nothing about this one. */
    fun speakerChanged() {
        lists.update { it.copy(playing = Playing.None, request = PlayRequest.None) }
    }

    // Internals.

    private suspend fun start(ref: ListRef, order: PlayOrder, startAt: Int): CommandOutcome {
        val first = playSequence(lists.value.songs(ref), order).getOrNull(startAt) ?: return CommandOutcome.Done
        val starting = PlayRequest.Starting(first.id)
        lists.update { it.copy(request = starting) }
        val outcome = send(ref, order, startAt)
        lists.update { held ->
            if (held.request != starting) {
                held
            } else {
                held.copy(
                    request = when (outcome) {
                        CommandOutcome.Done -> PlayRequest.None
                        is CommandOutcome.Failed -> PlayRequest.Failed(first.id, outcome.error)
                    },
                )
            }
        }
        return outcome
    }

    private suspend fun send(ref: ListRef, order: PlayOrder, startAt: Int): CommandOutcome = sending.withLock {
        val window = queueAround(playSequence(lists.value.songs(ref), order), startAt)
        val outcome = command { speaker.play(window.tracks, window.startIndex) }
        if (outcome is CommandOutcome.Done) {
            lists.update { it.sentFrom(ref, order, window.tracks.map { track -> track.id }) }
        }
        outcome
    }

    /**
     * Tells the speaker what now follows the song playing. The list is read under the lock, so
     * a burst of edits sends what they add up to, and the last one sent is the latest. Nothing is
     * sent when it is what the speaker was last told ([Playing.FromList.sentIds], not its reading,
     * which shows an edit only once the song has ended). A refusal (an R1 built before
     * `/api/media/queue`) is left silent: Next still re-sends the edited list.
     */
    private suspend fun sendUpcoming() = sending.withLock {
        val held = lists.value
        val pointer = held.playing as? Playing.FromList ?: return@withLock
        val media = mediaOf(feed.view.value) ?: return@withLock
        if (media.status !in SOUNDING) return@withLock
        val upcoming = upcomingAfter(pointer, held.songs(pointer.ref), media.videoId) ?: return@withLock
        val through = pointer.sentIds.take(pointer.sentIds.indexOf(media.videoId) + 1)
        if (pointer.sentIds.drop(through.size) == upcoming.map { it.id }) return@withLock
        if (command { speaker.queue(upcoming) } !is CommandOutcome.Done) return@withLock
        lists.update { it.sentFrom(pointer.ref, pointer.order, through + upcoming.map { track -> track.id }) }
    }

    private suspend fun settle(outcome: CommandOutcome) {
        if (outcome !is CommandOutcome.Done) return
        val before = feed.answers.value
        feed.refresh()
        withTimeoutOrNull(SETTLE_MILLIS) { feed.answers.first { it > before } }
    }

    private fun moveFor(held: MusicLists, media: MediaState): NextMove {
        val playing = held.playing
        val songs = if (playing is Playing.FromList) held.songs(playing.ref) else SongList.Empty
        return nextMove(playing, songs, media)
    }

    private fun mediaOf(view: SpeakerView): MediaState? = when (view) {
        is SpeakerView.Online -> view.snapshot.media
        is SpeakerView.Stale -> view.snapshot.media
        SpeakerView.Connecting, is SpeakerView.Offline -> null
    }

    private suspend fun load() {
        val read = try {
            preferences.playlists()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            StoredPlaylists.Unreadable(backedUp = false)
        }
        val loaded = when (read) {
            is StoredPlaylists.Readable -> Playlists.Readable(read.playlists, saveFailed = false, keptAside = false)
            is StoredPlaylists.Unreadable ->
                if (read.backedUp) Playlists.Readable(emptyList(), saveFailed = false, keptAside = true) else Playlists.Unreadable
        }
        lists.update { it.copy(playlists = loaded) }
    }

    private suspend fun saveLatest() {
        lists.map { (it.playlists as? Playlists.Readable)?.all }
            .filterNotNull()
            .distinctUntilChanged()
            .drop(1)
            .conflate()
            .collect { all ->
                val saved = try {
                    preferences.savePlaylists(all)
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
                lists.update { held ->
                    val playlists = held.playlists as? Playlists.Readable ?: return@update held
                    held.copy(playlists = playlists.copy(saveFailed = !saved))
                }
            }
    }

    private fun MusicLists.withPlaylist(id: PlaylistId, change: (Playlist) -> Playlist): MusicLists {
        val playlists = playlists as? Playlists.Readable ?: return this
        if (playlists.all.none { it.id == id }) return this
        return copy(playlists = playlists.copy(all = playlists.all.map { if (it.id == id) change(it) else it }))
    }

    private companion object {
        const val SETTLE_MILLIS = 1_000L

        /** A song is loaded on the speaker, so it has a list to walk when the song ends. */
        val SOUNDING = setOf(MediaStatus.Downloading, MediaStatus.Playing, MediaStatus.Paused)
    }
}
