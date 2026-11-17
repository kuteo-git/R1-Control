package com.r1control.app.ui.i18n

import androidx.compose.runtime.compositionLocalOf
import com.r1control.app.domain.model.AddressInput
import com.r1control.app.domain.model.LanguageChoice
import com.r1control.app.domain.model.ha.HaConnectionState
import com.r1control.app.domain.model.ha.HaInput
import com.r1control.app.domain.repository.SpeakerException

/**
 * Every word the app shows, as a property. Adding one here stops every
 * language from compiling until it is supplied, which is the point: a missing
 * translation cannot exist. Formatters that need a language take a [Strings].
 */
interface Strings {
    val appName: String
    /** The speaker's name under the app bar; a fixed word until the R1 reports a better one. */
    val speakerName: String
    val back: String
    val tryAgain: String
    val cancel: String

    // tabs
    val tabHa: String
    val tabControl: String
    val tabMusic: String
    val tabChat: String
    val settings: String

    // control
    val nothingPlaying: String
    val nothingPlayingHint: String
    val assistantListening: String
    val assistantSpeaking: String
    val speakerOffline: String
    /** [onIphone]: a refused Local Network permission looks exactly like being off the wifi, so the iPhone's words name it. */
    fun speakerOfflineHint(onIphone: Boolean): String
    val changeAddress: String
    val play: String
    val pause: String
    val next: String
    val stop: String
    fun streamingFrom(deviceName: String): String
    fun downloading(percent: Int): String
    fun onSpeaker(name: String): String
    fun volumePercent(pct: Int): String
    fun percent(value: Int): String
    /** What a screen reader says for the seek bar. */
    val seekPosition: String
    fun playbackPosition(position: String, duration: String): String

    // music
    val keywordHint: String
    val search: String
    val playAll: String
    val recentSearches: String
    val noResults: String
    fun searchFailedFor(keywords: List<String>): String
    val clearResults: String
    val playOnSpeaker: String
    val playingOnSpeaker: String
    val startingOnSpeaker: String
    val removeRecent: String
    fun searchedAt(day: String, time: String): String

    // chat
    val chatEmpty: String
    val chatHint: String
    val send: String
    val hideKeyboard: String
    /** A send that failed, said beside the field the text came back to; [reason] is already in words. */
    fun chatNotSent(reason: String): String
    val today: String
    val yesterday: String

    // settings
    val settingsAudio: String
    val settingsVoice: String
    val settingsNews: String
    val settingsBluetooth: String
    val settingsAdvanced: String
    val loudness: String
    val equalizer: String
    val newsEnabled: String
    val newsTime: String
    val newsVoice: String
    val newsTest: String
    val bluetoothEnabled: String
    val bluetoothScan: String
    val bluetoothAutoReconnect: String
    val addressLabel: String
    val addressHint: String
    val restartSpeaker: String
    val restartConfirm: String
    fun newsCategory(key: String): String

    // shared
    val close: String
    val live: String
    val clear: String
    val save: String

    // first run and address
    val welcomeTitle: String
    val welcomeBody: String
    val connect: String
    val addressChecking: String
    val addressSaved: String

    // control extras
    val volume: String

    // music extras
    val searchResults: String
    val musicEmptyHint: String
    val searching: String
    val openControl: String

    // settings extras
    val loudnessHint: String
    val equalizerBands: String
    val voiceNoServer: String
    val voiceAll: String
    val btOutputTitle: String
    val btInputTitle: String
    /** The top bar's shorter names for the two pages the menu's rows lead to. */
    val btOutputPage: String
    val btInputPage: String
    val btPhones: String
    val btTurnOn: String
    val btAllowPhone: String
    val btInputHint: String
    val newsCategories: String
    val newsTestHint: String
    val newsTestStarted: String
    val hours: String
    val minutes: String
    val increase: String
    val decrease: String
    val btOff: String
    val btUnsupported: String
    val btNone: String
    val btPairing: String
    val btConnecting: String
    val btNoDevices: String
    val btScanning: String
    val btPair: String
    val btConnect: String
    val btDisconnect: String
    val btForget: String
    val btPaired: String
    val btStreamingIn: String
    val btPlayingThrough: String
    fun btError(reason: String): String
    val btSummaryOff: String
    val btSummaryNone: String
    fun btSummaryOutput(name: String): String
    fun audioSummary(boost: String, eqOn: Boolean): String
    fun newsSummary(on: Boolean, time: String): String
    fun btReceiving(adapterName: String, seconds: Int): String
    fun btWaitingForPhone(seconds: Int): String
    fun btIncoming(name: String): String
    fun btOutput(name: String): String
    fun btForgetConfirm(name: String): String
    val restartButton: String
    val restarting: String
    val restartDone: String
    fun appVersion(version: String): String

    // playlists, list editing
    val playlists: String
    /** The page's title while its playlist is still being read. */
    val playlist: String
    val more: String
    val newPlaylist: String
    val untitledPlaylist: String
    fun songCount(n: Int): String
    val playlistName: String
    val create: String
    val renamePlaylist: String
    val deletePlaylist: String
    fun deletePlaylistQuestion(name: String): String
    val delete: String
    val shuffle: String
    val addToPlaylist: String
    val addAll: String
    val alreadyInPlaylist: String
    val playingFromHere: String
    /** The Now playing page: its title, the Control card's button to it, and where the songs come from. */
    val nowPlaying: String
    val clearSearch: String
    val songsPlaying: String
    fun playingFromPlaylist(name: String): String
    val playingFromResults: String
    val playingFromSpeaker: String
    val nowPlayingNothing: String
    val playlistEmptyHint: String
    val playlistsUnreadable: String
    val playlistsKeptAside: String
    val playlistsNotSaved: String
    /** Under the results while the playlists are locked: why no song has an add button. */
    val playlistsLockedNoAdding: String
    /** An empty playlist's button to the Music tab's search. */
    val findSongs: String
    /** What a screen reader says for a row's drag handle and its X. */
    fun reorderSong(title: String): String
    fun removeSong(title: String): String
    fun reorderCategory(name: String): String
    /** A draggable row's screen-reader actions, the drag's path without a finger. */
    val moveUp: String
    val moveDown: String

