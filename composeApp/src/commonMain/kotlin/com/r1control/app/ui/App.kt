package com.r1control.app.ui

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import com.r1control.app.ui.camera.CameraFullscreen
import com.r1control.app.ui.chat.ChatScreen
import com.r1control.app.ui.components.Haptic
import com.r1control.app.ui.components.IconTap
import com.r1control.app.ui.components.LocalHaptics
import com.r1control.app.ui.components.closesKeyboardOnTap
import com.r1control.app.ui.components.R1Icon
import com.r1control.app.ui.components.ScreenTopBar
import com.r1control.app.ui.components.rememberPlatformHaptics
import com.r1control.app.ui.control.ControlScreen
import com.r1control.app.ui.ha.HaEditButton
import com.r1control.app.ui.ha.HaScreen
import com.r1control.app.ui.ha.HaSettingsScreen
import com.r1control.app.ui.ha.HaSettingsViewModel
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.domain.model.LanguageChoice
import com.r1control.app.domain.model.ha.HaConnectionState
import com.r1control.app.domain.model.ha.haBaseUrl
import com.r1control.app.domain.usecase.SpeakerView
import com.r1control.app.ui.ha.HaState
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.settings.CommonSettings
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
    Ha(R1Icons.Home), Control(R1Icons.Speaker), Settings(R1Icons.Gear)
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
    CompositionLocalProvider(LocalStrings provides strings, LocalHaptics provides rememberPlatformHaptics()) {
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
    var tabIndex by rememberSaveable { mutableStateOf(Tab.Ha.ordinal) }
    var stack by rememberSaveable(stateSaver = PageStackSaver) { mutableStateOf(PageStack.Tabs) }
    val tab = Tab.entries[tabIndex]
    // Each tab keeps its own scroll: a tab left for another one holds its position and has it back on return.
    val tabState = rememberSaveableStateHolder()
    val page = stack.top
    val lists by models.store.state.collectAsState()
    // The keyboard covers the tab bar rather than hiding it (a hidden bar blinks back after the
    // keyboard closes), so the page is padded only by the part of the keyboard above the bars.
    var barsHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val mini by models.mini.state.collectAsState()
    val shownMini = mini as? MiniState.Showing
    val haptics = LocalHaptics.current
    val editButton by models.ha.editButton.collectAsState()
    val haState by models.ha.state.collectAsState()
    val haAddress by models.ha.address.collectAsState()
    val speaker by models.feed.view.collectAsState()
    val languageChoice by models.app.language.collectAsState()
    // A camera filling the screen is drawn over the whole shell, the tab bar and top bar included.
    val expandedCamera by models.ha.expandedCamera.collectAsState()

    // The one back handler: Music pages use it too, and it returns to the page somebody was on.
    val back = { stack = stack.back() }
    BackHandler(page != null) { back() }
    val open = { next: Page -> stack = stack.open(next) }
    val goneFrom = { gone: Page -> stack = stack.without(gone) }
    // Music is a page now, opened from the top bar: a search opens it over whatever is up.
    val findSongs = { stack = stack.open(Page.Music); models.music.askForSearch() }
    // A mini player over a page, or over the Music and Chat tabs; the tab bar only ever sits under the tabs.
    val miniUnderPage = page != null && page.keepsMiniPlayer && shownMini != null

    Box(Modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = page?.title(strings, lists.playlists) ?: when (tab) {
                Tab.Ha -> strings.haTitle
                Tab.Control -> strings.speakerName
                Tab.Settings -> strings.settings
            },
            subtitle = when {
                page != null -> ""
                tab == Tab.Ha -> haAddress
                tab == Tab.Control -> address.toString()
                else -> ""
            },
            modifier = Modifier.statusBarsPadding(),
            backDescription = if (page != null) strings.back else null,
            onBack = back,
            titleContent = when {
                page != null -> null
                tab == Tab.Ha -> ({ HaTitle(haState, strings) })
                tab == Tab.Control -> ({ TitleWithDot(strings.speakerName, speakerDot(speaker)) })
                else -> null
            },
            actions = {
                // The right of the bar is the tab's own: Home Assistant its edit button and gear, R1 its
                // Music, Chat and gear. The common Settings tab is itself settings, so it holds nothing.
                if (page == null) when (tab) {
                    Tab.Ha -> {
                        when (editButton) {
                            HaEditButton.Hidden -> Unit
                            HaEditButton.Edit -> IconTap(R1Icons.Edit, strings.haEdit, models.ha::toggleEditing)
                            HaEditButton.Done -> IconTap(R1Icons.Check, strings.haDoneEditing, models.ha::toggleEditing, tint = Tokens.Accent)
                        }
                        IconTap(R1Icons.Gear, strings.settings, { open(Page.HaSettings) }, iconSize = 26.dp)
                    }
                    Tab.Control -> {
                        IconTap(R1Icons.MusicNote, strings.tabMusic, { open(Page.Music) })
                        IconTap(R1Icons.Chat, strings.tabChat, { open(Page.Chat) })
                        IconTap(R1Icons.Gear, strings.settings, { open(Page.Settings(SettingsPage.Home)) }, iconSize = 26.dp)
                    }
                    Tab.Settings -> Unit
                }
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
                    tabState.SaveableStateProvider(tab.name) {
                        when (tab) {
                            Tab.Ha -> HaScreen(models.ha, models.haSetup)
                            Tab.Control -> ControlScreen(
                                models.control,
                                onChangeAddress = { open(Page.Settings(SettingsPage.Advanced)) },
                                onOpenSongs = { open(Page.NowPlaying) },
                            )
                            Tab.Settings -> CommonSettings(languageChoice) { open(Page.Settings(it)) }
                        }
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
                            onClick = {
                                if (tab != entry) haptics.play(Haptic.Tap)
                                tabIndex = entry.ordinal
                            },
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
      // Over the whole shell, the bars included, so a camera fills the screen the way the watch layer does.
      expandedCamera?.let {
          CameraFullscreen(it, haBaseUrl(haAddress), models.ha::cameraStreamPath, models.ha::closeCamera)
      }
    }
}

private fun LocalContentColorOf(selected: Boolean) = if (selected) Tokens.OnGround else Tokens.OnGroundMuted

/** A tab title with a live status dot: green when the thing it names is reachable, red when it is not. */
@Composable
private fun TitleWithDot(title: String, dot: Color, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        trailing?.invoke()
    }
}

/** The Home Assistant tab's title: "HA", the connection dot, then the live entity count once connected. */
@Composable
private fun HaTitle(state: HaState, strings: Strings) {
    val live = state as? HaState.Live
    val connection = live?.connection
    val dot = when (connection) {
        HaConnectionState.Connected -> Tokens.Online
        HaConnectionState.Connecting -> Tokens.OnGroundMuted
        else -> Tokens.Error
    }
    TitleWithDot(strings.tabHa, dot) {
        if (connection == HaConnectionState.Connected && live != null) {
            Text(
                "(" + strings.haEntityCount(live.entities.size) + ")",
                style = MaterialTheme.typography.titleSmall,
                color = Tokens.OnGroundMuted,
                maxLines = 1,
            )
        }
    }
}

/** The R1 tab's dot: green while the speaker answers, grey while it is being read, red when it is offline. */
private fun speakerDot(view: SpeakerView): Color = when (view) {
    is SpeakerView.Online, is SpeakerView.Stale -> Tokens.Online
    SpeakerView.Connecting -> Tokens.OnGroundMuted
    is SpeakerView.Offline -> Tokens.Error
}

private fun labelOf(tab: Tab, strings: Strings): String = when (tab) {
    Tab.Ha -> strings.tabHa
    Tab.Control -> strings.tabControl
    Tab.Settings -> strings.settings
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
                    address.toString(),
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
            Page.HaSettings -> HaSettingsScreen(
                remember {
                    HaSettingsViewModel(container.haSettings, container.connectHa, scope) { signedIn ->
                        models.ha.signedIn(signedIn)
                        goneFrom(page)
                    }
                },
                resyncState = models.ha.resync,
                onResync = models.ha::resyncFromHa,
            )
            Page.Music -> MusicScreen(models.music, onOpen = open)
            Page.Chat -> ChatScreen(models.chat)
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
