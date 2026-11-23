package com.r1control.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.SongList
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.ui.Problem
import com.r1control.app.ui.components.Cover
import com.r1control.app.ui.components.PrimaryButton
import com.r1control.app.ui.components.ProblemBanner
import com.r1control.app.ui.components.TonalButton
import com.r1control.app.ui.components.rememberListDrag
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.i18n.describe
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens
import org.jetbrains.compose.ui.tooling.preview.Preview

class PlaylistActions(
    val onPlay: (Int) -> Unit = {},
    val onShuffle: () -> Unit = {},
    val onMove: (Int, Int) -> Unit = { _, _ -> },
    val onRemove: (Track) -> Unit = {},
    /** An empty playlist's way in: the Music tab's search, its field focused. */
    val onFindSongs: () -> Unit = {},
)

/**
 * A playlist's page. [onGone]: it was deleted here, or is no longer there, and
 * the page backs out. [onFindSongs]: an empty playlist's button, to the search.
 */
@Composable
fun PlaylistScreen(viewModel: PlaylistViewModel, onGone: () -> Unit, onFindSongs: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val mark by viewModel.mark.collectAsState()
    PlaylistContent(
        state, mark,
        PlaylistActions(
            onPlay = viewModel::onPlay, onShuffle = viewModel::onShuffle,
            onMove = viewModel::onMove, onRemove = viewModel::onRemove,
            onFindSongs = onFindSongs,
        ),
        onGone,
    )
}

@Composable
fun PlaylistContent(state: PlaylistPageState, mark: SongMark, actions: PlaylistActions, onGone: () -> Unit = {}) {
    val strings = LocalStrings.current
    when (state) {
        PlaylistPageState.Loading -> Unit
        PlaylistPageState.Gone -> LaunchedEffect(Unit) { onGone() }
        is PlaylistPageState.Showing -> {
            val list = rememberLazyListState()
            val drag = rememberListDrag(state.songs.tracks)
            val title = if (state.hasName) state.name else strings.untitledPlaylist
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Space.Gutter, vertical = Space.Small),
            ) {
                item(key = "header") { Header(title, state, strings, actions) }
                if (state.songs.isEmpty) {
                    item(key = "empty") {
                        Column(Modifier.padding(top = Space.Large), verticalArrangement = Arrangement.spacedBy(Space.Large)) {
                            Text(
                                strings.playlistEmptyHint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TonalButton(strings.findSongs, actions.onFindSongs, icon = R1Icons.Search)
                        }
                    }
                } else {
                    songItems(
                        strings, state.songs, mark, drag, list,
                        onPlay = actions.onPlay, onMove = actions.onMove, onRemove = actions.onRemove, onAdd = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, state: PlaylistPageState.Showing, strings: Strings, actions: PlaylistActions) {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        val problem = state.problem
        if (problem is Problem.Failed) ProblemBanner(strings.describe(problem.error))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.Large)) {
            Cover(state.songs.tracks.firstOrNull()?.thumbnailUrl.orEmpty(), 96.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Lines)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    strings.songCount(state.songs.size),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.playingHere) {
                    Text(strings.playingFromHere, style = MaterialTheme.typography.bodySmall, color = Tokens.Accent)
                }
            }
        }
        if (state.canPlay) {
            // Shuffle is the prominent one, the filled pill; Play all is the quieter second. Rename and delete live on the top bar.
            Row(horizontalArrangement = Arrangement.spacedBy(Space.Small), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(strings.shuffle, actions.onShuffle, icon = R1Icons.Shuffle)
                TonalButton(strings.playAll, { actions.onPlay(0) }, icon = R1Icons.Play)
            }
        }
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

private fun showing(
    songs: List<Track> = tracks,
    playingHere: Boolean = false,
    problem: Problem = Problem.None,
    name: String = "Nhạc xưa",
) = PlaylistPageState.Showing(name, name.isNotEmpty(), SongList.of(songs), playingHere, problem)

@Preview
@Composable
private fun PlaylistSongsPreview() = PreviewHost(EnglishStrings) { PlaylistContent(showing(), SongMark.None, PlaylistActions()) }

@Preview
@Composable
private fun PlaylistEmptyPreview() = PreviewHost(EnglishStrings) { PlaylistContent(showing(emptyList()), SongMark.None, PlaylistActions()) }

@Preview
@Composable
private fun PlaylistLoadingPreview() = PreviewHost(EnglishStrings) { PlaylistContent(PlaylistPageState.Loading, SongMark.None, PlaylistActions()) }

@Preview
@Composable
private fun PlaylistPlayingHerePreview() = PreviewHost(EnglishStrings) {
    PlaylistContent(showing(playingHere = true), SongMark.OnSpeaker("a"), PlaylistActions())
}

@Preview
@Composable
private fun PlaylistFailedPreview() = PreviewHost(EnglishStrings) {
    val error = SpeakerException.Unreachable(RuntimeException("x"))
    PlaylistContent(showing(problem = Problem.Failed(error)), SongMark.Failed("b", error), PlaylistActions())
}

@Preview
@Composable
private fun PlaylistVietnamesePreview() = PreviewHost(VietnameseStrings) {
    PlaylistContent(showing(playingHere = true, name = ""), SongMark.OnSpeaker("c"), PlaylistActions())
}
