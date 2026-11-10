package com.r1control.app.ui

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.r1control.app.AppContainer
import com.r1control.app.domain.model.R1Address
import com.r1control.app.ui.address.AddressViewModel
import com.r1control.app.ui.address.FirstRunScreen
import com.r1control.app.ui.chat.ChatScreen
import com.r1control.app.ui.components.IconTap
import com.r1control.app.ui.components.closesKeyboardOnTap
import com.r1control.app.ui.components.R1Icon
import com.r1control.app.ui.components.ScreenTopBar
import com.r1control.app.ui.control.ControlScreen
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.deviceLanguage
import com.r1control.app.ui.i18n.resolve
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.music.MiniPlayer
import com.r1control.app.ui.music.MiniState
import com.r1control.app.ui.music.MusicScreen
import com.r1control.app.ui.music.NowPlayingScreen
import com.r1control.app.ui.music.PlaylistScreen
import com.r1control.app.ui.music.PlaylistViewModel
import com.r1control.app.ui.music.PlaylistsScreen
import com.r1control.app.ui.settings.AdvancedScreen
import com.r1control.app.ui.settings.AdvancedViewModel
import com.r1control.app.ui.settings.AudioScreen
import com.r1control.app.ui.settings.AudioViewModel
import com.r1control.app.ui.settings.LanguageScreen
import com.r1control.app.ui.settings.BluetoothInputScreen
import com.r1control.app.ui.settings.BluetoothMenu
import com.r1control.app.ui.settings.BluetoothOutputScreen
import com.r1control.app.ui.settings.BluetoothViewModel
import com.r1control.app.ui.settings.NewsScreen
import com.r1control.app.ui.settings.NewsViewModel
import com.r1control.app.ui.settings.PageSlide
import com.r1control.app.ui.settings.SettingsHome
import com.r1control.app.ui.settings.SettingsHomeViewModel
import com.r1control.app.ui.settings.SettingsPage
import com.r1control.app.ui.settings.VoiceScreen
import com.r1control.app.ui.settings.VoiceViewModel
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Tokens

private enum class Tab(val icon: ImageVector) {
    Control(R1Icons.Speaker), Music(R1Icons.MusicNote), Chat(R1Icons.Chat)
}

/** Holds [AppModels] across rotations and recreation, with the activity's view model store. */
private class RootHolder(container: AppContainer) : ViewModel() {
    val models = AppModels(container, viewModelScope)
}

@Composable
fun App(container: AppContainer) {
    val holder = viewModel { RootHolder(container) }
    // The language is state, not a value read once: choosing one in Settings redraws the whole app in it.
    // Android recreates the activity on a locale change and iOS restarts the app, so the phone's is read once.
    val choice by holder.models.app.language.collectAsState()
    val device = remember { deviceLanguage() }
    val strings = choice.resolve(device).strings
    CompositionLocalProvider(LocalStrings provides strings) {
        R1Theme {
            // Surface, not a bare Box: it is what gives Text its content colour. Without it text is black on black.
            Surface(Modifier.fillMaxSize().closesKeyboardOnTap(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                FollowForeground(container)
                Root(container, holder.models)
            }
        }
    }
}

/**
 * Tells the container whether the app is on screen. A stopped activity, or an
 * iOS app in the background, keeps its composition and every collector in it,
 * so the polls are held at the speaker's door instead.
 */
@Composable
private fun FollowForeground(container: AppContainer) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.currentStateFlow.collect { container.foreground.set(it.isAtLeast(Lifecycle.State.STARTED)) }
    }
}

