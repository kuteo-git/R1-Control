package com.r1control.app.domain.repository

import com.r1control.app.domain.model.TtsVoices

/** The text-to-speech server, which the R1 names in `tts_host` and which lives on another host. */
interface VoiceRepository {
    suspend fun voices(ttsHost: String): TtsVoices
    suspend fun setVoice(ttsHost: String, name: String)
}
