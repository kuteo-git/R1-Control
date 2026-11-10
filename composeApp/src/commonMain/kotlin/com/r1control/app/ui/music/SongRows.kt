package com.r1control.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.SongList
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.model.songKey
import com.r1control.app.ui.components.Cover
import com.r1control.app.ui.components.IconTap
import com.r1control.app.ui.components.ListDrag
import com.r1control.app.ui.components.R1Icon
import com.r1control.app.ui.components.editableItems
import com.r1control.app.ui.components.tappable
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.describe
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.Radius
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens

/**
 * [songs] as editable rows: handle, the song (a tap plays from it), an optional
 * add button, and the X, disabled at one song. Both song lists draw the same
 * rows through this, so the search results and a playlist cannot drift apart.
 * Keys are [songKey], unique by the list's invariant.
 *
 * [onAdd] null draws no add button: a playlist's own page has no use for one.
 * [onMove] null draws no drag handle: a shuffled playing list has no order to drag.
 */
fun LazyListScope.songItems(
    strings: Strings,
    songs: SongList,
    mark: SongMark,
    drag: ListDrag<Track>,
    list: LazyListState,
    onPlay: (index: Int) -> Unit,
    onMove: ((from: Int, to: Int) -> Unit)?,
    onRemove: (Track) -> Unit,
    onAdd: ((Track) -> Unit)?,
) {
    editableItems(
        items = songs.tracks,
        key = ::songKey,
        drag = drag,
        list = list,
        enabled = true,
        onMove = onMove ?: { _, _ -> },
        onRemove = onRemove,
        canRemove = songs.canRemove,
        handleLabel = { track -> strings.reorderSong(track.title) },
        removeLabel = { track -> strings.removeSong(track.title) },
        handle = onMove != null,
    ) { track ->
        // A tap is not possible mid-drag, so the index is read from the list, not from the drag's order.
        TrackRow(track, mark, Modifier.weight(1f)) { onPlay(songs.indexOf(track.id)) }
        if (onAdd != null) {
            IconTap(R1Icons.PlaylistAdd, strings.addToPlaylist, { onAdd(track) }, tint = Tokens.OnGroundMuted)
        }
    }
}

/** [onClick] null is a row that only shows: the speaker's own queue cannot be played from here. */
@Composable
internal fun TrackRow(track: Track, mark: SongMark, modifier: Modifier, onClick: (() -> Unit)?) {
    val strings = LocalStrings.current
    val lit = mark.isOnSpeaker(track.id)
    Row(
        modifier
            .clip(RoundedCornerShape(Radius.Row))
            .background(if (lit) Tokens.Accent.copy(alpha = 0.16f) else Color.Transparent)
            .then(if (onClick != null) Modifier.tappable(role = Role.Button, onClickLabel = strings.playOnSpeaker, onClick = onClick) else Modifier)
            .padding(horizontal = Space.Small, vertical = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Cover(track.thumbnailUrl, 56.dp)
            when {
                mark == SongMark.Starting(track.id) -> CoverMark(strings.startingOnSpeaker) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = Tokens.OnGround)
                }
                lit -> CoverMark(strings.playingOnSpeaker) {
                    R1Icon(R1Icons.Volume, null, tint = Tokens.OnGround, size = 26.dp)
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = Space.Medium), verticalArrangement = Arrangement.spacedBy(Space.Lines)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (lit) Tokens.Accent else Color.Unspecified,
                // Two lines: beside the handle, the add button and the X, one line held ~12 characters (UX-03, measured on the iPhone 16e).
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // The artist and the length share a line, to leave the handle, the add button and the X their 48 dp.
            if (track.hasArtist || track.hasDuration) {
                Text(
                    listOf(track.artist, track.duration).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (lit) Tokens.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (mark is SongMark.Failed && mark.trackId == track.id) {
                Text(strings.describe(mark.error), style = MaterialTheme.typography.bodySmall, color = Tokens.Error)
            }
        }
    }
}

/** A dark veil over a cover with a glyph on it; [description] is what a screen reader says for it. */
@Composable
private fun CoverMark(description: String, glyph: @Composable () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(Radius.Cover))
            .background(Tokens.Scrim.copy(alpha = 0.55f))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { glyph() }
}
