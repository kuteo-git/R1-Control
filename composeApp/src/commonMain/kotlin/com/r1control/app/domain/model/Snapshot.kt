package com.r1control.app.domain.model

/**
 * One reading of the speaker, assembled from `/api/state`, `/api/media/state`
 * and `/api/bt/state`, with only the fields the screens read.
 */
data class SpeakerSnapshot(
    val volume: Volume,
    val voice: VoiceActivity,
    val voiceAwake: Boolean,
    /** Oldest first, newest last. */
    val chat: List<ChatMessage>,
    val media: MediaState,
    val bluetooth: BluetoothState,
    val voiceControls: VoiceControls,
    /** From the `/api/state` body the rest of the reading already came from: Settings draws from the feed, with no request of its own. */
    val settings: SpeakerSettings,
) {
    val nowPlaying: NowPlaying get() = nowPlayingOf(this)
}
