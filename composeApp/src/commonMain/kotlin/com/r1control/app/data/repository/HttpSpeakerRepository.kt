package com.r1control.app.data.repository

import com.r1control.app.data.remote.R1Http
import com.r1control.app.data.remote.R1Json
import com.r1control.app.data.remote.dto.AckDto
import com.r1control.app.data.remote.dto.BtStateDto
import com.r1control.app.data.remote.dto.MediaStateDto
import com.r1control.app.data.remote.dto.NewsCategorySaveDto
import com.r1control.app.data.remote.dto.NewsSaveDto
import com.r1control.app.data.remote.dto.PlayBodyDto
import com.r1control.app.data.remote.dto.PlayItemDto
import com.r1control.app.data.remote.dto.StateDto
import com.r1control.app.data.remote.toChat
import com.r1control.app.data.remote.toDomain
import com.r1control.app.data.remote.toSettings
import com.r1control.app.data.remote.toTracks
import com.r1control.app.data.remote.toVoiceActivity
import com.r1control.app.data.remote.toVoiceControls
import com.r1control.app.data.remote.toVolume
import com.r1control.app.domain.model.BluetoothState
import com.r1control.app.domain.model.NewsSettings
import com.r1control.app.domain.model.R1Address
import com.r1control.app.domain.model.SavedAddress
import com.r1control.app.domain.model.SpeakerSettings
import com.r1control.app.domain.model.SpeakerSnapshot
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.repository.PreferencesRepository
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.repository.SpeakerRepository
import io.ktor.client.HttpClient
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.path
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString

/**
 * The R1 over HTTP. The address is read from [preferences] on every call, so a
 * newly saved address takes effect on the next request with nothing to rebuild.
 */
