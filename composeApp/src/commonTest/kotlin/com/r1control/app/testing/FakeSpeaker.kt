package com.r1control.app.testing

import com.r1control.app.domain.model.AudioSettings
import com.r1control.app.domain.model.BluetoothState
import com.r1control.app.domain.model.NewsSettings
import com.r1control.app.domain.model.R1Address
import com.r1control.app.domain.model.SpeakerSettings
import com.r1control.app.domain.model.SpeakerSnapshot
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.model.TtsVoices
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.repository.VoiceRepository

val unreachable = SpeakerException.Unreachable(RuntimeException("down"))

/**
 * A speaker whose answers a test sets and whose calls it reads back.
 * [pollFailure] makes reads fail, [commandFailure] makes every command fail.
 */
open class FakeSpeaker : StubSpeaker() {
    var current: SpeakerSnapshot = speakerSnapshot()
    var pollFailure: SpeakerException? = null
    var commandFailure: SpeakerException? = null
    var settings: SpeakerSettings? = null
    var bluetoothState: BluetoothState = com.r1control.app.testing.bluetooth()
    var searchAnswers: (String) -> List<Track> = { emptyList() }
    var searchDelayMillis: (String) -> Long = { 0L }
    val calls = mutableListOf<String>()
    val volumes = mutableListOf<Int>()
    var snapshots = 0
    var bluetoothReads = 0

    private fun command(name: String) {
        calls += name
        commandFailure?.let { throw it }
    }

    override suspend fun snapshot(): SpeakerSnapshot {
        snapshots++
        pollFailure?.let { throw it }
        return current
    }

    override suspend fun settings(): SpeakerSettings {
        pollFailure?.let { throw it }
        return checkNotNull(settings) { "no settings set" }
    }

    override suspend fun probe(address: R1Address) {
        calls += "probe $address"
        commandFailure?.let { throw it }
    }

    override suspend fun setVolume(pct: Int) {
        volumes += pct
        commandFailure?.let { throw it }
    }

    override suspend fun pause() = command("pause")
    override suspend fun resume() = command("resume")
    override suspend fun next() = command("next")
    override suspend fun stop() = command("stop")
    override suspend fun seek(positionS: Int) = command("seek $positionS")
    override suspend fun play(tracks: List<Track>, startIndex: Int) = command("play ${tracks.map { it.id }} from $startIndex")
    override suspend fun queue(tracks: List<Track>) = command("queue ${tracks.map { it.id }}")
    override suspend fun say(text: String) = command("say $text")
    override suspend fun setLoudness(mb: Int) = command("loudness $mb")
    override suspend fun setEqEnabled(enabled: Boolean) = command("eq $enabled")
    override suspend fun setEqBands(gainsMb: List<Int>) = command("bands $gainsMb")
    override suspend fun saveNews(news: NewsSettings) = command("saveNews $news")
    override suspend fun testNews() = command("testNews")
    override suspend fun restart() = command("restart")
    override suspend fun endVoiceSession() = command("end session")
    override suspend fun setWakeWord(enabled: Boolean) = command("wake word ${if (enabled) "on" else "off"}")

    override suspend fun search(keyword: String): List<Track> {
        kotlinx.coroutines.delay(searchDelayMillis(keyword))
        calls += "search $keyword"
        pollFailure?.let { throw it }
        return searchAnswers(keyword)
    }

    override suspend fun bluetooth(): BluetoothState {
        bluetoothReads++
        pollFailure?.let { throw it }
        return bluetoothState
    }

    override suspend fun setBluetoothEnabled(enabled: Boolean) = command("btEnabled $enabled")
    override suspend fun startBluetoothScan() = command("btScan")
    override suspend fun stopBluetoothScan() = command("btScanStop")
    override suspend fun pair(address: String) = command("pair $address")
    override suspend fun connect(address: String) = command("connect $address")
    override suspend fun disconnect(address: String) = command("disconnect $address")
    override suspend fun forget(address: String) = command("forget $address")
    override suspend fun setAutoReconnect(enabled: Boolean) = command("btAuto $enabled")
    override suspend fun receiveFromPhone() = command("btReceive")
}

class FakeVoices(
    var voices: TtsVoices = TtsVoices("a", listOf("a", "b")),
    var failure: SpeakerException? = null,
) : VoiceRepository {
    val chosen = mutableListOf<String>()
    var askedHost = ""

    override suspend fun voices(ttsHost: String): TtsVoices {
        askedHost = ttsHost
        failure?.let { throw it }
        return voices
    }

    override suspend fun setVoice(ttsHost: String, name: String) {
        failure?.let { throw it }
        chosen += name
    }
}

fun audioSettings(loudness: Int = 1000, eq: Boolean = true) = AudioSettings(
    loudnessMb = loudness, eqEnabled = eq,
    bands = listOf(com.r1control.app.domain.model.EqBand(60, 0), com.r1control.app.domain.model.EqBand(230, 100)),
    eqMinMb = -1500, eqMaxMb = 1500,
)

fun speakerSettings(
    audio: AudioSettings = audioSettings(),
    news: NewsSettings = NewsSettings(
        true, "09:15", "b",
        listOf(
            com.r1control.app.domain.model.NewsCategory("tech", true),
            com.r1control.app.domain.model.NewsCategory("world", true),
            com.r1control.app.domain.model.NewsCategory("power", false),
        ),
    ),
    ttsHost: String = "http://tts:8002",
) = SpeakerSettings(audio, news, ttsHost)
