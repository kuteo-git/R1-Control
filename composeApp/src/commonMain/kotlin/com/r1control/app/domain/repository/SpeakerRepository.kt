package com.r1control.app.domain.repository

import com.r1control.app.domain.model.AudioSettings
import com.r1control.app.domain.model.BluetoothState
import com.r1control.app.domain.model.NewsSettings
import com.r1control.app.domain.model.R1Address
import com.r1control.app.domain.model.SpeakerSettings
import com.r1control.app.domain.model.SpeakerSnapshot
import com.r1control.app.domain.model.Track

/**
 * The R1, as the screens need it. Every call throws a [SpeakerException] and
 * nothing else (cancellation excepted).
 */
interface SpeakerRepository {
    /** The three polled states, read together. */
    suspend fun snapshot(): SpeakerSnapshot

    /** `/api/state`'s settings part, for the settings screens. */
    suspend fun settings(): SpeakerSettings

    /** Reads `/api/state` at [address], which need not be the saved one. Throws if it does not answer like an R1. */
    suspend fun probe(address: R1Address)

    suspend fun setVolume(pct: Int)

    suspend fun pause()
    suspend fun resume()
    suspend fun next()
    suspend fun stop()
    suspend fun seek(positionS: Int)

    suspend fun play(tracks: List<Track>, startIndex: Int)

    /**
     * Replaces what the speaker plays after the current song with [tracks], without cutting the
     * current song off; it takes them when this song ends. An R1 built before the call answers
     * 404 ([SpeakerException.Refused]).
     */
    suspend fun queue(tracks: List<Track>)
    suspend fun search(keyword: String): List<Track>
    suspend fun say(text: String)

    suspend fun setLoudness(mb: Int)
    suspend fun setEqEnabled(enabled: Boolean)
    /** All bands' gains, in band order. */
    suspend fun setEqBands(gainsMb: List<Int>)

    suspend fun saveNews(news: NewsSettings)
    suspend fun testNews()

    suspend fun bluetooth(): BluetoothState
    suspend fun setBluetoothEnabled(enabled: Boolean)
    suspend fun startBluetoothScan()
    suspend fun stopBluetoothScan()
    suspend fun pair(address: String)
    suspend fun connect(address: String)
    suspend fun disconnect(address: String)
    suspend fun forget(address: String)
    suspend fun setAutoReconnect(enabled: Boolean)
    /** Opens the R1 to be paired by a phone for a couple of minutes. */
    suspend fun receiveFromPhone()

    suspend fun restart()

    /** `POST /api/voice/sleep`: ends the voice session, the hardware button's path. It stops music too, so callers gate on `canEndSession`. */
    suspend fun endVoiceSession()

    /** `POST /api/wake/enable?on=1|0`. */
    suspend fun setWakeWord(enabled: Boolean)
}