    // language
    val settingsLanguage: String
    val languageFollowPhone: String
    /** The line under "follow the phone": what the phone is set to, said in that language's own name. */
    fun languageFollowPhoneNow(deviceName: String): String

    // listening controls
    val wakeWord: String
    val wakeWordHint: String
    val endConversation: String
    /** "End the conversation" pressed, while the R1 closes the session. */
    val endingConversation: String

    // failures
    val errorNotConfigured: String
    fun errorUnreachable(onIphone: Boolean): String
    val errorBadReply: String
    fun errorRefused(reason: String): String
    fun addressInvalid(reason: AddressInput.Reason): String
    /** Something answered at [address], but not as an R1 does. */
    fun addressNotR1(address: String): String
    fun addressUnreachable(address: String, onIphone: Boolean): String

    // Home Assistant
    val haTitle: String
    val haSettingsTitle: String
    val haWelcomeTitle: String
    val haWelcomeBody: String
    val haAddressHint: String
    val haTokenHint: String
    val haChecking: String
    val haConnected: String
    val haTokenRefused: String
    fun haInvalid(reason: HaInput.Reason): String
    fun haUnreachable(address: String, onIphone: Boolean): String
    fun haConnection(state: HaConnectionState): String
    fun haEntityCount(count: Int): String
    val haNoEntities: String
    val haDashboardLoading: String
    val haDashboardFailed: String
    val haDashboardEmpty: String
    val haCoverOpen: String
    val haCoverClose: String
    val haCoverStop: String
    /** Re-importing the dashboard from Home Assistant, in Home Assistant's own settings. */
    val haSyncTitle: String
    val haSyncBody: String
    val haSyncButton: String
    val haSyncWarning: String
    val haSyncConfirm: String
    val haSyncing: String
    val haSynced: String
    val haSyncFailed: String
    /** The edit mode of the dashboard: the top bar's button, the rows, the dialogs and the entity picker. */
    val haEdit: String
    val haDoneEditing: String
    val haEmptyEdit: String
    val haStartEmpty: String
    val haTabUntitled: String
    val haTabAdd: String
    val haTabRename: String
    val haTabChooseIcon: String
    val haTabDeleteTitle: String
    val haTabDeleteBody: String
    val haAddSection: String
    val haNewSection: String
    val haSectionName: String
    val haRenameSection: String
    val haUntitledSection: String
    fun haDeleteSectionQuestion(title: String, tiles: Int): String
    val haAddTile: String
    val haRenameTile: String
    val haTileName: String
    val haPickEntity: String
    val haSearchEntities: String
    val haNothingToPick: String
    val haNoEntityMatches: String
    fun haReorderSection(title: String): String
    fun haReorderTile(title: String): String
    fun haRemoveSection(title: String): String
    fun haRemoveTile(title: String): String

    /** Marks a tile in the edit mode that is drawn only while a condition holds. */
    val haConditionalTile: String

    /** Marks a tile in the edit mode whose text is a template Home Assistant renders. */
    val haTemplateTile: String

    /** The controls sheet a light or a fan tile opens. */
    val haBrightness: String
    val haFanSpeed: String
    val haOscillation: String
    val haFanMode: String
    val haPower: String
    val haColor: String
    val haWhite: String
}

/** What went wrong, in the language of the screen. */
fun Strings.describe(error: SpeakerException): String = when (error) {
    is SpeakerException.NotConfigured -> errorNotConfigured
    is SpeakerException.Unreachable -> errorUnreachable(phoneAsksForLocalNetwork)
    is SpeakerException.Refused -> errorRefused(error.reason)
    is SpeakerException.BadReply -> errorBadReply
}

object EnglishStrings : Strings {
    override val appName = "Control"
    override val speakerName = "R1"
    override val back = "Back"
    override val tryAgain = "Try again"
    override val cancel = "Cancel"

    override val tabHa = "HA"
    override val tabControl = "R1"
    override val tabMusic = "Music"
    override val tabChat = "Chat"
    override val settings = "Settings"

    override val nothingPlaying = "Nothing playing"
    override val nothingPlayingHint = "Play something from the Music tab, or ask the speaker."
    override val assistantListening = "Listening…"
    override val assistantSpeaking = "Speaking"
    override val speakerOffline = "The speaker is not answering"
    override fun speakerOfflineHint(onIphone: Boolean) =
        "Check that the speaker is on, that this phone is on the house wifi, and that the address is right." +
            if (onIphone) " Also check Settings › Privacy & Security › Local Network: Control must be on." else ""
    override val changeAddress = "Change address"
    override val play = "Play"
    override val pause = "Pause"
    override val next = "Next"
    override val stop = "Stop"
    override fun streamingFrom(deviceName: String) = "Playing from $deviceName"
    override fun downloading(percent: Int) = "Getting ready… $percent%"
    override fun onSpeaker(name: String) = "on $name"
    override fun volumePercent(pct: Int) = "$pct%"
    override fun percent(value: Int) = "$value%"
    override val seekPosition = "Position"
    override fun playbackPosition(position: String, duration: String) = "$position of $duration"

