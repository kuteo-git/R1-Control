package com.r1control.app.domain.model

/** Made by the phone when a playlist is created ("p" + time + a random suffix); never shown. */
@kotlin.jvm.JvmInline
value class PlaylistId(val value: String)

data class Playlist(
    val id: PlaylistId,
    /** Cleaned by [cleanPlaylistName] before a create or rename; blank only if storage held a blank one. */
    val name: String,
    val songs: SongList,
    val createdAtMillis: Long,
) {
    /** The shelf card's picture: the first song's thumbnail, else a glyph. */
    val coverUrl: String get() = songs.tracks.firstOrNull()?.thumbnailUrl.orEmpty()
    val hasCover: Boolean get() = coverUrl.isNotEmpty()

    /** False draws the localised "Untitled playlist". */
    val hasName: Boolean get() = name.isNotEmpty()
}

/**
 * What the phone's storage answered for the playlists. Absent (a fresh install)
 * is [Readable] with an empty list. [Unreadable] is a stored value that does
 * not decode, which is **not** an empty list: a save from an empty one would
 * delete every playlist the phone had.
 */
sealed interface StoredPlaylists {
    data class Readable(val playlists: List<Playlist>) : StoredPlaylists

    /**
     * [backedUp]: the raw value was copied aside before this answer, so the
     * store may start from an empty list and its first save loses nothing.
     * False: the copy failed, and nothing may be saved over the stored value.
     */
    data class Unreadable(val backedUp: Boolean) : StoredPlaylists
}

/** The playlists as the store holds them. Newest first. */
sealed interface Playlists {
    /** The first read is on its way: no shelf, no picker yet. */
    data object Loading : Playlists

    /**
     * The stored value did not decode and could not be copied aside. The store
     * **never saves from here**: one bad read followed by one save would
     * delete every playlist. The shelf says so and offers nothing to create.
     */
    data object Unreadable : Playlists

    /**
     * [saveFailed]: the last write to the phone's storage threw, so a change
     * may not survive a restart. [keptAside]: this run began from an unreadable
     * stored value that was copied aside, and the shelf says so.
     */
    data class Readable(val all: List<Playlist>, val saveFailed: Boolean, val keptAside: Boolean) : Playlists
}

/** How many cards the Music tab's shelf shows before "More" appears. */
const val SHELF_MAX = 5

/** The Music tab's shelf: at most [SHELF_MAX] cards; [hasMore] draws "More" (top right) to the All playlists page. */
data class Shelf(val cards: List<Playlist>, val hasMore: Boolean) {
    val isEmpty: Boolean get() = cards.isEmpty()
}

fun shelfOf(playlists: List<Playlist>): Shelf = Shelf(playlists.take(SHELF_MAX), playlists.size > SHELF_MAX)

/** A typed name as it is kept: trimmed, inner whitespace collapsed. Empty means the dialog's confirm is disabled. */
fun cleanPlaylistName(text: String): String =
    text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
