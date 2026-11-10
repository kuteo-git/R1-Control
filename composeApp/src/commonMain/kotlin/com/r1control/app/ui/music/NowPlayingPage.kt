package com.r1control.app.ui.music

import com.r1control.app.domain.model.ListRef
import com.r1control.app.domain.model.NowPlaying
import com.r1control.app.domain.model.PlayOrder
import com.r1control.app.domain.model.PlaylistId
import com.r1control.app.domain.model.Playing
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.model.playSequence
import com.r1control.app.domain.model.playsFrom
import com.r1control.app.domain.usecase.MusicLists
import com.r1control.app.domain.usecase.PlaylistLookup
import com.r1control.app.domain.usecase.SpeakerView

/** Where the songs of a [NowPlayingPage.FromApp] come from, in the words the header uses. */
sealed interface PlaySource {
    data class Playlist(val id: PlaylistId, val name: String, val hasName: Boolean) : PlaySource
    data object SearchResults : PlaySource
}

/** What the Now playing page shows. One value: the page draws nothing the derivation did not decide. */
sealed interface NowPlayingPage {
    /** The speaker is not on music: an empty page that leads to the search. */
    data object Nothing : NowPlayingPage

    /**
     * The speaker plays a list this phone sent. [sequence] is the order it plays
     * in ([order]), so a tapped row plays from its position in that same order.
     * Results stay in the store while they play, so a cleared search still has its list.
     */
    data class FromApp(
        val source: PlaySource,
        val ref: ListRef,
        val order: PlayOrder,
        val sequence: List<Track>,
        val currentId: String,
    ) : NowPlayingPage {
        val shuffled: Boolean get() = order is PlayOrder.Shuffled

        /** The list's own order is the playing order only when listed, so only then can a drag reorder what plays next. */
        val canReorder: Boolean get() = order == PlayOrder.Listed
    }

    /** Music the phone did not send (a voice request, related songs): the speaker's own queue, read only. */
    data class FromSpeaker(val queue: List<Track>, val currentId: String) : NowPlayingPage
}

fun nowPlayingPageOf(lists: MusicLists, view: SpeakerView): NowPlayingPage {
    val snapshot = when (view) {
        is SpeakerView.Online -> view.snapshot
        is SpeakerView.Stale -> view.snapshot
        SpeakerView.Connecting, is SpeakerView.Offline -> return NowPlayingPage.Nothing
    }
    val now = snapshot.nowPlaying as? NowPlaying.Music ?: return NowPlayingPage.Nothing
    val media = snapshot.media
    val playing = lists.playing
    if (playing is Playing.FromList && playsFrom(playing, playing.ref, media)) {
        val sequence = playSequence(lists.songs(playing.ref), playing.order)
        if (sequence.isNotEmpty()) {
            val source = when (val ref = playing.ref) {
                is ListRef.Results -> PlaySource.SearchResults
                is ListRef.Saved -> (lists.playlist(ref.id) as? PlaylistLookup.Found)?.playlist
                    ?.let { PlaySource.Playlist(it.id, it.name, it.hasName) }
                    ?: return speakerPage(media.queue, media.videoId, now)
            }
            return NowPlayingPage.FromApp(source, playing.ref, playing.order, sequence, media.videoId)
        }
    }
    return speakerPage(media.queue, media.videoId, now)
}

private fun speakerPage(queue: List<Track>, currentId: String, now: NowPlaying.Music): NowPlayingPage.FromSpeaker {
    // The speaker may report a song that is not in its own queue; the page still names it.
    val shown = if (queue.any { it.id == currentId }) queue else listOf(Track(currentId, now.title, now.artist, "", now.coverUrl)) + queue
    return NowPlayingPage.FromSpeaker(shown, currentId)
}