    override val keywordHint = "Songs or artists (a, b, c)"
    override val search = "Search"
    override val playAll = "Play all"
    override val recentSearches = "Recent searches"
    override val noResults = "Nothing found"
    override fun searchFailedFor(keywords: List<String>) =
        "Could not search for ${keywords.joinToString(", ") { "“$it”" }}"
    override val clearResults = "Clear results"
    override val playOnSpeaker = "Play on the speaker"
    override val playingOnSpeaker = "Playing on the speaker"
    override val startingOnSpeaker = "Starting on the speaker"
    override val removeRecent = "Remove from recent searches"
    override fun searchedAt(day: String, time: String) = "$day, $time"

    override val chatEmpty = "No messages yet. Message the speaker below."
    override val chatHint = "Message the speaker"
    override val send = "Send"
    override val hideKeyboard = "Hide the keyboard"
    override fun chatNotSent(reason: String) = "Not sent. $reason"
    override val today = "Today"
    override val yesterday = "Yesterday"

    override val settingsAudio = "Audio"
    override val settingsVoice = "Voice"
    override val settingsNews = "News"
    override val settingsBluetooth = "Bluetooth"
    override val settingsAdvanced = "Advanced"
    override val loudness = "Loudness boost"
    override val equalizer = "Equalizer"
    override val newsEnabled = "Daily bulletin"
    override val newsTime = "Time"
    override val newsVoice = "Voice"
    override val newsTest = "Play a test"
    override val bluetoothEnabled = "Bluetooth"
    override val bluetoothScan = "Scan for speakers"
    override val bluetoothAutoReconnect = "Reconnect automatically"
    override val addressLabel = "Speaker address"
    override val addressHint = "e.g. 192.168.1.20"
    override val restartSpeaker = "Restart the speaker's app"
    override val restartConfirm = "Restart the speaker's app? It is silent for a few seconds."
    override fun newsCategory(key: String) = when (key) {
        "tech" -> "Tech news"
        "world" -> "World news"
        "society" -> "Local news"
        "weather" -> "Weather"
        "power" -> "Power cut schedule"
        else -> key
    }

    override val close = "Close"
    override val live = "LIVE"
    override val clear = "Clear"
    override val save = "Save"

    override val welcomeTitle = "Connect to your R1"
    override val welcomeBody = "Type the speaker's address on your wifi. You can find it in your router's device list."
    override val connect = "Connect"
    override val addressChecking = "Checking…"
    override val addressSaved = "Saved"

    override val volume = "Volume"

    override val searchResults = "Results"
    override val musicEmptyHint = "Type a song or an artist. Separate several with commas to mix their results."
    override val searching = "Searching…"
    override val openControl = "Open the player"

    override val loudnessHint = "Makes quiet sound louder. Above +3 dB the built-in speaker can crackle; a Bluetooth speaker takes more."
    override val equalizerBands = "Bands"
    override val voiceNoServer = "The speaker did not name a voice server, so there is no list of voices."
    override val voiceAll = "All voices"
    override val btOutputTitle = "Play to a Bluetooth speaker"
    override val btInputTitle = "Play from your phone"
    override val btOutputPage = "Bluetooth speaker"
    override val btInputPage = "From your phone"
    override val btPhones = "Phones"
    override val btTurnOn = "Turn on Bluetooth"
    override val btAllowPhone = "Let a phone connect"
    override val btInputHint = "Tap the button, then pick the speaker in your phone's Bluetooth list."
    override val newsCategories = "Topics, in reading order"
    override val newsTestHint = "Reads a bulletin now, as set above. Music that is playing stops."
    override val newsTestStarted = "Sent. The speaker starts reading in about 12 seconds."
    override val hours = "Hours"
    override val minutes = "Minutes"
    override val increase = "Increase"
    override val decrease = "Decrease"
    override val btOff = "Bluetooth is off."
    override val btUnsupported = "This speaker has no Bluetooth."
    override val btNone = "Nothing connected. Sound comes from the speaker itself."
    override val btPairing = "Pairing…"
    override val btConnecting = "Connecting…"
    override val btNoDevices = "No speakers yet. Tap Scan."
    override val btScanning = "Looking for speakers…"
    override val btPair = "Pair"
    override val btConnect = "Connect"
    override val btDisconnect = "Disconnect"
    override val btForget = "Forget"
    override val btPaired = "paired"
    override val btStreamingIn = "playing into the speaker"
    override val btPlayingThrough = "the R1 is playing through this"
    override fun btError(reason: String) = "The speaker's Bluetooth reported a problem: $reason"
    override val btSummaryOff = "Off"
    override val btSummaryNone = "Not connected"
    override fun btSummaryOutput(name: String) = "Playing through $name"
    override fun audioSummary(boost: String, eqOn: Boolean) = "Boost $boost · Equalizer ${if (eqOn) "on" else "off"}"
    override fun newsSummary(on: Boolean, time: String) = if (on) "Every day at $time" else "Off"
    override fun btReceiving(adapterName: String, seconds: Int) =
        "On your phone, open Bluetooth and choose $adapterName ($seconds s left)."
    override fun btWaitingForPhone(seconds: Int) = "Waiting for your phone ($seconds s left)"
    override fun btIncoming(name: String) = "Receiving music from $name"
    override fun btOutput(name: String) = "Playing through $name. The speaker itself is silent."
    override fun btForgetConfirm(name: String) = "Forget $name? You will have to pair it again."
    override val restartButton = "Restart"
    override val restarting = "Restarting…"
    override val restartDone = "Restarting. The speaker is back in a few seconds."
    override fun appVersion(version: String) = "Version $version"

