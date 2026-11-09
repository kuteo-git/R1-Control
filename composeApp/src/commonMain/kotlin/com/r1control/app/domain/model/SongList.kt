package com.r1control.app.domain.model

/**
 * [this] with the item at [from] taken out and put back at [to]; unchanged when
 * either index is out of range or they are equal. The only reorder in the app:
 * the drag's live order, a song list's Move and the news categories all call it.
 */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    val out = toMutableList()
    out.add(to, out.removeAt(from))
    return out
}

/** [this] without the item at [index]; unchanged when [index] is out of range. */
fun <T> List<T>.without(index: Int): List<T> {
    if (index !in indices) return this
    return toMutableList().also { it.removeAt(index) }
}

/** One change a person makes to a song list from its rows. */
sealed interface ListEdit {
    /** The drag's release: indexes in the order the list had when the row was picked up. */
    data class Move(val from: Int, val to: Int) : ListEdit

    /** The X. By id, not index: the id names exactly one row (see [SongList]). */
    data class Remove(val id: String) : ListEdit

    /** "Add" on a row, or "Add all" on the results. Songs already there are skipped. */
    data class Add(val songs: List<Track>) : ListEdit
}

/**
 * The songs of a list the phone keeps: a playlist, or the search results on
 * screen.
 *
 * **Invariant: no two songs share an id.** The constructor is private and
 * [of] keeps the first of any repeat, so the invariant cannot be broken from
 * outside. It is what makes three other things simple:
 * - a row's lazy key is the song's id ([songKey]), unique by construction;
 * - the speaker's `video_id` names exactly one row, so the playing list's
 *   cursor is derived from the speaker and never stored ([nextMove]);
 * - adding a song that is already there is a no-op ([ListEdit.Add]).
 *
 * Search results already satisfy it ([mixInterleaved] drops repeats); a stored
 * playlist that somehow repeats a song reads with the repeat dropped.
 */
@ConsistentCopyVisibility
data class SongList private constructor(val tracks: List<Track>) {
    val size: Int get() = tracks.size
    val isEmpty: Boolean get() = tracks.isEmpty()

    /** The X is disabled, and [ListEdit.Remove] refuses, when this is false: a list never loses its last song to an X. */
    val canRemove: Boolean get() = tracks.size > 1

    fun contains(id: String): Boolean = tracks.any { it.id == id }

    /** The position of [id], or -1. */
    fun indexOf(id: String): Int = tracks.indexOfFirst { it.id == id }

    /**
     * The list after [edit]. Returns `this` (the same instance) when the edit
     * changes nothing (an out-of-range move, the last song's X, an Add of songs
     * all present), so a caller can skip a save with `===`.
     */
    fun edited(edit: ListEdit): SongList = when (edit) {
        is ListEdit.Move -> {
            val reordered = tracks.moved(edit.from, edit.to)
            if (reordered === tracks) this else SongList(reordered)
        }
        is ListEdit.Remove -> {
            val at = indexOf(edit.id)
            if (!canRemove || at < 0) this else SongList(tracks.without(at))
        }
        is ListEdit.Add -> {
            val appended = edit.songs.filter { !contains(it.id) }.distinctBy { it.id }
            if (appended.isEmpty()) this else SongList(tracks + appended)
        }
    }

    companion object {
        val Empty: SongList = SongList(emptyList())

        /** The only way in: repeats (same id) after the first are dropped. */
        fun of(tracks: List<Track>): SongList = SongList(tracks.distinctBy { it.id })
    }
}

/**
 * A song row's lazy key. Unique within a [SongList] by its invariant, and
 * prefixed so it can never meet the Music tab's other keys ("query", "hint",
 * "recents-title", a recent's key, "playlist:<id>", "shelf").
 */
fun songKey(track: Track): String = "song:${track.id}"
