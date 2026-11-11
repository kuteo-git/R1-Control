package com.r1control.app.testing

import com.r1control.app.domain.model.AudioSettings
import com.r1control.app.domain.model.BluetoothDevice
import com.r1control.app.domain.model.BluetoothState
import com.r1control.app.domain.model.ChatMessage
import com.r1control.app.domain.model.MediaState
import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.NewsSettings
import com.r1control.app.domain.model.LanguageChoice
import com.r1control.app.domain.model.Playlist
import com.r1control.app.domain.model.R1Address
import com.r1control.app.domain.model.RecentSearch
import com.r1control.app.domain.model.SavedAddress
import com.r1control.app.domain.model.SpeakerSettings
import com.r1control.app.domain.model.SpeakerSnapshot
import com.r1control.app.domain.model.StoredPlaylists
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.model.VoiceActivity
import com.r1control.app.domain.model.VoiceControls
import com.r1control.app.domain.model.Volume
import com.r1control.app.domain.model.VolumeScale
import com.r1control.app.domain.repository.PreferencesRepository
import com.r1control.app.domain.repository.SpeakerRepository
import com.r1control.app.domain.model.PlaylistId
import com.r1control.app.domain.usecase.Created
import com.r1control.app.domain.usecase.MusicStore
import com.r1control.app.domain.usecase.SpeakerFeed
import kotlinx.coroutines.CoroutineScope
import kotlin.random.Random

fun track(id: String, title: String = "t$id", artist: String = "a") =
    Track(id = id, title = title, artist = artist, duration = "3:00", thumbnailUrl = "")

fun media(
    status: MediaStatus = MediaStatus.Idle,
    videoId: String = "",
    title: String = "",
    positionS: Int = 0,
    durationS: Int = 0,
    downloadPercent: Int = -1,
    queue: List<Track> = emptyList(),
) = MediaState(status, videoId, title, "", "", durationS, positionS, downloadPercent, queue)

fun bluetooth(
    incoming: String = "",
    connected: String = "",
    devices: List<BluetoothDevice> = emptyList(),
    receiveSecondsLeft: Int = 0,
) = BluetoothState(
    supported = true, enabled = true, scanning = false, autoReconnect = true,
    adapterName = "R1", receiveSecondsLeft = receiveSecondsLeft, incomingAddress = incoming,
    connectedAddress = connected, busy = "", error = "", devices = devices,
)

fun device(address: String, name: String) =
    BluetoothDevice(address, name, bonded = true, audio = true, connected = false, streamingIn = false, phone = false)

fun speakerSnapshot(
    media: MediaState = media(),
    bluetooth: BluetoothState = bluetooth(),
    voice: VoiceActivity = VoiceActivity.Idle,
    voiceAwake: Boolean = false,
    chat: List<ChatMessage> = emptyList(),
    voiceControls: VoiceControls = VoiceControls.Absent,
    settings: SpeakerSettings = speakerSettings(),
) = SpeakerSnapshot(Volume(40, VolumeScale(15)), voice, voiceAwake, chat, media, bluetooth, voiceControls, settings)

/** A repository whose every call fails loudly; a test overrides only what it uses. */
open class StubSpeaker : SpeakerRepository {
    private fun unused(): Nothing = error("not used by this test")
    override suspend fun snapshot(): SpeakerSnapshot = unused()
    override suspend fun settings(): SpeakerSettings = unused()
    override suspend fun probe(address: R1Address): Unit = unused()
    override suspend fun setVolume(pct: Int): Unit = unused()
    override suspend fun pause(): Unit = unused()
    override suspend fun resume(): Unit = unused()
    override suspend fun next(): Unit = unused()
    override suspend fun stop(): Unit = unused()
    override suspend fun seek(positionS: Int): Unit = unused()
    override suspend fun play(tracks: List<Track>, startIndex: Int): Unit = unused()
    override suspend fun queue(tracks: List<Track>): Unit = unused()
    override suspend fun search(keyword: String): List<Track> = unused()
    override suspend fun say(text: String): Unit = unused()
    override suspend fun setLoudness(mb: Int): Unit = unused()
    override suspend fun setEqEnabled(enabled: Boolean): Unit = unused()
    override suspend fun setEqBands(gainsMb: List<Int>): Unit = unused()
    override suspend fun saveNews(news: NewsSettings): Unit = unused()
    override suspend fun testNews(): Unit = unused()
    override suspend fun bluetooth(): BluetoothState = unused()
    override suspend fun setBluetoothEnabled(enabled: Boolean): Unit = unused()
    override suspend fun startBluetoothScan(): Unit = unused()
    override suspend fun stopBluetoothScan(): Unit = unused()
    override suspend fun pair(address: String): Unit = unused()
    override suspend fun connect(address: String): Unit = unused()
    override suspend fun disconnect(address: String): Unit = unused()
    override suspend fun forget(address: String): Unit = unused()
    override suspend fun setAutoReconnect(enabled: Boolean): Unit = unused()
    override suspend fun receiveFromPhone(): Unit = unused()
    override suspend fun restart(): Unit = unused()
    override suspend fun endVoiceSession(): Unit = unused()
    override suspend fun setWakeWord(enabled: Boolean): Unit = unused()
}

open class MemoryPreferences(
    var address: SavedAddress = SavedAddress.Unset,
    var recents: List<RecentSearch> = emptyList(),
    var playlists: List<Playlist> = emptyList(),
    /** Makes [playlists] answer `Unreadable`, as a stored value that does not decode does. */
    var unreadablePlaylists: Boolean = false,
    /** With [unreadablePlaylists]: the raw value was copied aside before the answer, as the real repository does. */
    var unreadableBackedUp: Boolean = false,
    var language: LanguageChoice = LanguageChoice.FollowPhone,
) : PreferencesRepository {
    /** Every list [savePlaylists] was given, in order. */
    val playlistWrites = mutableListOf<List<Playlist>>()
    var failPlaylistWrites = false

    override suspend fun address() = address
    override suspend fun saveAddress(address: R1Address) { this.address = SavedAddress.Set(address) }
    override suspend fun recentSearches() = recents
    override suspend fun saveRecentSearches(searches: List<RecentSearch>) { recents = searches }

    override suspend fun playlists(): StoredPlaylists =
        if (unreadablePlaylists) StoredPlaylists.Unreadable(backedUp = unreadableBackedUp) else StoredPlaylists.Readable(playlists)

    override suspend fun savePlaylists(playlists: List<Playlist>) {
        if (failPlaylistWrites) error("storage is full")
        playlistWrites.add(playlists)
        this.playlists = playlists
    }

    override suspend fun language() = language
    override suspend fun saveLanguage(choice: LanguageChoice) { language = choice }
}

/** The store the view models share, over the same [feed] they read. */
fun musicStore(
    speaker: SpeakerRepository,
    feed: SpeakerFeed,
    scope: CoroutineScope,
    prefs: PreferencesRepository = MemoryPreferences(),
) = MusicStore(speaker, prefs, feed, scope, Random(1)) { 1_000L }

/** A playlist the test needs to exist: [MusicStore.create] over readable playlists and a name that is not blank. */
fun MusicStore.made(name: String, songs: List<Track>): PlaylistId {
    val made = create(name, songs)
    check(made is Created.Made) { "set up: the store refused to make \"$name\"" }
    return made.id
}