    override val playlists = "Playlists"
    override val playlist = "Playlist"
    override val more = "More"
    override val newPlaylist = "New playlist"
    override val untitledPlaylist = "Untitled playlist"
    override fun songCount(n: Int) = if (n == 1) "1 song" else "$n songs"
    override val playlistName = "Playlist name"
    override val create = "Create"
    override val renamePlaylist = "Rename playlist"
    override val deletePlaylist = "Delete playlist"
    override fun deletePlaylistQuestion(name: String) = "Delete “$name”? This cannot be undone."
    override val delete = "Delete"
    override val shuffle = "Shuffle"
    override val addToPlaylist = "Add to a playlist"
    override val addAll = "Add all"
    override val alreadyInPlaylist = "Already in this playlist"
    override val playingFromHere = "Playing from this playlist"
    override val nowPlaying = "Now playing"
    override val clearSearch = "Clear search"
    override val songsPlaying = "Songs playing"
    override fun playingFromPlaylist(name: String) = "Playing from $name"
    override val playingFromResults = "Playing from search results"
    override val playingFromSpeaker = "Playing from the speaker's own queue"
    override val nowPlayingNothing = "Nothing is playing. Find a song to play on the speaker."
    override val playlistEmptyHint = "No songs yet. Search for music and tap the add button on a song."
    override val playlistsUnreadable = "The saved playlists could not be read, so none are shown. Nothing was changed."
    override val playlistsKeptAside = "The saved playlists could not be read. They were kept aside, and this list starts empty."
    override val playlistsNotSaved = "The last change may be lost when the app closes."
    override val playlistsLockedNoAdding = "Songs cannot be added to a playlist: the saved playlists could not be read."
    override val findSongs = "Find songs"
    override fun reorderSong(title: String) = "Move $title"
    override fun removeSong(title: String) = "Remove $title"
    override fun reorderCategory(name: String) = "Move $name"
    override val moveUp = "Move up"
    override val moveDown = "Move down"

    override val settingsLanguage = "Language"
    override val languageFollowPhone = "Follow the phone"
    override fun languageFollowPhoneNow(deviceName: String) = "The phone is set to $deviceName"

    override val wakeWord = "Wake word"
    override val wakeWordHint = "When off, only the button, typing, Play and the news wake the speaker."
    override val endConversation = "End the conversation"
    override val endingConversation = "Ending…"

    override val errorNotConfigured = "Set the speaker's address first."
    override fun errorUnreachable(onIphone: Boolean) =
        if (onIphone) {
            "Could not reach the speaker. Is the phone on the house wifi, and is Control on in Settings › Privacy & Security › Local Network?"
        } else {
            "Could not reach the speaker. Is the phone on the house wifi?"
        }
    override val errorBadReply = "The speaker answered with something unreadable."
    override fun errorRefused(reason: String) =
        if (reason.isEmpty()) "The speaker refused." else "The speaker refused (it said: $reason)."
    override fun addressInvalid(reason: AddressInput.Reason) = when (reason) {
        AddressInput.Reason.Empty -> "Type the speaker's address."
        AddressInput.Reason.BadHost -> "That is not an address. Use a form like 192.168.1.20."
        AddressInput.Reason.BadPort -> "The port must be a number from 1 to 65535."
    }
    override fun addressNotR1(address: String) = "Something answered at $address, but it is not an R1. Check the address and the port."
    override fun addressUnreachable(address: String, onIphone: Boolean) =
        "No speaker found at $address. Check that it is on and that the phone is on the house wifi." +
            if (onIphone) {
                " If you tapped Don't Allow when iOS asked about the local network, turn Control on in " +
                    "Settings › Privacy & Security › Local Network, then come back."
            } else {
                ""
            }
    override val haTitle = "Home Assistant"
    override val haSettingsTitle = "Home Assistant connection"
    override val haWelcomeTitle = "Connect to Home Assistant"
    override val haWelcomeBody = "Type your Home Assistant's address on your wifi and a long-lived access token. Create one in Home Assistant under your profile, Security, Long-lived access tokens."
    override val haAddressHint = "e.g. 192.168.1.10:8123"
    override val haTokenHint = "Long-lived access token"
    override val haChecking = "Connecting…"
    override val haConnected = "Connected"
    override val haTokenRefused = "Home Assistant refused this token. Create a new long-lived access token and paste it here."
    override fun haInvalid(reason: HaInput.Reason) = when (reason) {
        HaInput.Reason.EmptyAddress -> "Type Home Assistant's address."
        HaInput.Reason.BadHost -> "That is not an address. Use a form like 192.168.1.10 or homeassistant.local."
        HaInput.Reason.BadPort -> "The port must be a number from 1 to 65535."
        HaInput.Reason.EmptyToken -> "Paste a long-lived access token."
        HaInput.Reason.BadToken -> "A token has no spaces in it. Paste it again."
    }
    override fun haUnreachable(address: String, onIphone: Boolean) =
        "Home Assistant did not answer at $address. Check the address and that the phone is on the house wifi." +
            if (onIphone) " If you tapped Don't Allow when iOS asked about the local network, turn Control on in Settings › Privacy & Security › Local Network." else ""
    override fun haConnection(state: HaConnectionState) = when (state) {
        HaConnectionState.Disconnected -> "Not connected"
        HaConnectionState.Connecting -> "Connecting…"
        HaConnectionState.Connected -> "Connected"
        HaConnectionState.AuthInvalid -> "Token refused"
        HaConnectionState.Unreachable -> "Not answering, trying again"
    }
    override fun haEntityCount(count: Int) = if (count == 1) "1 entity" else "$count entities"
    override val haNoEntities = "No entities yet"
    override val haDashboardLoading = "Loading your Home Assistant dashboard…"
    override val haDashboardFailed = "Could not read your Home Assistant dashboard. Showing every device instead."
    override val haDashboardEmpty = "Your dashboard is empty. Tap the pencil to add sections and tiles."
    override val haCoverOpen = "Open"
    override val haCoverClose = "Close"
    override val haCoverStop = "Stop"
    override val haSyncTitle = "Dashboard"
    override val haSyncBody = "Replace your dashboard with Home Assistant's current one."
    override val haSyncButton = "Sync from Home Assistant"
    override val haSyncWarning = "This replaces your dashboard with Home Assistant's. The tiles, sections and order you changed here are lost."
    override val haSyncConfirm = "Sync"
    override val haSyncing = "Syncing…"
    override val haSynced = "Synced from Home Assistant."
    override val haSyncFailed = "Could not sync. Check the connection and try again."
    override val haEdit = "Edit dashboard"
    override val haDoneEditing = "Done editing"
    override val haEmptyEdit = "Nothing here yet. Add a section, then add tiles to it."
    override val haStartEmpty = "Build my own dashboard"
    override val haTabUntitled = "Tab"
    override val haTabAdd = "Add tab"
    override val haTabRename = "Tab name"
    override val haTabChooseIcon = "Choose an icon"
    override val haTabDeleteTitle = "Delete this tab?"
    override val haTabDeleteBody = "The tab and everything in it is removed from this phone. Home Assistant is not changed."
    override val haAddSection = "Add section"
    override val haNewSection = "New section"
    override val haSectionName = "Section name"
    override val haRenameSection = "Rename section"
    override val haUntitledSection = "Untitled section"
    override fun haDeleteSectionQuestion(title: String, tiles: Int) = when (tiles) {
        0 -> "Delete “$title”?"
        1 -> "Delete “$title” and its tile?"
        else -> "Delete “$title” and its $tiles tiles?"
    }
    override val haAddTile = "Add tile"
    override val haRenameTile = "Rename tile"
    override val haTileName = "Tile name"
    override val haPickEntity = "Pick a device"
    override val haSearchEntities = "Search by name or id"
    override val haNothingToPick = "Every device is already on your dashboard."
    override val haNoEntityMatches = "No device matches."
    override fun haReorderSection(title: String) = "Move section $title"
    override fun haReorderTile(title: String) = "Move $title"
    override fun haRemoveSection(title: String) = "Delete section $title"
    override fun haRemoveTile(title: String) = "Remove $title"
    override val haConditionalTile = "Shown only when a condition holds"
    override val haTemplateTile = "Template"
    override val haBrightness = "Brightness"
    override val haFanSpeed = "Speed"
    override val haOscillation = "Oscillation"
    override val haFanMode = "Mode"
    override val haPower = "Power"
    override val haColor = "Colour"
    override val haWhite = "White"
}

