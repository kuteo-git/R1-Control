package com.r1control.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.ListRef
import com.r1control.app.domain.model.PlayOrder
import com.r1control.app.domain.model.PlaylistId
import com.r1control.app.domain.model.SongList
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.model.songKey
import com.r1control.app.ui.components.Cover
import com.r1control.app.ui.components.ListDrag
import com.r1control.app.ui.components.R1Icon
import com.r1control.app.ui.components.TonalButton
import com.r1control.app.ui.components.rememberListDrag
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Radius
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens
import org.jetbrains.compose.ui.tooling.preview.Preview

class NowPlayingActions(
    val onPlay: (Int) -> Unit = {},
    val onMove: (Int, Int) -> Unit = { _, _ -> },
    val onRemove: (Track) -> Unit = {},
    /** A saved list's header leads to its page. */
    val onOpenPlaylist: (PlaylistId) -> Unit = {},
    val onFindSongs: () -> Unit = {},
)

/** The songs playing on the speaker, opened from the mini player or the Control card. */
@Composable
fun NowPlayingScreen(viewModel: NowPlayingViewModel, onOpenPlaylist: (PlaylistId) -> Unit, onFindSongs: () -> Unit) {
    val page by viewModel.state.collectAsState()
    val mark by viewModel.mark.collectAsState()
    NowPlayingContent(
        page, mark,
        NowPlayingActions(
            onPlay = viewModel::onPlay, onMove = viewModel::onMove, onRemove = viewModel::onRemove,
            onOpenPlaylist = onOpenPlaylist, onFindSongs = onFindSongs,
        ),
    )
}

@Composable
fun NowPlayingContent(page: NowPlayingPage, mark: SongMark, actions: NowPlayingActions) {
    val strings = LocalStrings.current
    when (page) {
        NowPlayingPage.Nothing -> Column(
            Modifier.fillMaxSize().padding(Space.Gutter),
            verticalArrangement = Arrangement.spacedBy(Space.Large, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                strings.nowPlayingNothing,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TonalButton(strings.findSongs, actions.onFindSongs, icon = R1Icons.Search)
        }
        is NowPlayingPage.FromApp -> {
            val songs = remember(page.sequence) { SongList.of(page.sequence) }
            PlayingList(songs, page.currentId) { list, drag ->
                item(key = "header") {
                    val source = page.source
                    val line = when (source) {
                        is PlaySource.Playlist -> strings.playingFromPlaylist(if (source.hasName) source.name else strings.untitledPlaylist)
                        PlaySource.SearchResults -> strings.playingFromResults
                    }
                    Header(
                        line, songs.tracks.firstOrNull { it.id == page.currentId }, page.shuffled,
                        onClick = (source as? PlaySource.Playlist)?.let { { actions.onOpenPlaylist(it.id) } },
                    )
                }
                songItems(
                    strings, songs, mark, drag, list,
                    onPlay = actions.onPlay,
                    onMove = if (page.canReorder) actions.onMove else null,
                    onRemove = actions.onRemove,
                    onAdd = null,
                )
            }
        }
        is NowPlayingPage.FromSpeaker -> {
            val songs = remember(page.queue) { SongList.of(page.queue) }
            PlayingList(songs, page.currentId) { _, _ ->
                item(key = "header") {
                    Header(strings.playingFromSpeaker, songs.tracks.firstOrNull { it.id == page.currentId }, shuffled = false, onClick = null)
                }
                items(songs.tracks, key = ::songKey) { track ->
                    TrackRow(track, mark, Modifier.fillMaxWidth(), onClick = null)
                }
            }
        }
    }
}

/**
 * The list both kinds of page share. It opens with the current song in view,
 * through the list's initial index, so the first frame is already there.
 */
@Composable
private fun PlayingList(
    songs: SongList,
    currentId: String,
    content: LazyListScope.(LazyListState, ListDrag<Track>) -> Unit,
) {
    // Item 0 is the header and song n is item n + 1: from the third song on, the one before the current one is the top row.
    val at = songs.indexOf(currentId)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = if (at < 3) 0 else at)
    val drag = rememberListDrag(songs.tracks)
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.Gutter, vertical = Space.Small),
    ) { content(list, drag) }
}

@Composable
private fun Header(line: String, current: Track?, shuffled: Boolean, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.Small)
            .clip(RoundedCornerShape(Radius.Row))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Large),
    ) {
        Cover(current?.thumbnailUrl.orEmpty(), 96.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Lines)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                if (shuffled) R1Icon(R1Icons.Shuffle, null, tint = Tokens.Accent, size = 20.dp)
                Text(line, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (current != null) {
                Text(
                    current.title, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onClick != null) R1Icon(R1Icons.ChevronRight, null, tint = Tokens.OnGroundMuted)
    }
}

// ---- previews ----

@Composable
private fun PreviewHost(strings: Strings, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStrings provides strings) {
        R1Theme { Box(Modifier.background(Tokens.Ground)) { content() } }
    }
}

private val tracks = listOf(
    Track("a", "Như Một Người Rưng", "OSAD & Hà An Huy", "4:10", ""),
    Track("b", "Không Môn Đăng Hộ Đối (rumba version, a very long title that must be cut)", "Nguyễn Vĩ", "5:02", ""),
    Track("c", "Tôi Thua Số Đuôi", "Văn Hường", "3:40", ""),
)
private val playlist = PlaySource.Playlist(PlaylistId("p1"), "Nhạc xưa", hasName = true)
private val ref = ListRef.Saved(PlaylistId("p1"))

@Preview
@Composable
private fun NowPlayingNothingPreview() = PreviewHost(EnglishStrings) {
    NowPlayingContent(NowPlayingPage.Nothing, SongMark.None, NowPlayingActions())
}

@Preview
@Composable
private fun NowPlayingListedPreview() = PreviewHost(EnglishStrings) {
    NowPlayingContent(
        NowPlayingPage.FromApp(playlist, ref, PlayOrder.Listed, tracks, "b"), SongMark.OnSpeaker("b"), NowPlayingActions(),
    )
}

@Preview
@Composable
private fun NowPlayingShuffledPreview() = PreviewHost(EnglishStrings) {
    NowPlayingContent(
        NowPlayingPage.FromApp(PlaySource.SearchResults, ListRef.Results(1), PlayOrder.Shuffled(listOf("c", "a", "b")), listOf(tracks[2], tracks[0], tracks[1]), "a"),
        SongMark.OnSpeaker("a"), NowPlayingActions(),
    )
}

@Preview
@Composable
private fun NowPlayingFromSpeakerPreview() = PreviewHost(EnglishStrings) {
    NowPlayingContent(NowPlayingPage.FromSpeaker(tracks, "a"), SongMark.OnSpeaker("a"), NowPlayingActions())
}

@Preview
@Composable
private fun NowPlayingVietnamesePreview() = PreviewHost(VietnameseStrings) {
    NowPlayingContent(
        NowPlayingPage.FromApp(playlist, ref, PlayOrder.Listed, tracks, "c"), SongMark.OnSpeaker("c"), NowPlayingActions(),
    )
}
