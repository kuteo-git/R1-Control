package com.r1control.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.ListRef
import com.r1control.app.domain.model.NowPlaying
import com.r1control.app.domain.model.PlayRequest
import com.r1control.app.domain.model.Playlist
import com.r1control.app.domain.model.PlaylistId
import com.r1control.app.domain.model.RecentSearch
import com.r1control.app.domain.model.Shelf
import com.r1control.app.domain.model.SongList
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.model.formatProgress
import com.r1control.app.domain.model.keywordsIn
import com.r1control.app.domain.model.shelfOf
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.usecase.Created
import com.r1control.app.domain.usecase.FailedKeyword
import com.r1control.app.ui.Page
import com.r1control.app.ui.Problem
import com.r1control.app.ui.components.Cover
import com.r1control.app.ui.components.IconTap
import com.r1control.app.ui.components.LoadingBox
import com.r1control.app.ui.components.NameDialog
import com.r1control.app.ui.components.OutlinePill
import com.r1control.app.ui.components.ProblemBanner
import com.r1control.app.ui.components.R1Icon
import com.r1control.app.ui.components.R1TextField
import com.r1control.app.ui.components.SectionLabel
import com.r1control.app.ui.components.TextAction
import com.r1control.app.ui.components.rememberListDrag
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.i18n.dayOf
import com.r1control.app.ui.i18n.describe
import com.r1control.app.ui.i18n.formatLocalTime
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Radius
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens
import kotlin.time.Clock

class MusicActions(
    val onQuery: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onRecent: (RecentSearch) -> Unit = {},
    val onForgetRecent: (RecentSearch) -> Unit = {},
    val onPlay: (Int) -> Unit = {},
    val onMove: (Int, Int) -> Unit = { _, _ -> },
    val onRemove: (Track) -> Unit = {},
    /** One song, or every result. Opens the playlist sheet. */
    val onAdd: (List<Track>) -> Unit = {},
    val onPick: (PlaylistId) -> Unit = {},
    val onPickNew: (String) -> Unit = {},
    val onPickerDismiss: () -> Unit = {},
    /** The shelf's New playlist card, with the name asked for. */
    val onNewPlaylist: (String) -> Unit = {},
    val onOpen: (Page) -> Unit = {},
    val onClear: () -> Unit = {},
    val onClearQuery: () -> Unit = {},
    /** The field took the focus it was asked to take. */
    val onSearchFocused: () -> Unit = {},
)

@Composable
fun MusicScreen(viewModel: MusicViewModel, onOpen: (Page) -> Unit) {
    val state by viewModel.state.collectAsState()
    val query by viewModel.query.collectAsState()
    val mark by viewModel.mark.collectAsState()
    val focusSearch by viewModel.focusSearch.collectAsState()
    MusicContent(
        state = state,
        query = query,
        mark = mark,
        focusSearch = focusSearch,
        actions = MusicActions(
            onQuery = viewModel::onQuery,
            onSearch = viewModel::search,
            onRecent = viewModel::searchAgain,
            onForgetRecent = viewModel::forget,
            onPlay = viewModel::play,
            onMove = viewModel::onMove,
            onRemove = viewModel::onRemove,
            onAdd = viewModel::onAddToPlaylist,
            onPick = viewModel::onPick,
            onPickNew = viewModel::onPickNew,
            onPickerDismiss = viewModel::onPickerDismiss,
            // The new playlist opens at once, empty, with its hint on how to fill it.
            onNewPlaylist = { name ->
                when (val made = viewModel.onNewPlaylist(name)) {
                    is Created.Made -> onOpen(Page.PlaylistDetails(made.id))
                    Created.Refused -> Unit
                }
            },
            onOpen = onOpen,
            onClear = viewModel::clearResults,
            onClearQuery = viewModel::clearQuery,
            onSearchFocused = viewModel::onSearchFocused,
        ),
    )
}

