package com.r1control.app.domain.repository

import com.r1control.app.domain.model.TtsVoices

/** The text-to-speech server, which the R1 names in `tts_host` and which lives on another host. */
interface VoiceRepository {
    suspend fun voices(ttsHost: String): TtsVoices
    suspend fun setVoice(ttsHost: String, name: String)

    /** The last list read for [ttsHost], without a request; [TtsVoices.None] when this repository keeps none. */
    fun remembered(ttsHost: String): TtsVoices = TtsVoices.None
}
