package com.r1control.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire shapes of the R1's control server. Every field is optional because the
// R1 may leave one out; the mappers decide what absence means.

@Serializable
data class StateDto(
    val volume: Int? = null,
    @SerialName("volume_steps") val volumeSteps: Int? = null,
    @SerialName("eq_enabled") val eqEnabled: Boolean? = null,
    @SerialName("eq_bands") val eqBands: List<Int>? = null,
    val eq: EqDto? = null,
    @SerialName("loudness_mb") val loudnessMb: Int? = null,
    @SerialName("voice_state") val voiceState: String? = null,
    @SerialName("voice_awake") val voiceAwake: Boolean? = null,
    val chat: List<ChatDto>? = null,
    @SerialName("news_enabled") val newsEnabled: Boolean? = null,
    @SerialName("news_time") val newsTime: String? = null,
    @SerialName("news_voice") val newsVoice: String? = null,
    @SerialName("news_categories") val newsCategories: List<NewsCategoryDto>? = null,
    @SerialName("tts_host") val ttsHost: String? = null,
    /** Absent on an R1 build before the listening controls (round 3); see [toVoiceControls]. */
    @SerialName("wake_enabled") val wakeEnabled: Boolean? = null,
)

@Serializable
data class EqDto(
    val freqs: List<Int>? = null,
    val min: Int? = null,
    val max: Int? = null,
)

@Serializable
data class ChatDto(
    val sender: String? = null,
    val text: String? = null,
    val time: Long? = null,
)

@Serializable
data class NewsCategoryDto(
    val key: String? = null,
    val enabled: Boolean? = null,
)

@Serializable
data class TrackDto(
    @SerialName("video_id") val videoId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val duration: String? = null,
    val thumbnail: String? = null,
)

@Serializable
data class MediaStateDto(
    val state: String? = null,
    @SerialName("video_id") val videoId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("duration_s") val durationS: Int? = null,
    @SerialName("position_s") val positionS: Int? = null,
    @SerialName("download_percent") val downloadPercent: Int? = null,
    val queue: List<TrackDto>? = null,
)

@Serializable
data class BtDeviceDto(
    val address: String? = null,
    val name: String? = null,
    val bonded: Boolean? = null,
    val audio: Boolean? = null,
    val connected: Boolean? = null,
    @SerialName("streaming_in") val streamingIn: Boolean? = null,
    val phone: Boolean? = null,
)

@Serializable
data class BtStateDto(
    val supported: Boolean? = null,
    val enabled: Boolean? = null,
    val scanning: Boolean? = null,
    @SerialName("auto_reconnect") val autoReconnect: Boolean? = null,
    val connected: String? = null,
    @SerialName("adapter_name") val adapterName: String? = null,
    @SerialName("receive_seconds_left") val receiveSecondsLeft: Int? = null,
    val incoming: String? = null,
    val busy: String? = null,
    val error: String? = null,
    val devices: List<BtDeviceDto>? = null,
)

/** The reply to every write, and to search: `{ok}` or `{ok:false,error}`. */
@Serializable
data class AckDto(
    val ok: Boolean? = null,
    val error: String? = null,
    val results: List<TrackDto>? = null,
)

@Serializable
data class VoicesDto(
    val voice: String? = null,
    val voices: List<String>? = null,
)

@Serializable
data class PlayBodyDto(
    val items: List<PlayItemDto>,
    @SerialName("start_index") val startIndex: Int,
)

/** What `/api/media/play` reads back: all five keys, empty rather than absent. */
@Serializable
data class PlayItemDto(
    @SerialName("video_id") val videoId: String,
    val title: String,
    val artist: String,
    val duration: String,
    val thumbnail: String,
)

@Serializable
data class NewsSaveDto(
    val enabled: Boolean,
    val time: String,
    val voice: String,
    val categories: List<NewsCategorySaveDto>,
)

@Serializable
data class NewsCategorySaveDto(val key: String, val enabled: Boolean)