@Composable
fun MusicContent(
    state: MusicState,
    query: String,
    mark: SongMark,
    /** The field takes focus, and the list goes to its top: asked for by an empty playlist's "Find songs". */
    focusSearch: Boolean,
    actions: MusicActions,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val canSearch = keywordsIn(query).isNotEmpty() && state.phase != MusicPhase.Searching
    val focus = LocalFocusManager.current
    // The results are what the person wants to see, so the keyboard goes with the tap.
    val search = { focus.clearFocus(); actions.onSearch() }
    val nowMillis = remember(state.recents) { Clock.System.now().toEpochMilliseconds() }
    val list = rememberLazyListState()
    val drag = rememberListDrag(state.results.tracks)
    var naming by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    LaunchedEffect(focusSearch) {
        if (!focusSearch) return@LaunchedEffect
        // The field is the list's first item, composed only while it is in view.
        list.scrollToItem(0)
        withFrameNanos {}
        field.requestFocus()
        actions.onSearchFocused()
    }
    LazyColumn(
        state = list,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.Gutter, vertical = Space.Small),
    ) {
        item(key = "query") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                R1TextField(
                    value = query,
                    onValueChange = actions.onQuery,
                    hint = strings.keywordHint,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    onDone = if (canSearch) search else null,
                    focusRequester = field,
                    trailing = {
                        if (query.isNotEmpty()) {
                            IconTap(
                                R1Icons.Close, strings.clearSearch,
                                { actions.onClearQuery(); field.requestFocus() },
                                tint = Tokens.OnGroundMuted, iconSize = 20.dp,
                            )
                        }
                    },
                )
                IconTap(
                    icon = R1Icons.Search,
                    description = strings.search,
                    onClick = search,
                    enabled = canSearch,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    disabledTint = Tokens.OnGroundMuted,
                    background = if (canSearch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    iconSize = 22.dp,
                    box = 52.dp,
                )
            }
        }
        when (val phase = state.phase) {
            MusicPhase.Idle -> {
                shelfItems(state.shelf, strings, actions.onOpen) { naming = true }
                idleItems(state.recents, nowMillis, strings, actions)
            }
            MusicPhase.Searching -> item(key = "searching") { LoadingBox(Modifier.padding(top = Space.XLarge)) }
            is MusicPhase.Done -> resultItems(phase, state, mark, drag, list, strings, actions)
        }
    }
    PlaylistPicker(state.picker, actions.onPick, actions.onPickNew, actions.onPickerDismiss)
    if (naming) {
        NameDialog(
            title = strings.newPlaylist, initial = "", hint = strings.playlistName,
            confirmText = strings.create, cancelText = strings.cancel,
            onConfirm = { naming = false; actions.onNewPlaylist(it) },
            onCancel = { naming = false },
        )
    }
}

private val CardSize = 132.dp

/**
 * The playlists above the recent searches. Its keys ("shelf*", "playlist:*")
 * never meet "query", "hint", "recents-title", a recent's key or a "song:*" key.
 * It always ends with a New playlist card, so a phone with none has one card
 * and a phone with some can start another without leaving the tab.
 */
private fun LazyListScope.shelfItems(shelf: ShelfState, strings: Strings, onOpen: (Page) -> Unit, onNew: () -> Unit) {
    when (shelf) {
        // A first frame without a shelf, then the shelf: a local read takes a millisecond, so no skeleton.
        ShelfState.Loading -> Unit
        ShelfState.Unreadable -> item(key = "shelf") {
            QuietLine(strings.playlistsUnreadable, Modifier.padding(top = Space.XLarge))
        }
        is ShelfState.Ready -> {
            item(key = "shelf-title") {
                Row(Modifier.fillMaxWidth().padding(top = Space.Small), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(strings.playlists, Modifier.weight(1f))
                    if (shelf.shelf.hasMore) TextAction(strings.more) { onOpen(Page.AllPlaylists) }
                }
            }
            item(key = "shelf") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.Medium)) {
                    items(shelf.shelf.cards, key = { "playlist:" + it.id.value }) { playlist ->
                        PlaylistCard(playlist, strings) { onOpen(Page.PlaylistDetails(playlist.id)) }
                    }
                    item(key = "shelf-new") { NewPlaylistCard(strings.newPlaylist, onNew) }
                }
            }
            if (shelf.keptAside) item(key = "shelf-aside") { QuietLine(strings.playlistsKeptAside, Modifier.padding(top = Space.Medium)) }
            if (shelf.saveFailed) item(key = "shelf-unsaved") { QuietLine(strings.playlistsNotSaved, Modifier.padding(top = Space.Medium)) }
        }
    }
}

@Composable
private fun QuietLine(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PlaylistCard(playlist: Playlist, strings: Strings, onClick: () -> Unit) {
    Column(
        Modifier
            .width(CardSize)
            .clip(RoundedCornerShape(Radius.Row))
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Space.Hair),
    ) {
        Cover(playlist.coverUrl, CardSize, radius = Radius.Row)
        Text(
            if (playlist.hasName) playlist.name else strings.untitledPlaylist,
            style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            strings.songCount(playlist.songs.size),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
        )
    }
}