object VietnameseStrings : Strings {
    override val appName = "Control"
    override val speakerName = "R1"
    override val back = "Quay lại"
    override val tryAgain = "Thử lại"
    override val cancel = "Huỷ"

    override val tabHa = "HA"
    override val tabControl = "R1"
    override val tabMusic = "Nhạc"
    override val tabChat = "Trò chuyện"
    override val settings = "Cài đặt"

    override val nothingPlaying = "Chưa phát gì"
    override val nothingPlayingHint = "Chọn bài ở mục Nhạc, hoặc trò chuyện với loa."
    override val assistantListening = "Đang nghe…"
    override val assistantSpeaking = "Đang trả lời"
    override val speakerOffline = "Loa không trả lời"
    override fun speakerOfflineHint(onIphone: Boolean) =
        "Kiểm tra loa đã bật, điện thoại đang bắt wifi nhà và địa chỉ loa đúng." +
            if (onIphone) " Xem thêm Cài đặt › Quyền riêng tư & Bảo mật › Mạng cục bộ: Control phải được bật." else ""
    override val changeAddress = "Đổi địa chỉ"
    override val play = "Phát"
    override val pause = "Tạm dừng"
    override val next = "Bài tiếp theo"
    override val stop = "Dừng"
    override fun streamingFrom(deviceName: String) = "Đang phát từ $deviceName"
    override fun downloading(percent: Int) = "Đang chuẩn bị… $percent%"
    override fun onSpeaker(name: String) = "Phát qua $name"
    override fun volumePercent(pct: Int) = "$pct%"
    override fun percent(value: Int) = "$value%"
    override val seekPosition = "Vị trí phát"
    override fun playbackPosition(position: String, duration: String) = "$position trên $duration"

    override val keywordHint = "Bài hát, ca sĩ (a, b, c)"
    override val search = "Tìm"
    override val playAll = "Phát tất cả"
    override val recentSearches = "Tìm gần đây"
    override val noResults = "Không tìm thấy"
    override fun searchFailedFor(keywords: List<String>) =
        "Không tìm được ${keywords.joinToString(", ") { "“$it”" }}"
    override val clearResults = "Xoá kết quả"
    override val playOnSpeaker = "Phát trên loa"
    override val playingOnSpeaker = "Đang phát trên loa"
    override val startingOnSpeaker = "Đang mở bài trên loa"
    override val removeRecent = "Xoá khỏi tìm gần đây"
    override fun searchedAt(day: String, time: String) = "$day, $time"

    override val chatEmpty = "Chưa có tin nhắn. Nhắn cho loa ở bên dưới."
    override val chatHint = "Nhắn cho loa"
    override val send = "Gửi"
    override val hideKeyboard = "Ẩn bàn phím"
    override fun chatNotSent(reason: String) = "Chưa gửi được. $reason"
    override val today = "Hôm nay"
    override val yesterday = "Hôm qua"