@Composable
private fun Root(container: AppContainer, models: AppModels) {
    val gate by models.app.gate.collectAsState()
    when (val now = gate) {
        Gate.Loading -> Unit
        Gate.NeedsAddress -> {
            val scope = rememberCoroutineScope()
            val vm = remember { AddressViewModel("", container.saveAddress, scope, models::addressSaved) }
            FirstRunScreen(vm)
        }
        is Gate.Ready -> Shell(container, models, now.address)
    }
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun Shell(container: AppContainer, models: AppModels, address: R1Address) {
    val strings = LocalStrings.current
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    var stack by rememberSaveable(stateSaver = PageStackSaver) { mutableStateOf(PageStack.Tabs) }
    val tab = Tab.entries[tabIndex]
    val page = stack.top
    val lists by models.store.state.collectAsState()
    // The keyboard covers the tab bar rather than hiding it (a hidden bar blinks back after the
    // keyboard closes), so the page is padded only by the part of the keyboard above the bars.
    var barsHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val mini by models.mini.state.collectAsState()
    val shownMini = mini as? MiniState.Showing

    // The one back handler: Music pages use it too, and it returns to the page somebody was on.
    val back = { stack = stack.back() }
    BackHandler(page != null) { back() }
    val open = { next: Page -> stack = stack.open(next) }
    val goneFrom = { gone: Page -> stack = stack.without(gone) }
    // The search is a tab, not a page, so going there closes the pages over the tabs.
    val findSongs = { stack = PageStack.Tabs; tabIndex = Tab.Music.ordinal; models.music.askForSearch() }
    // A mini player over a page, or over the Music and Chat tabs; the tab bar only ever sits under the tabs.
    val miniUnderPage = page != null && page.keepsMiniPlayer && shownMini != null

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = page?.title(strings, lists.playlists) ?: strings.speakerName,
            subtitle = if (page == null) address.toString() else "",
            modifier = Modifier.statusBarsPadding(),
            backDescription = if (page != null) strings.back else null,
            onBack = back,
            actions = {
                if (page == null) IconTap(R1Icons.Gear, strings.settings, { open(Page.Settings(SettingsPage.Home)) }, iconSize = 26.dp)
            },
        )
        Box(
            Modifier.weight(1f).fillMaxSize()
                .let { if (page == null) it.consumeWindowInsets(PaddingValues(bottom = barsHeight)) else if (miniUnderPage) it else it.navigationBarsPadding() }
                .imePadding(),
        ) {
            PageSlide(page to stack.depth, depth = { it.second }) { (shown, _) ->
                if (shown != null) {
                    PageHost(shown, container, models, address, open, goneFrom, findSongs)
                } else {
                    when (tab) {
                        Tab.Control -> ControlScreen(
                            models.control,
                            onChangeAddress = { open(Page.Settings(SettingsPage.Advanced)) },
                            onOpenSongs = { open(Page.NowPlaying) },
                        )
                        Tab.Music -> MusicScreen(models.music, onOpen = open)
                        Tab.Chat -> ChatScreen(models.chat)
                    }
                }
            }
        }
        if (page == null) {
            Column(Modifier.onSizeChanged { barsHeight = with(density) { it.height.toDp() } }) {
                if (shownMini != null && tab != Tab.Control) {
                    MiniPlayer(shownMini, onOpen = { open(Page.NowPlaying) }, onToggle = models.mini::onPlayPause, onNext = models.mini::onNext)
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    Tab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tabIndex = entry.ordinal },
                            icon = { R1Icon(entry.icon, null, tint = LocalContentColorOf(tab == entry)) },
                            label = { Text(labelOf(entry, strings)) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Tokens.VolumeFill,
                                selectedIconColor = Tokens.OnGround,
                                selectedTextColor = Tokens.OnGround,
                                unselectedIconColor = Tokens.OnGroundMuted,
                                unselectedTextColor = Tokens.OnGroundMuted,
                            ),
                        )
                    }
                }
            }
        } else if (shownMini != null && miniUnderPage) {
            // The bar's own colour runs under the system bar, so no gap of ground shows below it.
            Box(Modifier.background(Tokens.Card).navigationBarsPadding()) {
                MiniPlayer(
                    shownMini,
                    onOpen = { if (page != Page.NowPlaying) open(Page.NowPlaying) },
                    onToggle = models.mini::onPlayPause,
                    onNext = models.mini::onNext,
                )
            }
        }
    }
}

private fun LocalContentColorOf(selected: Boolean) = if (selected) Tokens.OnGround else Tokens.OnGroundMuted

private fun labelOf(tab: Tab, strings: Strings): String = when (tab) {
    Tab.Control -> strings.tabControl
    Tab.Music -> strings.tabMusic
    Tab.Chat -> strings.tabChat
}

@Composable
private fun PageHost(
    page: Page,
    container: AppContainer,
    models: AppModels,
    address: R1Address,
    open: (Page) -> Unit,
    goneFrom: (Page) -> Unit,
    findSongs: () -> Unit,
) {
    // One scope per page: leaving it cancels its polling and any write still waiting.
    key(page) {
        val scope = rememberCoroutineScope()
        when (page) {
            is Page.Settings -> when (page.page) {
                SettingsPage.Home -> SettingsHome(
                    remember { SettingsHomeViewModel(models.feed, container.voices, scope) },
                    address.toString(), models.app.language,
                ) { open(Page.Settings(it)) }
                SettingsPage.Audio -> AudioScreen(
                    remember { AudioViewModel(container.speaker, scope, start = models.feed.view.value, onWrote = models.feed::refresh) },
                )
                SettingsPage.Voice -> VoiceScreen(
                    remember { VoiceViewModel(container.speaker, container.voices, scope, start = models.feed.view.value) },
                )
                SettingsPage.News -> NewsScreen(
                    remember { NewsViewModel(container.speaker, container.voices, scope, start = models.feed.view.value, onWrote = models.feed::refresh) },
                )
                SettingsPage.Bluetooth -> BluetoothMenu(
                    remember { BluetoothViewModel(container.speaker, scope, start = models.feed.view.value, onChanged = models.feed::refresh) },
                ) { open(Page.Settings(it)) }
                SettingsPage.BluetoothOutput -> BluetoothOutputScreen(
                    remember { BluetoothViewModel(container.speaker, scope, start = models.feed.view.value, onChanged = models.feed::refresh) },
                )
                SettingsPage.BluetoothInput -> BluetoothInputScreen(
                    remember { BluetoothViewModel(container.speaker, scope, start = models.feed.view.value, onChanged = models.feed::refresh) },
                )
                SettingsPage.Language -> LanguageScreen(models.app)
                SettingsPage.Advanced -> AdvancedScreen(
                    address = remember { AddressViewModel(address.toString(), container.saveAddress, scope, models::addressSaved) },
                    viewModel = remember { AdvancedViewModel(container.speaker, scope) },
                )
            }
            Page.AllPlaylists -> PlaylistsScreen(models.store, onOpen = open)
            Page.NowPlaying -> NowPlayingScreen(
                models.nowPlaying,
                onOpenPlaylist = { open(Page.PlaylistDetails(it)) },
                onFindSongs = findSongs,
            )
            is Page.PlaylistDetails -> PlaylistScreen(
                remember { PlaylistViewModel(page.id, models.store, models.feed, scope) },
                onGone = { goneFrom(page) },
                onFindSongs = findSongs,
            )
        }
    }
}
