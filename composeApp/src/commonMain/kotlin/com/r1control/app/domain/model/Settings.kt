package com.r1control.app.domain.model

/** One equaliser band: its centre frequency and gain, both as the R1 counts them. */
data class EqBand(val freqHz: Int, val gainMb: Int)

/** Loudness and equaliser. Gains and loudness are millibels; /100 is dB. */
data class AudioSettings(
    val loudnessMb: Int,
    val eqEnabled: Boolean,
    val bands: List<EqBand>,
    val eqMinMb: Int,
    val eqMaxMb: Int,
) {
    companion object {
        const val LOUDNESS_MIN_MB = 0
        const val LOUDNESS_MAX_MB = 2000
        const val LOUDNESS_STEP_MB = 100
    }
}

/** A bulletin topic. [key] is the server's (tech, world, society, weather, power) and may be one this app has no label for. */
data class NewsCategory(val key: String, val enabled: Boolean)

/** The daily bulletin. [categories] order is playback order. [time] is `HH:mm`. */
data class NewsSettings(
    val enabled: Boolean,
    val time: String,
    val voice: String,
    val categories: List<NewsCategory>,
)

/** What `/api/state` holds for the settings screens. */
data class SpeakerSettings(
    val audio: AudioSettings,
    val news: NewsSettings,
    /** Base URL of the TTS server, empty when the R1 did not say. */
    val ttsHost: String,
) {
    val hasTtsHost: Boolean get() = ttsHost.isNotEmpty()
}

data class TtsVoices(val current: String, val available: List<String>) {
    /** A list that was really read: the voice server always names some voices. */
    val isKnown: Boolean get() = available.isNotEmpty()

    companion object {
        val None = TtsVoices("", emptyList())
    }
}