    override val settingsAudio = "Âm thanh"
    override val settingsVoice = "Giọng đọc"
    override val settingsNews = "Bản tin"
    override val settingsBluetooth = "Bluetooth"
    override val settingsAdvanced = "Nâng cao"
    override val loudness = "Tăng độ to"
    override val equalizer = "Bộ chỉnh âm (EQ)"
    override val newsEnabled = "Bản tin hằng ngày"
    override val newsTime = "Giờ đọc"
    override val newsVoice = "Giọng đọc"
    override val newsTest = "Phát thử"
    override val bluetoothEnabled = "Bluetooth"
    override val bluetoothScan = "Quét loa Bluetooth"
    override val bluetoothAutoReconnect = "Tự kết nối lại"
    override val addressLabel = "Địa chỉ loa"
    override val addressHint = "Ví dụ: 192.168.1.20"
    override val restartSpeaker = "Khởi động lại ứng dụng trên loa"
    override val restartConfirm = "Khởi động lại ứng dụng trên loa? Loa sẽ im vài giây."
    override fun newsCategory(key: String) = when (key) {
        "tech" -> "Tin công nghệ"
        "world" -> "Tin nước ngoài"
        "society" -> "Tin trong nước"
        "weather" -> "Thời tiết"
        "power" -> "Lịch cúp điện"
        else -> key
    }

    override val close = "Đóng"
    override val live = "TRỰC TIẾP"
    override val clear = "Xoá"
    override val save = "Lưu"

    override val welcomeTitle = "Kết nối với loa R1"
    override val welcomeBody = "Nhập địa chỉ của loa trong wifi nhà. Xem trong danh sách thiết bị của router."
    override val connect = "Kết nối"
    override val addressChecking = "Đang kiểm tra…"
    override val addressSaved = "Đã lưu"

    override val volume = "Âm lượng"

    override val searchResults = "Kết quả"
    override val musicEmptyHint = "Nhập tên bài hoặc ca sĩ. Cách nhau bằng dấu phẩy để trộn kết quả."
    override val searching = "Đang tìm…"
    override val openControl = "Mở trình phát"

    override val loudnessHint = "Làm tiếng nhỏ to hơn. Trên +3 dB loa trong máy có thể bị rè; loa Bluetooth chịu được nhiều hơn."
    override val equalizerBands = "Các dải"
    override val voiceNoServer = "Loa không cho biết máy chủ giọng đọc nên chưa có danh sách giọng."
    override val voiceAll = "Tất cả giọng"
    override val btOutputTitle = "Phát ra loa Bluetooth"
    override val btInputTitle = "Nhận nhạc từ điện thoại"
    override val btOutputPage = "Loa Bluetooth"
    override val btInputPage = "Từ điện thoại"
    override val btPhones = "Điện thoại"
    override val btTurnOn = "Bật Bluetooth"
    override val btAllowPhone = "Cho điện thoại kết nối"
    override val btInputHint = "Bấm nút bên dưới, rồi chọn loa R1 trong danh sách Bluetooth của điện thoại."
    override val newsCategories = "Chủ đề, theo thứ tự đọc"
    override val newsTestHint = "Loa đọc ngay một bản tin theo cài đặt ở trên. Nhạc đang phát sẽ dừng."
    override val newsTestStarted = "Đã gửi. Khoảng 12 giây nữa loa bắt đầu đọc."
    override val hours = "Giờ"
    override val minutes = "Phút"
    override val increase = "Tăng"
    override val decrease = "Giảm"
    override val btOff = "Bluetooth đang tắt."
    override val btUnsupported = "Loa này không có Bluetooth."
    override val btNone = "Chưa kết nối thiết bị nào. Tiếng phát ra từ chính loa R1."
    override val btPairing = "Đang ghép đôi…"
    override val btConnecting = "Đang kết nối…"
    override val btNoDevices = "Chưa thấy loa nào. Bấm Quét."
    override val btScanning = "Đang tìm loa…"
    override val btPair = "Ghép đôi"
    override val btConnect = "Kết nối"
    override val btDisconnect = "Ngắt kết nối"
    override val btForget = "Bỏ ghép đôi"
    override val btPaired = "đã ghép đôi"
    override val btStreamingIn = "đang phát vào loa"
    override val btPlayingThrough = "loa R1 đang phát qua loa này"
    override fun btError(reason: String) = "Bluetooth trên loa báo lỗi: $reason"
    override val btSummaryOff = "Đang tắt"
    override val btSummaryNone = "Chưa kết nối"
    override fun btSummaryOutput(name: String) = "Đang phát qua $name"
    override fun audioSummary(boost: String, eqOn: Boolean) = "Tăng độ to $boost · EQ ${if (eqOn) "bật" else "tắt"}"
    override fun newsSummary(on: Boolean, time: String) = if (on) "Mỗi ngày lúc $time" else "Đang tắt"
    override fun btReceiving(adapterName: String, seconds: Int) =
        "Trên điện thoại, mở Bluetooth và chọn $adapterName (còn $seconds giây)."
    override fun btWaitingForPhone(seconds: Int) = "Đang chờ điện thoại (còn $seconds giây)"
    override fun btIncoming(name: String) = "Đang nhận nhạc từ $name"
    override fun btOutput(name: String) = "Đang phát qua $name. Loa trong máy im."
    override fun btForgetConfirm(name: String) = "Bỏ ghép đôi với $name? Muốn dùng lại phải ghép đôi lại."
    override val restartButton = "Khởi động lại"
    override val restarting = "Đang khởi động lại…"
    override val restartDone = "Đang khởi động lại. Vài giây nữa loa sẽ sẵn sàng."
    override fun appVersion(version: String) = "Phiên bản $version"