@Composable
private fun NewPlaylistCard(text: String, onClick: () -> Unit) {
    Column(
        Modifier
            .width(CardSize)
            .clip(RoundedCornerShape(Radius.Row))
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Space.Hair),
    ) {
        Box(
            Modifier.size(CardSize).clip(RoundedCornerShape(Radius.Row)).background(Tokens.Card),
            contentAlignment = Alignment.Center,
        ) { R1Icon(R1Icons.Add, null, tint = Tokens.OnGround, size = 36.dp) }
        Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun LazyListScope.idleItems(
    recents: List<RecentSearch>,
    nowMillis: Long,
    strings: Strings,
    actions: MusicActions,
) {
    if (recents.isEmpty()) {
        item(key = "hint") {
            Text(
                strings.musicEmptyHint,
                modifier = Modifier.padding(top = Space.XLarge),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    item(key = "recents-title") { SectionLabel(strings.recentSearches, Modifier.padding(top = Space.XLarge)) }
    itemsIndexed(recents, key = { _, r -> r.atMillis.toString() + r.keywords.joinToString("|") }) { _, recent ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button) { actions.onRecent(recent) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            R1Icon(R1Icons.Search, null, tint = Tokens.OnGroundMuted, size = 22.dp)
            Column(Modifier.weight(1f).padding(horizontal = Space.Large), verticalArrangement = Arrangement.spacedBy(Space.Lines)) {
                Text(
                    recent.keywords.joinToString(", "),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (recent.atMillis > 0) {
                    Text(
                        strings.searchedAt(strings.dayOf(recent.atMillis, nowMillis), formatLocalTime(recent.atMillis)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconTap(R1Icons.Close, strings.removeRecent, { actions.onForgetRecent(recent) }, tint = Tokens.OnGroundMuted)
        }
    }
}

private fun LazyListScope.resultItems(
    phase: MusicPhase.Done,
    state: MusicState,
    mark: SongMark,
    drag: com.r1control.app.ui.components.ListDrag<Track>,
    list: androidx.compose.foundation.lazy.LazyListState,
    strings: Strings,
    actions: MusicActions,
) {
    val results = state.results
    if (phase.allFailed) {
        item(key = "all-failed") {
            ProblemBanner(
                strings.describe(phase.failed.first().error),
                Modifier.padding(top = Space.Large),
                strings.tryAgain,
                actions.onSearch,
            )
        }
        return
    }
    item(key = "results-title") {
        Column(Modifier.fillMaxWidth().padding(top = Space.XLarge, bottom = Space.Small), verticalArrangement = Arrangement.spacedBy(Space.Small)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    strings.searchResults,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextAction(strings.clearResults, actions.onClear)
            }
            if (!results.isEmpty) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                    OutlinePill(strings.playAll, { actions.onPlay(0) }, icon = R1Icons.Play)
                    if (state.canAdd) OutlinePill(strings.addAll, { actions.onAdd(results.tracks) }, icon = R1Icons.PlaylistAdd)
                }
            }
        }
    }
    if (state.shelf == ShelfState.Unreadable && !results.isEmpty) {
        item(key = "no-adding") { QuietLine(strings.playlistsLockedNoAdding, Modifier.padding(bottom = Space.Small)) }
    }
    if (phase.failed.isNotEmpty()) {
        item(key = "failed") {
            Row(Modifier.fillMaxWidth().padding(bottom = Space.Small), verticalAlignment = Alignment.Top) {
                R1Icon(R1Icons.Warning, null, tint = Tokens.Error, size = 16.dp, modifier = Modifier.padding(top = 2.dp))
                Text(
                    strings.searchFailedFor(phase.failed.map(FailedKeyword::keyword)),
                    modifier = Modifier.padding(start = Space.Small),
                    style = MaterialTheme.typography.bodySmall,
                    color = Tokens.Error,
                )
            }
        }
    }
    if (results.isEmpty) {
        item(key = "empty") {
            Text(
                strings.noResults,
                modifier = Modifier.padding(top = Space.Large),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    songItems(
        strings, results, mark, drag, list,
        onPlay = actions.onPlay, onMove = actions.onMove, onRemove = actions.onRemove,
        onAdd = if (state.canAdd) ({ actions.onAdd(listOf(it)) }) else null,
    )
}

/** The bar above the tab bar while music plays on the speaker; a tap opens the songs playing. */
@Composable
fun MiniPlayer(state: MiniState.Showing, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Tokens.Card)
            .clickable(role = Role.Button, onClickLabel = strings.openControl, onClick = onOpen)
            .padding(horizontal = Space.Medium, vertical = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(state.coverUrl, 48.dp, radius = Radius.Cover)
        Column(Modifier.weight(1f).padding(horizontal = Space.Medium), verticalArrangement = Arrangement.spacedBy(Space.Lines)) {
            Text(state.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val problem = state.problem
            val phase = state.phase
            val failed = problem is Problem.Failed
            val line = when {
                problem is Problem.Failed && problem.error is SpeakerException.Unreachable -> strings.speakerOffline
                problem is Problem.Failed -> strings.describe(problem.error)
                phase is NowPlaying.Phase.Downloading -> strings.downloading(phase.percent)
                else -> ""
            }
            val muted = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            if (line.isNotEmpty()) {
                Text(line, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                // The artist gives way to the clock, which never gets cut.
                Row {
                    if (state.artist.isNotEmpty()) {
                        Text(
                            state.artist,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodySmall,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(Space.Medium))
                    }
                    Text(
                        formatProgress(state.positionS, state.durationS),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        IconTap(
            icon = if (state.playing) R1Icons.Pause else R1Icons.Play,
            description = if (state.playing) strings.pause else strings.play,
            onClick = onToggle,
            enabled = state.canToggle,
            iconSize = 30.dp,
        )
        if (state.canNext) {
            IconTap(R1Icons.Next, strings.next, onNext, iconSize = 30.dp)
        }
    }
}

// ---- previews ----

private fun track(n: Int, title: String, artist: String) =
    Track("id$n", title, artist, "${3 + n % 4}:${10 + n}", "")

private val sample = listOf(
    track(1, "Như Một Người Rưng", "OSAD & Hà An Huy"),
    track(2, "Không Môn Đăng Hộ Đối (rumba version, a very long title that must be cut)", "Nguyễn Vĩ"),
    track(3, "Tôi Thua Số Đuôi", "Văn Hường"),
)

@Composable
private fun PreviewHost(strings: Strings, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStrings provides strings) {
        R1Theme { Box(Modifier.background(Tokens.Ground)) { content() } }
    }
}

private fun playlist(n: Int, name: String, songs: Int) =
    Playlist(PlaylistId("p$n"), name, SongList.of(List(songs) { track(it + n * 100, "Bài $it", "Ca sĩ") }), n.toLong())

private fun readyShelf(count: Int, keptAside: Boolean = false, saveFailed: Boolean = false) =
    ShelfState.Ready(shelfOf(List(count) { playlist(it, "Nhạc xưa ${it + 1}", 3 + it) }), saveFailed, keptAside)

private fun state(
    phase: MusicPhase,
    recents: List<RecentSearch> = emptyList(),
    results: SongList = SongList.Empty,
    shelf: ShelfState = readyShelf(0),
    picker: Picker = Picker.Closed,
    request: PlayRequest = PlayRequest.None,
) = MusicState(phase, results, recents, shelf, picker, request)

private fun done(failed: List<FailedKeyword> = emptyList(), allFailed: Boolean = false) =
    MusicPhase.Done(ListRef.Results(1), failed, allFailed)

private val recentsSample = listOf(RecentSearch(listOf("thanh ngân", "sương trắng"), 1_762_500_000_000), RecentSearch(listOf("bolero"), 0))

@Preview
@Composable
private fun MusicEmptyPreview() = PreviewHost(EnglishStrings) {
    MusicContent(state(MusicPhase.Idle), "", SongMark.None, focusSearch = false, MusicActions())
}

@Preview
@Composable
private fun MusicQueryWithClearPreview() = PreviewHost(EnglishStrings) {
    MusicContent(state(MusicPhase.Idle, recentsSample), "thanh ngân, bolero", SongMark.None, focusSearch = false, MusicActions())
}

@Preview
@Composable
private fun MusicLoadingShelfPreview() = PreviewHost(EnglishStrings) {
    MusicContent(state(MusicPhase.Idle, recentsSample, shelf = ShelfState.Loading), "", SongMark.None, focusSearch = false, MusicActions())
}

@Preview
@Composable
private fun MusicShelfPreview() = PreviewHost(EnglishStrings) {
    MusicContent(state(MusicPhase.Idle, recentsSample, shelf = readyShelf(2)), "bolero", SongMark.None, focusSearch = false, MusicActions())
}

@Preview
@Composable
private fun MusicShelfWithMorePreview() = PreviewHost(EnglishStrings) {
    MusicContent(state(MusicPhase.Idle, recentsSample, shelf = readyShelf(6, saveFailed = true)), "", SongMark.None, focusSearch = false, MusicActions())
}

@Preview
@Composable
private fun MusicShelfUnreadablePreview() = PreviewHost(EnglishStrings) {
    MusicContent(state(MusicPhase.Idle, recentsSample, shelf = ShelfState.Unreadable), "", SongMark.None, focusSearch = false, MusicActions())
}

@Preview
@Composable
private fun MusicShelfKeptAsidePreview() = PreviewHost(EnglishStrings) {
    MusicContent(state(MusicPhase.Idle, shelf = readyShelf(0, keptAside = true)), "", SongMark.None, focusSearch = false, MusicActions())
}

@Preview
@Composable
private fun MusicSearchingPreview() = PreviewHost(EnglishStrings) {
    MusicContent(state(MusicPhase.Searching), "thanh ngân", SongMark.None, focusSearch = false, MusicActions())
}

@Preview
@Composable
private fun MusicResultsPreview() = PreviewHost(EnglishStrings) {
    val failed = listOf(FailedKeyword("b", SpeakerException.Unreachable(RuntimeException("x"))))
    MusicContent(state(done(failed), results = SongList.of(sample)), "a, b", SongMark.OnSpeaker("id1"), focusSearch = false, MusicActions())
}

@Preview
@Composable
private fun MusicResultsLockedPreview() = PreviewHost(VietnameseStrings) {
    MusicContent(state(done(), results = SongList.of(sample), shelf = ShelfState.Unreadable), "a", SongMark.None, focusSearch = false, MusicActions())
}

@Preview
@Composable
private fun MusicResultsWithPickerPreview() = PreviewHost(EnglishStrings) {
    val picker = Picker.Open(
        listOf(sample[0]),
        listOf(PickerRow(PlaylistId("p1"), "Nhạc xưa", true, 3, hasAll = true), PickerRow(PlaylistId("p2"), "Buổi sáng", true, 12, hasAll = false)),
    )
    MusicContent(state(done(), results = SongList.of(sample), picker = picker), "a", SongMark.None, focusSearch = false, MusicActions())
}

@Preview
@Composable
private fun MusicNoResultsPreview() = PreviewHost(EnglishStrings) {
    MusicContent(state(done()), "zzz", SongMark.None, focusSearch = false, MusicActions())
}

@Preview
@Composable
private fun MusicFailedPreview() = PreviewHost(EnglishStrings) {
    val error = SpeakerException.Unreachable(RuntimeException("x"))
    MusicContent(state(done(listOf(FailedKeyword("a", error)), allFailed = true)), "a", SongMark.None, focusSearch = false, MusicActions())
}

@Preview
@Composable
private fun MusicVietnamesePreview() = PreviewHost(VietnameseStrings) {
    MusicContent(
        state(done(), results = SongList.of(sample), shelf = readyShelf(3)), "thanh ngân, bolero",
        SongMark.Failed("id2", SpeakerException.Refused("queue busy")), focusSearch = false, MusicActions(),
    )
}

@Preview
@Composable
private fun MusicShelfVietnamesePreview() = PreviewHost(VietnameseStrings) {
    MusicContent(state(MusicPhase.Idle, recentsSample, shelf = readyShelf(6)), "", SongMark.None, focusSearch = false, MusicActions())
}

private fun mini(phase: NowPlaying.Phase, problem: Problem = Problem.None, canNext: Boolean = true) =
    MiniState.Showing("Hoa Nở Không Màu", "Hoài Lâm", "", phase, problem, positionS = 83, durationS = 245, canNext = canNext)

@Preview
@Composable
private fun MiniPlayerPreview() = PreviewHost(VietnameseStrings) {
    MiniPlayer(mini(NowPlaying.Phase.Playing), {}, {}, {})
}

@Preview
@Composable
private fun MiniPlayerLastSongPreview() = PreviewHost(EnglishStrings) {
    MiniPlayer(mini(NowPlaying.Phase.Paused, canNext = false), {}, {}, {})
}

@Preview
@Composable
private fun MiniPlayerDownloadingPreview() = PreviewHost(VietnameseStrings) {
    MiniPlayer(mini(NowPlaying.Phase.Downloading(40)), {}, {}, {})
}

@Preview
@Composable
private fun MiniPlayerFailedPreview() = PreviewHost(EnglishStrings) {
    val problem = Problem.Failed(SpeakerException.Unreachable(RuntimeException("x")))
    MiniPlayer(mini(NowPlaying.Phase.Paused, problem), {}, {}, {})
}
