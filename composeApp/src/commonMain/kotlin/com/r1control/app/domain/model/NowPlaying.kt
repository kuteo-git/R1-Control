package com.r1control.app.domain.model

/** What the media card shows. */
sealed interface NowPlaying {
    data object Nothing : NowPlaying

    /** A phone is streaming into the R1. It cannot be seeked or skipped from here. */
    data class BluetoothIn(val deviceName: String) : NowPlaying

    data class Music(
        val title: String,
        val artist: String,
        val coverUrl: String,
        val positionS: Int,
        val durationS: Int,
        val phase: Phase,
        val output: Output,
    ) : NowPlaying {
        val hasCover: Boolean get() = coverUrl.isNotEmpty()
        val hasArtist: Boolean get() = artist.isNotEmpty()
        val canSeek: Boolean get() = phase !is Phase.Downloading && durationS > 0
    }

    data class Assistant(val activity: Activity, val lastLine: String) : NowPlaying {
        val hasLastLine: Boolean get() = lastLine.isNotEmpty()
    }

    sealed interface Phase {
        data object Playing : Phase
        data object Paused : Phase
        data class Downloading(val percent: Int) : Phase
    }

    sealed interface Output {
        data object ThisSpeaker : Output
        data class Bluetooth(val speakerName: String) : Output
    }

    enum class Activity { Listening, Speaking }
}

/**
 * The one decision the Control tab's card is made of, by priority:
 *
 * 1. a phone streaming in over Bluetooth,
 * 2. music (`playing`, `paused`, `downloading`),
 * 3. the assistant, when it is awake and listening or speaking,
 * 4. nothing.
 *
 * `stopped` and `idle` are nothing even with a queue kept: the queue is what a
 * stop leaves behind, not something playing. Music ranks above the assistant
 * because the spec lists it so; the R1 does not report whether it ducked the
 * music for the assistant.
 *
 * Listening beside a `stopped` player is nothing too. A stop, or the end of a
 * queue, reopens the R1's microphone inside the same session, so it reports
 * listening although nobody spoke. A session that times out clears the player
 * to `idle`, so a later wake word shows as listening. One that the R1's button
 * ended keeps `stopped`, and its next wake shows nothing until the answer,
 * which shows as speaking (read from the R1's `VoiceAssistant`, 2026-11-08).
 */
fun nowPlayingOf(snapshot: SpeakerSnapshot): NowPlaying {
    val bluetooth = snapshot.bluetooth
    if (bluetooth.hasIncoming) return NowPlaying.BluetoothIn(bluetooth.incomingName)

    val media = snapshot.media
    val phase = when (media.status) {
        MediaStatus.Playing -> NowPlaying.Phase.Playing
        MediaStatus.Paused -> NowPlaying.Phase.Paused
        MediaStatus.Downloading ->
            NowPlaying.Phase.Downloading(media.downloadPercent.coerceIn(0, 100))
        MediaStatus.Idle, MediaStatus.Stopped, MediaStatus.Unknown -> null
    }
    if (phase != null) {
        return NowPlaying.Music(
            title = media.title,
            artist = media.artist,
            coverUrl = media.coverUrl,
            positionS = if (media.durationS > 0) media.positionS.coerceIn(0, media.durationS) else media.positionS.coerceAtLeast(0),
            durationS = media.durationS.coerceAtLeast(0),
            phase = phase,
            output = if (bluetooth.hasOutput) {
                NowPlaying.Output.Bluetooth(bluetooth.outputName)
            } else {
                NowPlaying.Output.ThisSpeaker
            },
        )
    }

    if (snapshot.voiceAwake) {
        val activity = when (snapshot.voice) {
            VoiceActivity.Listening ->
                if (media.status == MediaStatus.Stopped) null else NowPlaying.Activity.Listening
            VoiceActivity.Speaking -> NowPlaying.Activity.Speaking
            VoiceActivity.Idle -> null
        }
        if (activity != null) {
            return NowPlaying.Assistant(activity, snapshot.chat.lastOrNull()?.text.orEmpty())
        }
    }
    return NowPlaying.Nothing
}

/**
 * Whether "end the conversation" (`POST /api/voice/sleep`) may be offered.
 *
 * The R1's sleep path flushes playback and clears its music flag, and during a
 * song the R1 reports `SPEAKING`, awake, for the whole song. So raw
 * `voiceAwake` is not safe:
 * - the assistant card ([NowPlaying.Assistant], which already puts music first)
 *   is a session;
 * - listening beside a `stopped` player (the card's [NowPlaying.Nothing], see
 *   [nowPlayingOf]) is a session the R1 really holds open;
 * - music of any phase (playing, paused, downloading) and Bluetooth in are not.
 *
 * Says nothing about whether the R1 has the route; [VoiceControls] does.
 */
fun canEndSession(snapshot: SpeakerSnapshot): Boolean = when (snapshot.nowPlaying) {
    is NowPlaying.Assistant -> true
    NowPlaying.Nothing ->
        snapshot.voiceAwake && snapshot.voice == VoiceActivity.Listening && snapshot.media.status == MediaStatus.Stopped
    is NowPlaying.Music, is NowPlaying.BluetoothIn -> false
}