    override val playlists = "Danh sách phát"
    override val playlist = "Danh sách phát"
    override val more = "Xem thêm"
    override val newPlaylist = "Danh sách phát mới"
    override val untitledPlaylist = "Danh sách chưa đặt tên"
    override fun songCount(n: Int) = "$n bài"
    override val playlistName = "Tên danh sách phát"
    override val create = "Tạo"
    override val renamePlaylist = "Đổi tên danh sách phát"
    override val deletePlaylist = "Xoá danh sách phát"
    override fun deletePlaylistQuestion(name: String) = "Xoá “$name”? Không thể hoàn tác."
    override val delete = "Xoá"
    override val shuffle = "Phát ngẫu nhiên"
    override val addToPlaylist = "Thêm vào danh sách phát"
    override val addAll = "Thêm tất cả"
    override val alreadyInPlaylist = "Đã có trong danh sách này"
    override val playingFromHere = "Đang phát từ danh sách này"
    override val nowPlaying = "Đang phát"
    override val clearSearch = "Xoá ô tìm"
    override val songsPlaying = "Các bài đang phát"
    override fun playingFromPlaylist(name: String) = "Đang phát từ $name"
    override val playingFromResults = "Đang phát từ kết quả tìm kiếm"
    override val playingFromSpeaker = "Đang phát từ hàng đợi của loa"
    override val nowPlayingNothing = "Loa chưa phát gì. Tìm một bài để phát trên loa."
    override val playlistEmptyHint = "Chưa có bài nào. Tìm nhạc rồi bấm nút thêm ở một bài."
    override val playlistsUnreadable = "Không đọc được các danh sách phát đã lưu nên chưa hiện danh sách nào. Dữ liệu vẫn được giữ nguyên."
    override val playlistsKeptAside = "Không đọc được các danh sách phát đã lưu. Bản cũ được giữ riêng, danh sách bắt đầu lại từ trống."
    override val playlistsNotSaved = "Thay đổi vừa rồi có thể mất khi đóng ứng dụng."
    override val playlistsLockedNoAdding = "Chưa thêm được bài vào danh sách phát: không đọc được các danh sách đã lưu."
    override val findSongs = "Tìm bài hát"
    override fun reorderSong(title: String) = "Đổi vị trí $title"
    override fun removeSong(title: String) = "Bỏ $title khỏi danh sách"
    override fun reorderCategory(name: String) = "Đổi vị trí $name"
    override val moveUp = "Chuyển lên"
    override val moveDown = "Chuyển xuống"

    override val settingsLanguage = "Ngôn ngữ"
    override val languageFollowPhone = "Theo điện thoại"
    override fun languageFollowPhoneNow(deviceName: String) = "Điện thoại đang dùng $deviceName"

    override val wakeWord = "Từ đánh thức"
    override val wakeWordHint = "Khi tắt, loa chỉ thức dậy bằng nút bấm, tin nhắn, phát nhạc hoặc bản tin."
    override val endConversation = "Kết thúc trò chuyện"
    override val endingConversation = "Đang kết thúc…"

