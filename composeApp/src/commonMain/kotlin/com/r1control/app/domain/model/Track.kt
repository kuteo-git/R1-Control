package com.r1control.app.domain.model

/**
 * A song the speaker can play.
 *
 * [duration] is the label as the R1 sends it (`10:05`), kept as text because it
 * is shown and sent back unchanged. Empty strings mean the wire omitted a field.
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String,
    val thumbnailUrl: String,
) {
    val hasArtist: Boolean get() = artist.isNotEmpty()
    val hasDuration: Boolean get() = duration.isNotEmpty()
    val hasThumbnail: Boolean get() = thumbnailUrl.isNotEmpty()
}
