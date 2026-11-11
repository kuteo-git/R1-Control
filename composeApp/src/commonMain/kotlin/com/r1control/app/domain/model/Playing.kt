package com.r1control.app.domain.model

import com.r1control.app.domain.repository.SpeakerException

// The playing list and the one Next decision.
//
// The phone does not keep a second copy of the playing list. It keeps a
// pointer: which of its lists was sent, in what order, and the ids it sent.
// The list's songs stay with the list's one owner (a playlist, or the results),
// so an edit made on that list's page is an edit of the playing list with no
// forwarding. The cursor is not stored either: it is the speaker's `video_id`,
// found in the list (unique by SongList's invariant).

/** Which phone-side list a pointer names. */
sealed interface ListRef {
    /** One search's results. [serial] grows per search, so an older answer still playing is not the one on screen. */
    data class Results(val serial: Int) : ListRef

    data class Saved(val id: PlaylistId) : ListRef
}

/**
 * The order a list is played in. Shuffle is an ordering, not a mode: it is
 * chosen once, at the press, and Next stays inside it.
 */
sealed interface PlayOrder {
    /** The list's own order, read live: a drag on the list's page reorders what plays next. */
    data object Listed : PlayOrder

    /**
     * A shuffled order of the list's ids, fixed at the press. Read through
     * [playSequence]: a song removed from the list drops out; a song added after
     * the press plays at the end. A drag on the list's page changes the list,
     * not this order.
     */
    data class Shuffled(val ids: List<String>) : PlayOrder
}

/** What the phone last told the speaker to play from one of its lists. */
sealed interface Playing {
    /** No app list: nothing sent since launch, the speaker changed, or the list was deleted. Next is the speaker's own. */
    data object None : Playing

    data class FromList(
        val ref: ListRef,
        val order: PlayOrder,
        /** The ids of the window last POSTed to `/api/media/play`, in its order. "Is the speaker playing our list?" is "is its song in here?". */
        val sentIds: List<String>,
    ) : Playing
}

/** What a press of Next does. One value, read by the mini player and the Control card alike. */
sealed interface NextMove {
    /** Nothing to go to: Next is not drawn. */
    data object None : NextMove

    /** `POST /api/media/next`: the speaker's own queue already holds the right next songs. */
    data object Speaker : NextMove

    /** `POST /api/media/play` with [queue]: the list was edited, or the 30-song window has run out. */
    data class Resend(val queue: PlayQueue) : NextMove
}

/** [songs] in the order [order] plays them. */
fun playSequence(songs: SongList, order: PlayOrder): List<Track> = when (order) {
    PlayOrder.Listed -> songs.tracks
    is PlayOrder.Shuffled -> {
        val byId = songs.tracks.associateBy { it.id }
        val shuffled = order.ids.mapNotNull { byId[it] }
        val placed = order.ids.toSet()
        shuffled + songs.tracks.filter { it.id !in placed }
    }
}

/**
 * The one Next decision. [songs] are the songs of [playing]'s list (Empty for
 * [Playing.None]); [media] is the latest reading.
 *
 * An edit applies from the next song and never interrupts this one: Next
 * re-sends the edited list from the next song, and the speaker's own next is
 * used when no app list plays or when it would already do the right thing.
 */
fun nextMove(playing: Playing, songs: SongList, media: MediaState): NextMove {
    // Next is offered only over a song that has started; a downloading one has none to skip.
    if (media.status != MediaStatus.Playing && media.status != MediaStatus.Paused) return NextMove.None

    // No app list, or the speaker has moved on to a song the phone did not send
    // (a voice request, or the server continuing with related songs): the speaker's own.
    val speakerFallback = if (media.hasNext) NextMove.Speaker else NextMove.None
    val current = media.videoId
    val (sequence, anchor) = placeAfter(playing, songs, current) ?: return speakerFallback
    val rest = sequence.drop(anchor)
    if (rest.isEmpty()) return NextMove.None

    // The speaker's own next is right only if its queue already holds the list's next songs, in order.
    // Unknown or different: re-send, which is always correct and costs a download the speaker would make anyway.
    val queue = media.queue.map { it.id }
    val k = queue.indexOf(current)
    val speakerRest = if (k >= 0) queue.drop(k + 1) else emptyList()
    if (speakerRest.isNotEmpty() && rest.map { it.id }.take(speakerRest.size) == speakerRest) return NextMove.Speaker

    return NextMove.Resend(queueAround(sequence, anchor))
}

/**
 * What follows the song playing, [current], in [playing]'s list as it is now: what the
 * speaker should walk to when this song ends, at most a queue's worth. Null when the
 * phone's list is not what plays: no app list, or the speaker moved on to a song the phone
 * did not send. [nextMove] goes to its first song.
 */
fun upcomingAfter(playing: Playing, songs: SongList, current: String): List<Track>? =
    placeAfter(playing, songs, current)?.let { (sequence, anchor) -> sequence.drop(anchor).take(PLAY_QUEUE_MAX - 1) }

/** [playing]'s list in play order, and where in it the song after [current] is. */
private fun placeAfter(playing: Playing, songs: SongList, current: String): Pair<List<Track>, Int>? {
    if (playing !is Playing.FromList || current !in playing.sentIds) return null
    val sequence = playSequence(songs, playing.order)
    val at = sequence.indexOfFirst { it.id == current }
    return sequence to if (at >= 0) at + 1 else placeOfRemoved(current, playing.sentIds, sequence)
}

/**
 * Where the next song starts in [sequence] once the song playing, [current],
 * has been taken out of it: the place it left. The window that was sent is
 * the only record of that place, so it is found from the songs sent around it
 * that are still in the list:
 * - just after the nearest one sent before it, so a song added after the press
 *   (at the end) or beyond the 30-song window still follows;
 * - else at the nearest one sent after it (it was the first of the window);
 * - else, with every sent song gone, at the start: none of what is left has
 *   played, because the speaker plays only what it was sent.
 */
private fun placeOfRemoved(current: String, sentIds: List<String>, sequence: List<Track>): Int {
    val sentAt = sentIds.indexOf(current)
    val position = sequence.withIndex().associate { (i, track) -> track.id to i }
    sentIds.take(sentAt).asReversed().firstNotNullOfOrNull { position[it] }?.let { return it + 1 }
    return sentIds.drop(sentAt + 1).firstNotNullOfOrNull { position[it] } ?: 0
}

/**
 * Whether the speaker plays from [ref] now, which is what "Playing from this
 * playlist" says. Not "the list last sent": the speaker may have moved to a
 * song asked for by voice, run past the list into related songs, or stopped.
 * So it is derived from the reading, as [nextMove] is: the pointer names
 * [ref], and the speaker's song is one the phone sent and has started or is
 * starting.
 */
fun playsFrom(playing: Playing, ref: ListRef, media: MediaState): Boolean =
    playing is Playing.FromList &&
        playing.ref == ref &&
        media.videoId in playing.sentIds &&
        when (media.status) {
            MediaStatus.Playing, MediaStatus.Paused, MediaStatus.Downloading -> true
            MediaStatus.Idle, MediaStatus.Stopped, MediaStatus.Unknown -> false
        }

/** The play call for a tapped row, while it is on its way or after the speaker turned it down. */
sealed interface PlayRequest {
    data object None : PlayRequest
    data class Starting(val trackId: String) : PlayRequest
    data class Failed(val trackId: String, val error: SpeakerException) : PlayRequest
}