    override val errorNotConfigured = "Hãy nhập địa chỉ của loa trước."
    override fun errorUnreachable(onIphone: Boolean) =
        if (onIphone) {
            "Không nối được với loa. Điện thoại có đang dùng wifi nhà không, và Control đã được bật trong Cài đặt › Quyền riêng tư & Bảo mật › Mạng cục bộ chưa?"
        } else {
            "Không nối được với loa. Điện thoại có đang dùng wifi nhà không?"
        }
    override val errorBadReply = "Loa trả lời không đúng dạng."
    override fun errorRefused(reason: String) =
        if (reason.isEmpty()) "Loa không làm theo yêu cầu." else "Loa không làm theo yêu cầu (loa báo: $reason)."
    override fun addressInvalid(reason: AddressInput.Reason) = when (reason) {
        AddressInput.Reason.Empty -> "Hãy nhập địa chỉ của loa."
        AddressInput.Reason.BadHost -> "Địa chỉ không đúng. Dạng như 192.168.1.20."
        AddressInput.Reason.BadPort -> "Cổng phải là số từ 1 đến 65535."
    }
    override fun addressNotR1(address: String) = "Có máy trả lời ở $address nhưng không phải loa R1. Kiểm tra lại địa chỉ và cổng."
    override fun addressUnreachable(address: String, onIphone: Boolean) =
        "Không thấy loa ở $address. Kiểm tra loa đang bật và điện thoại đang dùng wifi nhà." +
            if (onIphone) {
                " Nếu lúc iOS hỏi về mạng cục bộ bạn đã chọn Không cho phép, hãy bật Control trong " +
                    "Cài đặt › Quyền riêng tư & Bảo mật › Mạng cục bộ rồi quay lại đây."
            } else {
                ""
            }
    override val haTitle = "Home Assistant"
    override val haSettingsTitle = "Kết nối Home Assistant"
    override val haWelcomeTitle = "Kết nối với Home Assistant"
    override val haWelcomeBody = "Nhập địa chỉ Home Assistant trong wifi nhà và một mã truy cập dài hạn. Tạo mã trong Home Assistant: hồ sơ, Bảo mật, Mã truy cập dài hạn."
    override val haAddressHint = "Ví dụ: 192.168.1.10:8123"
    override val haTokenHint = "Mã truy cập dài hạn"
    override val haChecking = "Đang kết nối…"
    override val haConnected = "Đã kết nối"
    override val haTokenRefused = "Home Assistant từ chối mã này. Hãy tạo mã truy cập dài hạn mới rồi dán vào đây."
    override fun haInvalid(reason: HaInput.Reason) = when (reason) {
        HaInput.Reason.EmptyAddress -> "Hãy nhập địa chỉ của Home Assistant."
        HaInput.Reason.BadHost -> "Địa chỉ không đúng. Dạng như 192.168.1.10 hoặc homeassistant.local."
        HaInput.Reason.BadPort -> "Cổng phải là số từ 1 đến 65535."
        HaInput.Reason.EmptyToken -> "Hãy dán mã truy cập dài hạn."
        HaInput.Reason.BadToken -> "Mã không có khoảng trắng. Hãy dán lại."
    }
    override fun haUnreachable(address: String, onIphone: Boolean) =
        "Home Assistant không trả lời ở $address. Kiểm tra địa chỉ và điện thoại đang dùng wifi nhà." +
            if (onIphone) " Nếu lúc iOS hỏi về mạng cục bộ bạn đã chọn Không cho phép, hãy bật Control trong Cài đặt › Quyền riêng tư & Bảo mật › Mạng cục bộ." else ""
    override fun haConnection(state: HaConnectionState) = when (state) {
        HaConnectionState.Disconnected -> "Chưa kết nối"
        HaConnectionState.Connecting -> "Đang kết nối…"
        HaConnectionState.Connected -> "Đã kết nối"
        HaConnectionState.AuthInvalid -> "Mã bị từ chối"
        HaConnectionState.Unreachable -> "Không trả lời, đang thử lại"
    }
    override fun haEntityCount(count: Int) = "$count thiết bị"
    override val haNoEntities = "Chưa có thiết bị nào"
    override val haDashboardLoading = "Đang tải màn hình Home Assistant của bạn…"
    override val haDashboardFailed = "Không đọc được màn hình Home Assistant của bạn. Đang hiện tất cả thiết bị."
    override val haDashboardEmpty = "Màn hình của bạn đang trống. Chạm vào bút chì để thêm nhóm và ô."
    override val haCoverOpen = "Mở"
    override val haCoverClose = "Đóng"
    override val haCoverStop = "Dừng"
    override val haSyncTitle = "Bảng điều khiển"
    override val haSyncBody = "Thay bảng điều khiển bằng bản hiện tại bên Home Assistant."
    override val haSyncButton = "Đồng bộ từ Home Assistant"
    override val haSyncWarning = "Việc này thay bảng điều khiển bằng bản của Home Assistant. Mọi tile, mục và thứ tự bạn đã chỉnh ở đây sẽ mất."
    override val haSyncConfirm = "Đồng bộ"
    override val haSyncing = "Đang đồng bộ…"
    override val haSynced = "Đã đồng bộ từ Home Assistant."
    override val haSyncFailed = "Không đồng bộ được. Kiểm tra kết nối rồi thử lại."
    override val haEdit = "Sửa màn hình"
    override val haDoneEditing = "Xong"
    override val haEmptyEdit = "Chưa có gì. Hãy thêm một nhóm, rồi thêm ô vào nhóm đó."
    override val haStartEmpty = "Tự tạo màn hình"
    override val haTabUntitled = "Thẻ"
    override val haTabAdd = "Thêm thẻ"
    override val haTabRename = "Tên thẻ"
    override val haTabChooseIcon = "Chọn biểu tượng"
    override val haTabDeleteTitle = "Xoá thẻ này?"
    override val haTabDeleteBody = "Thẻ và mọi thứ trong nó sẽ bị gỡ khỏi điện thoại này. Home Assistant không thay đổi."
    override val haAddSection = "Thêm nhóm"
    override val haNewSection = "Nhóm mới"
    override val haSectionName = "Tên nhóm"
    override val haRenameSection = "Đổi tên nhóm"
    override val haUntitledSection = "Nhóm không tên"
    override fun haDeleteSectionQuestion(title: String, tiles: Int) =
        if (tiles == 0) "Xoá “$title”?" else "Xoá “$title” và $tiles ô trong nhóm?"
    override val haAddTile = "Thêm ô"
    override val haRenameTile = "Đổi tên ô"
    override val haTileName = "Tên ô"
    override val haPickEntity = "Chọn thiết bị"
    override val haSearchEntities = "Tìm theo tên hoặc mã"
    override val haNothingToPick = "Mọi thiết bị đã có trên màn hình."
    override val haNoEntityMatches = "Không có thiết bị nào khớp."
    override fun haReorderSection(title: String) = "Đổi vị trí nhóm $title"
    override fun haReorderTile(title: String) = "Đổi vị trí $title"
    override fun haRemoveSection(title: String) = "Xoá nhóm $title"
    override fun haRemoveTile(title: String) = "Bỏ $title"
    override val haConditionalTile = "Chỉ hiện khi có điều kiện"
    override val haTemplateTile = "Mẫu"
    override val haBrightness = "Độ sáng"
    override val haFanSpeed = "Tốc độ"
    override val haOscillation = "Xoay"
    override val haFanMode = "Chế độ"
    override val haPower = "Nguồn"
    override val haColor = "Màu"
    override val haWhite = "Trắng"
}

enum class Language {
    English, Vietnamese;

    val strings: Strings
        get() = when (this) {
            English -> EnglishStrings
            Vietnamese -> VietnameseStrings
        }

    companion object {
        /** The device's language, or English; only the primary subtag counts (`vi-VN` is Vietnamese). */
        fun forTag(tag: String): Language =
            if (tag.substringBefore('-').lowercase() == "vi") Vietnamese else English
    }
}

/** The language a choice means on a phone set to [device]. */
fun LanguageChoice.resolve(device: Language): Language = when (this) {
    LanguageChoice.FollowPhone -> device
    LanguageChoice.Vietnamese -> Language.Vietnamese
    LanguageChoice.English -> Language.English
}

/** A language in its own words, always, whatever language the app is in. */
val Language.ownName: String
    get() = when (this) {
        Language.English -> "English"
        Language.Vietnamese -> "Tiếng Việt"
    }

/** A choice as the Language page and the Settings row name it. */
fun Strings.languageChoiceName(choice: LanguageChoice): String = when (choice) {
    LanguageChoice.FollowPhone -> languageFollowPhone
    LanguageChoice.Vietnamese -> Language.Vietnamese.ownName
    LanguageChoice.English -> Language.English.ownName
}

/** The language this device is set to. */
expect fun deviceLanguage(): Language

val LocalStrings = compositionLocalOf<Strings> { EnglishStrings }
