package com.r1control.app.data.remote

import com.r1control.app.data.remote.dto.BtStateDto
import com.r1control.app.data.remote.dto.ChatDto
import com.r1control.app.data.remote.dto.MediaStateDto
import com.r1control.app.data.remote.dto.StateDto
import com.r1control.app.data.remote.dto.TrackDto
import com.r1control.app.data.remote.dto.VoicesDto
import com.r1control.app.domain.model.AudioSettings
import com.r1control.app.domain.model.BluetoothDevice
import com.r1control.app.domain.model.BluetoothState
import com.r1control.app.domain.model.ChatMessage
import com.r1control.app.domain.model.EqBand
import com.r1control.app.domain.model.MediaState
import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.NewsCategory
import com.r1control.app.domain.model.NewsSettings
import com.r1control.app.domain.model.Sender
import com.r1control.app.domain.model.SpeakerSettings
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.model.TtsVoices
import com.r1control.app.domain.model.VoiceActivity
import com.r1control.app.domain.model.VoiceControls
import com.r1control.app.domain.model.Volume
import com.r1control.app.domain.model.VolumeScale

// Absence is decided here and nowhere else: a missing field becomes the
// neutral value (empty text, 0, false, empty list), never a parse failure.

fun StateDto.toVolume(): Volume =
    Volume(
        pct = (volume ?: 0).coerceIn(0, 100),
        scale = VolumeScale((volumeSteps ?: 0).coerceAtLeast(0)),
    )

fun StateDto.toVoiceActivity(): VoiceActivity = when (voiceState) {
    "LISTENING" -> VoiceActivity.Listening
    "SPEAKING" -> VoiceActivity.Speaking
    else -> VoiceActivity.Idle
}

/**
 * `wake_enabled` absent is an R1 build without the listening routes: neither
 * control is drawn. Present, its value is the switch's.
 */
fun StateDto.toVoiceControls(): VoiceControls =
    wakeEnabled?.let { VoiceControls.Present(it) } ?: VoiceControls.Absent

fun StateDto.toChat(): List<ChatMessage> = chat.orEmpty().map { it.toDomain() }

/** Anything the R1 does not call `user` is the assistant's line. */
fun ChatDto.toDomain(): ChatMessage = ChatMessage(
    sender = if (sender == "user") Sender.User else Sender.Assistant,
    text = text.orEmpty(),
    timeMillis = time ?: 0L,
)

fun StateDto.toSettings(): SpeakerSettings {
    val freqs = eq?.freqs.orEmpty()
    val gains = eqBands.orEmpty()
    return SpeakerSettings(
        audio = AudioSettings(
            loudnessMb = loudnessMb ?: 0,
            eqEnabled = eqEnabled ?: false,
            bands = freqs.mapIndexed { i, hz -> EqBand(hz, gains.getOrElse(i) { 0 }) },
            eqMinMb = eq?.min ?: DEFAULT_EQ_MIN_MB,
            eqMaxMb = eq?.max ?: DEFAULT_EQ_MAX_MB,
        ),
        news = NewsSettings(
            enabled = newsEnabled ?: false,
            time = newsTime ?: DEFAULT_NEWS_TIME,
            voice = newsVoice.orEmpty(),
            categories = newsCategories.orEmpty()
                .filter { !it.key.isNullOrBlank() }
                .map { NewsCategory(it.key.orEmpty(), it.enabled ?: true) },
        ),
        ttsHost = ttsHost.orEmpty(),
    )
}

private const val DEFAULT_EQ_MIN_MB = -1500
private const val DEFAULT_EQ_MAX_MB = 1500
private const val DEFAULT_NEWS_TIME = "07:00"

fun TrackDto.toDomain(): Track = Track(
    id = videoId.orEmpty(),
    title = title.orEmpty(),
    artist = artist.orEmpty(),
    duration = duration.orEmpty(),
    thumbnailUrl = thumbnail.orEmpty(),
)

/** A row with no id cannot be played or told apart from another, so it is dropped. */
fun List<TrackDto>?.toTracks(): List<Track> =
    orEmpty().map { it.toDomain() }.filter { it.id.isNotEmpty() }

fun MediaStateDto.toDomain(): MediaState = MediaState(
    status = when (state) {
        "idle" -> MediaStatus.Idle
        "downloading" -> MediaStatus.Downloading
        "playing" -> MediaStatus.Playing
        "paused" -> MediaStatus.Paused
        "stopped" -> MediaStatus.Stopped
        else -> MediaStatus.Unknown
    },
    // A stopped player reports the literal string "null" as its video id.
    videoId = videoId.orEmpty().takeUnless { it == "null" }.orEmpty(),
    title = title.orEmpty(),
    artist = artist.orEmpty(),
    coverUrl = coverUrl.orEmpty(),
    durationS = durationS ?: 0,
    positionS = positionS ?: 0,
    downloadPercent = downloadPercent ?: -1,
    queue = queue.toTracks(),
)

fun BtStateDto.toDomain(): BluetoothState = BluetoothState(
    supported = supported ?: false,
    enabled = enabled ?: false,
    scanning = scanning ?: false,
    autoReconnect = autoReconnect ?: false,
    adapterName = adapterName.orEmpty(),
    receiveSecondsLeft = (receiveSecondsLeft ?: 0).coerceAtLeast(0),
    incomingAddress = incoming.orEmpty(),
    connectedAddress = connected.orEmpty(),
    busy = busy.orEmpty(),
    error = error.orEmpty(),
    devices = devices.orEmpty().filter { !it.address.isNullOrEmpty() }.map {
        val address = it.address.orEmpty()
        BluetoothDevice(
            address = address,
            name = it.name.orEmpty().ifBlank { address },
            bonded = it.bonded ?: false,
            audio = it.audio ?: false,
            connected = it.connected ?: false,
            streamingIn = it.streamingIn ?: false,
            phone = it.phone ?: false,
        )
    },
)

fun VoicesDto.toDomain(): TtsVoices = TtsVoices(current = voice.orEmpty(), available = voices.orEmpty())
