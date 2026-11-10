package com.r1control.app.domain.usecase

import com.r1control.app.domain.model.TtsVoices
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.repository.VoiceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The one owner of what the voice server last said, per host. Every read
 * refreshes it and a [setVoice] updates it, so a Settings page opens with the
 * voice already known and refreshes behind the first frame.
 */
class VoiceCache(private val inner: VoiceRepository) : VoiceRepository {
    private val last = MutableStateFlow<Map<String, TtsVoices>>(emptyMap())
    private val asked = MutableStateFlow<Set<String>>(emptySet())

    override suspend fun voices(ttsHost: String): TtsVoices =
        inner.voices(ttsHost).also { read -> last.update { it + (ttsHost to read) } }

    override suspend fun setVoice(ttsHost: String, name: String) {
        inner.setVoice(ttsHost, name)
        last.update { held -> held[ttsHost]?.let { held + (ttsHost to it.copy(current = name)) } ?: held }
    }

    override fun remembered(ttsHost: String): TtsVoices = last.value[ttsHost] ?: TtsVoices.None

    /** Reads [ttsHost] once in the background, the first time it is named; a failed read may be tried again. */
    fun warm(ttsHost: String, scope: CoroutineScope) {
        if (ttsHost.isEmpty()) return
        var first = false
        asked.update { held -> (held + ttsHost).also { first = ttsHost !in held } }
        if (!first) return
        scope.launch {
            try {
                voices(ttsHost)
            } catch (e: CancellationException) {
                throw e
            } catch (_: SpeakerException) {
                asked.update { it - ttsHost }
            }
        }
    }
}