class HttpSpeakerRepository(
    client: HttpClient,
    private val preferences: PreferencesRepository,
) : SpeakerRepository {

    private val http = R1Http(client)

    override suspend fun snapshot(): SpeakerSnapshot {
        val address = address()
        return coroutineScope {
            val state = async { read<StateDto>(address, "state", "/api/state") }
            val media = async { read<MediaStateDto>(address, "media state", "/api/media/state") }
            val bluetooth = async { read<BtStateDto>(address, "bluetooth state", "/api/bt/state") }
            val s = state.await()
            SpeakerSnapshot(
                volume = s.toVolume(),
                voice = s.toVoiceActivity(),
                voiceAwake = s.voiceAwake ?: false,
                chat = s.toChat(),
                media = media.await().toDomain(),
                bluetooth = bluetooth.await().toDomain(),
                voiceControls = s.toVoiceControls(),
            )
        }
    }

    override suspend fun settings(): SpeakerSettings =
        read<StateDto>(address(), "state", "/api/state").toSettings()

    override suspend fun probe(address: R1Address) {
        read<StateDto>(address, "state", "/api/state")
    }

    override suspend fun setVolume(pct: Int) = write("set", "key" to "volume", "value" to pct.toString(), path = "/api/set")

    override suspend fun pause() = write("pause", path = "/api/media/pause")
    override suspend fun resume() = write("resume", path = "/api/media/resume")
    override suspend fun next() = write("next", path = "/api/media/next")
    override suspend fun stop() = write("stop", path = "/api/media/stop")
    override suspend fun seek(positionS: Int) =
        write("seek", "position_s" to positionS.coerceAtLeast(0).toString(), path = "/api/media/seek")

    override suspend fun play(tracks: List<Track>, startIndex: Int) {
        val body = R1Json.encodeToString(
            PlayBodyDto(
                items = tracks.map { PlayItemDto(it.id, it.title, it.artist, it.duration, it.thumbnailUrl) },
                startIndex = startIndex,
            ),
        )
        write("play", path = "/api/media/play", body = body)
    }

    override suspend fun search(keyword: String): List<Track> {
        val ack = ack(address(), "search", "/api/media/search", listOf("q" to keyword))
        return ack.results.toTracks()
    }

    override suspend fun say(text: String) = write("say", "text" to text, path = "/api/say")

    override suspend fun setLoudness(mb: Int) = write("set", "key" to "loudness_mb", "value" to mb.toString(), path = "/api/set")
    override suspend fun setEqEnabled(enabled: Boolean) = write("set", "key" to "eq_enabled", "value" to enabled.toString(), path = "/api/set")
    override suspend fun setEqBands(gainsMb: List<Int>) =
        write("set", "key" to "eq_bands", "value" to gainsMb.joinToString(","), path = "/api/set")

    override suspend fun saveNews(news: NewsSettings) {
        val body = R1Json.encodeToString(
            NewsSaveDto(
                enabled = news.enabled,
                time = news.time,
                voice = news.voice,
                categories = news.categories.map { NewsCategorySaveDto(it.key, it.enabled) },
            ),
        )
        write("news save", path = "/api/news/save", body = body)
    }

    override suspend fun testNews() = write("news test", path = "/api/news/test")

    override suspend fun bluetooth(): BluetoothState =
        read<BtStateDto>(address(), "bluetooth state", "/api/bt/state").toDomain()

    override suspend fun setBluetoothEnabled(enabled: Boolean) =
        write("bluetooth enable", "on" to flag(enabled), path = "/api/bt/enable")

    override suspend fun startBluetoothScan() = write("bluetooth scan", path = "/api/bt/scan/start")
    override suspend fun stopBluetoothScan() = write("bluetooth scan", path = "/api/bt/scan/stop")
    override suspend fun pair(address: String) = write("pair", "addr" to address, path = "/api/bt/pair")
    override suspend fun connect(address: String) = write("connect", "addr" to address, path = "/api/bt/connect")
    override suspend fun disconnect(address: String) = write("disconnect", "addr" to address, path = "/api/bt/disconnect")
    override suspend fun forget(address: String) = write("forget", "addr" to address, path = "/api/bt/forget")
    override suspend fun setAutoReconnect(enabled: Boolean) =
        write("bluetooth auto", "on" to flag(enabled), path = "/api/bt/auto")
    override suspend fun receiveFromPhone() = write("bluetooth receive", path = "/api/bt/receive")

    override suspend fun restart() = write("restart", path = "/api/restart")

    override suspend fun endVoiceSession() = write("end session", path = "/api/voice/sleep")

    // The R1 reads anything but "1" as off, so the flag is always sent and pinned by a test.
    override suspend fun setWakeWord(enabled: Boolean) =
        write("wake word", "on" to flag(enabled), path = "/api/wake/enable")

    private fun flag(on: Boolean) = if (on) "1" else "0"

    private suspend fun address(): R1Address = when (val saved = preferences.address()) {
        SavedAddress.Unset -> throw SpeakerException.NotConfigured()
        is SavedAddress.Set -> saved.address
    }

    private fun urlOf(address: R1Address, path: String, params: List<Pair<String, String>>): URLBuilder.() -> Unit = {
        protocol = URLProtocol.HTTP
        host = address.host
        port = address.port
        path(*path.trim('/').split('/').toTypedArray())
        params.forEach { (k, v) -> parameters.append(k, v) }
    }

    private suspend inline fun <reified T> read(address: R1Address, what: String, path: String): T {
        val text = http.call(HttpMethod.Get, urlOf(address, path, emptyList()))
        return decode(text, what)
    }

    private suspend fun ack(
        address: R1Address,
        what: String,
        path: String,
        params: List<Pair<String, String>>,
        body: String? = null,
    ): AckDto {
        val text = http.call(HttpMethod.Post, urlOf(address, path, params), body)
        val ack = decode<AckDto>(text, what)
        if (ack.ok == false) throw SpeakerException.Refused(ack.error.orEmpty())
        return ack
    }

    private suspend fun write(
        what: String,
        vararg params: Pair<String, String>,
        path: String,
        body: String? = null,
    ) {
        ack(address(), what, path, params.toList(), body)
    }

    private inline fun <reified T> decode(text: String, what: String): T = try {
        R1Json.decodeFromString<T>(text)
    } catch (_: SerializationException) {
        throw SpeakerException.BadReply(what)
    } catch (_: IllegalArgumentException) {
        throw SpeakerException.BadReply(what)
    }
}
