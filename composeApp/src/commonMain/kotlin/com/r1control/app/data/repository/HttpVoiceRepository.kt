package com.r1control.app.data.repository

import com.r1control.app.data.remote.R1Http
import com.r1control.app.data.remote.R1Json
import com.r1control.app.data.remote.dto.VoicesDto
import com.r1control.app.data.remote.toDomain
import com.r1control.app.domain.model.TtsVoices
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.repository.VoiceRepository
import io.ktor.client.HttpClient
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/** The TTS server's `/voice`, at the host the R1 names in `tts_host`. */
class HttpVoiceRepository(client: HttpClient) : VoiceRepository {

    private val http = R1Http(client)

    override suspend fun voices(ttsHost: String): TtsVoices {
        val text = http.call(HttpMethod.Get, voiceUrl(ttsHost))
        return try {
            R1Json.decodeFromString<VoicesDto>(text).toDomain()
        } catch (_: SerializationException) {
            throw SpeakerException.BadReply("voices")
        } catch (_: IllegalArgumentException) {
            throw SpeakerException.BadReply("voices")
        }
    }

    override suspend fun setVoice(ttsHost: String, name: String) {
        http.call(HttpMethod.Post, voiceUrl(ttsHost) { parameters.append("name", name) })
    }

    /**
     * Parsed here, before the request: `tts_host` is the R1's answer, so an unusable
     * one is a bad reply. Inside the request any throw reads as "unreachable".
     */
    private fun voiceUrl(ttsHost: String, extra: URLBuilder.() -> Unit = {}): URLBuilder.() -> Unit {
        if (ttsHost.isBlank()) throw SpeakerException.BadReply("tts host")
        val base = try {
            URLBuilder().takeFrom(ttsHost).appendPathSegments("voice").build()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw SpeakerException.BadReply("tts host")
        }
        return {
            takeFrom(base)
            extra()
        }
    }
}
