package com.r1control.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.Playlist
import com.r1control.app.domain.model.PlaylistId
import com.r1control.app.domain.model.Playlists
import com.r1control.app.domain.model.SongList
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.usecase.Created
import com.r1control.app.domain.usecase.MusicStore
import com.r1control.app.ui.Page
import com.r1control.app.ui.components.Cover
import com.r1control.app.ui.components.NameDialog
import com.r1control.app.ui.components.R1Icon
import com.r1control.app.ui.components.tappable
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

/**
 * Every playlist, newest first, as rows (cover, name, song count) under a "New
 * playlist" row. No page view model: a page that only lists and creates has no
 * state of its own, so it reads the store's and calls `create`.
 */
@Composable
fun PlaylistsScreen(store: MusicStore, onOpen: (Page) -> Unit) {
    val lists by store.state.collectAsState()
    PlaylistsContent(
        playlists = lists.playlists,
        onOpen = { onOpen(Page.PlaylistDetails(it)) },
        onCreate = { name ->
            when (val made = store.create(name, emptyList())) {
                is Created.Made -> onOpen(Page.PlaylistDetails(made.id))
                Created.Refused -> Unit
            }
        },
    )
}

@Composable
fun PlaylistsContent(playlists: Playlists, onOpen: (PlaylistId) -> Unit, onCreate: (String) -> Unit) {
    val strings = LocalStrings.current
    var naming by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.Gutter, vertical = Space.Small)) {
        when (playlists) {
            Playlists.Loading -> Unit
            Playlists.Unreadable -> item(key = "unreadable") {
                Text(
                    strings.playlistsUnreadable,
                    modifier = Modifier.padding(top = Space.Large),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is Playlists.Readable -> {
                item(key = "new") { Line(null, R1Icons.Add, strings.newPlaylist, "") { naming = true } }
                items(playlists.all, key = { "playlist:" + it.id.value }) { playlist ->
                    Line(
                        playlist.coverUrl, R1Icons.MusicNote,
                        if (playlist.hasName) playlist.name else strings.untitledPlaylist,
                        strings.songCount(playlist.songs.size),
                    ) { onOpen(playlist.id) }
                }
            }
        }
    }
    if (naming) {
        NameDialog(
            title = strings.newPlaylist, initial = "", hint = strings.playlistName,
            confirmText = strings.create, cancelText = strings.cancel,
            onConfirm = { naming = false; onCreate(it) },
            onCancel = { naming = false },
        )
    }
}

/** A row: a 56 dp picture (the first song's cover, or a glyph), a name and a count. [cover] null is the New playlist row. */
@Composable
private fun Line(cover: String?, glyph: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(Radius.Row))
            .tappable(role = Role.Button, onClick = onClick)
            .padding(vertical = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cover == null) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(Radius.Cover)).background(Tokens.Card), contentAlignment = Alignment.Center) {
                R1Icon(glyph, null, tint = Tokens.OnGround, size = 28.dp)
            }
        } else {
            Cover(cover, 56.dp)
        }
        Column(Modifier.weight(1f).padding(horizontal = Space.Large), verticalArrangement = Arrangement.spacedBy(Space.Lines)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        R1Icon(R1Icons.ChevronRight, null, tint = Tokens.OnGroundMuted)
    }
}

// ---- previews ----

@Composable
private fun PreviewHost(strings: Strings, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStrings provides strings) {
        R1Theme { Box(Modifier.background(Tokens.Ground)) { content() } }
    }
}

private fun readable(count: Int) = Playlists.Readable(
    List(count) { n ->
        Playlist(PlaylistId("p$n"), if (n == 2) "" else "Nhạc xưa ${n + 1}", SongList.of(List(n + 1) { Track("t$n-$it", "Bài $it", "", "", "") }), n.toLong())
    },
    saveFailed = false, keptAside = false,
)

@Preview
@Composable
private fun PlaylistsPreview() = PreviewHost(EnglishStrings) { PlaylistsContent(readable(7), {}, {}) }

@Preview
@Composable
private fun PlaylistsNonePreview() = PreviewHost(EnglishStrings) { PlaylistsContent(readable(0), {}, {}) }

@Preview
@Composable
private fun PlaylistsLoadingPreview() = PreviewHost(EnglishStrings) { PlaylistsContent(Playlists.Loading, {}, {}) }

@Preview
@Composable
private fun PlaylistsUnreadablePreview() = PreviewHost(EnglishStrings) { PlaylistsContent(Playlists.Unreadable, {}, {}) }

@Preview
@Composable
private fun PlaylistsVietnamesePreview() = PreviewHost(VietnameseStrings) { PlaylistsContent(readable(4), {}, {}) }
