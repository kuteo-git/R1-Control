package com.r1control.app.data

import com.r1control.app.data.remote.dto.AckDto
import com.r1control.app.data.remote.dto.BtStateDto
import com.r1control.app.data.remote.dto.MediaStateDto
import com.r1control.app.data.remote.dto.StateDto
import com.r1control.app.data.remote.dto.VoicesDto
import com.r1control.app.data.remote.R1Json
import com.r1control.app.data.remote.toChat
import com.r1control.app.data.remote.toDomain
import com.r1control.app.data.remote.toSettings
import com.r1control.app.data.remote.toTracks
import com.r1control.app.data.remote.toVoiceActivity
import com.r1control.app.data.remote.toVoiceControls
import com.r1control.app.data.remote.toVolume
import com.r1control.app.domain.model.ChatMessage
import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.NowPlaying
import com.r1control.app.domain.model.Sender
import com.r1control.app.domain.model.SpeakerSnapshot
import com.r1control.app.domain.model.VoiceActivity
import com.r1control.app.domain.model.VoiceControls
import com.r1control.app.domain.model.VolumeScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MappingTest {
    private inline fun <reified T> parse(name: String): T = R1Json.decodeFromString(fixture(name))

    private fun snapshotOf(state: String, media: String, bt: String): SpeakerSnapshot {
        val s = R1Json.decodeFromString<StateDto>(fixture(state))
        return SpeakerSnapshot(
            volume = s.toVolume(),
            voice = s.toVoiceActivity(),
            voiceAwake = s.voiceAwake ?: false,
            chat = s.toChat(),
            media = parse<MediaStateDto>(media).toDomain(),
            bluetooth = parse<BtStateDto>(bt).toDomain(),
            voiceControls = s.toVoiceControls(),
        )
    }

    @Test fun idleState() {
        val s = parse<StateDto>("state_idle.json")
        val volume = s.toVolume()
        assertEquals(100, volume.pct)
        assertEquals(VolumeScale(15), volume.scale)
        assertEquals(VoiceActivity.Idle, s.toVoiceActivity())
        assertTrue(s.toChat().isEmpty())
    }

    @Test fun settingsPart() {
        val settings = parse<StateDto>("state_idle.json").toSettings()
        val audio = settings.audio
        assertEquals(1000, audio.loudnessMb)
        assertTrue(audio.eqEnabled)
        assertEquals(listOf(60, 230, 910, 3600, 14000), audio.bands.map { it.freqHz })
        assertEquals(listOf(1000, 0, -600, 0, 800), audio.bands.map { it.gainMb })
        assertEquals(-1500, audio.eqMinMb)
        assertEquals(1500, audio.eqMaxMb)
        val news = settings.news
        assertFalse(news.enabled)
        assertEquals("09:15", news.time)
        assertEquals("Thiền Tâm Đức", news.voice)
        assertEquals(listOf("tech", "world", "society", "weather", "power"), news.categories.map { it.key })
        assertEquals(listOf(true, true, false, false, true), news.categories.map { it.enabled })
        assertEquals("http://10.25.113.151:8002", settings.ttsHost)
        assertTrue(settings.hasTtsHost)
    }

    @Test fun mediaPlaying() {
        val m = parse<MediaStateDto>("media_playing.json").toDomain()
        assertEquals(MediaStatus.Playing, m.status)
        assertEquals("qkXf2c5mlA8", m.videoId)
        assertEquals("Thành Tâm Sám Hối", m.title)
        assertEquals("NSƯT Thanh Ngân", m.artist)
        assertEquals(604, m.durationS)
        assertEquals(15, m.positionS)
        assertEquals(3, m.queue.size)
        assertEquals("10:05", m.queue.first().duration)
        assertTrue(m.queue.first().thumbnailUrl.startsWith("https://yt3.googleusercontent.com/"))
        assertTrue(m.hasNext)
    }

    @Test fun mediaPausedKeepsItsPosition() {
        val m = parse<MediaStateDto>("media_paused.json").toDomain()
        assertEquals(MediaStatus.Paused, m.status)
        assertEquals(6, m.positionS)
    }

    @Test fun mediaIdleOmitsTheVideoId() {
        val m = parse<MediaStateDto>("media_idle.json").toDomain()
        assertEquals(MediaStatus.Idle, m.status)
        assertEquals("", m.videoId)
        assertTrue(m.queue.isEmpty())
    }

    @Test fun stoppedReportsTheStringNullAsItsVideoIdAndKeepsTheQueue() {
        val m = parse<MediaStateDto>("media_stopped.json").toDomain()
        assertEquals(MediaStatus.Stopped, m.status)
        assertEquals("", m.videoId)
        assertEquals(3, m.queue.size)
        assertFalse(m.hasNext)
    }

    @Test fun searchResults() {
        val ack = parse<AckDto>("search_suong_trang.json")
        assertEquals(true, ack.ok)
        val tracks = ack.results.toTracks()
        assertEquals("ml8uV4oD1u8", tracks.first().id)
        assertEquals("Sương Trắng Miền Quê Ngoại", tracks.first().title)
        assertEquals("Quang Lê", tracks.first().artist)
        assertEquals("4:57", tracks.first().duration)
        assertTrue(tracks.size > 1)
        assertTrue(tracks.all { it.id.isNotEmpty() })
    }

    @Test fun bluetoothIdle() {
        val bt = parse<BtStateDto>("bt_idle.json").toDomain()
        assertTrue(bt.supported)
        assertTrue(bt.enabled)
        assertTrue(bt.autoReconnect)
        assertFalse(bt.hasIncoming)
        assertFalse(bt.hasOutput)
        assertEquals("Phicomm_R1_2E2B", bt.adapterName)
        assertEquals(listOf("Kitchen speaker", "UGREEN-BT305"), bt.devices.map { it.name })
        assertTrue(bt.devices.all { it.bonded && it.audio && !it.connected && !it.streamingIn && !it.phone })
    }

    /** The "Bluetooth in" body is bt_idle with incoming set and that device streaming (spec). */
    @Test fun bluetoothIncomingFromTheFixtureWithTheSpecsEdit() {
        val edited = fixture("bt_idle.json")
            .replace("\"incoming\":\"\"", "\"incoming\":\"F4:4E:FD:7A:0C:92\"")
            .replace(
                "\"name\":\"UGREEN-BT305\",\"bonded\":true,\"audio\":true,\"connected\":false,\"streaming_in\":false",
                "\"name\":\"UGREEN-BT305\",\"bonded\":true,\"audio\":true,\"connected\":false,\"streaming_in\":true",
            )
        val bt = R1Json.decodeFromString<BtStateDto>(edited).toDomain()
        assertTrue(bt.hasIncoming)
        assertEquals("UGREEN-BT305", bt.incomingName)
        assertTrue(bt.devices.single { it.address == "F4:4E:FD:7A:0C:92" }.streamingIn)
    }

    @Test fun voices() {
        val v = parse<VoicesDto>("tts_voice.json").toDomain()
        assertEquals("Lê Yến", v.current)
        assertEquals(27, v.available.size)
        assertTrue("Thiền Tâm Đức" in v.available)
    }

    @Test fun missingFieldsBecomeNeutralValuesNotFailures() {
        val s = R1Json.decodeFromString<StateDto>("{}")
        assertEquals(0, s.toVolume().pct)
        assertFalse(s.toVolume().scale.hasHardwareSteps)
        assertEquals(VoiceActivity.Idle, s.toVoiceActivity())
        assertEquals("", s.toSettings().ttsHost)
        val m = R1Json.decodeFromString<MediaStateDto>("{}").toDomain()
        assertEquals(MediaStatus.Unknown, m.status)
        assertEquals("", R1Json.decodeFromString<BtStateDto>("{}").toDomain().incomingAddress)
    }

    @Test fun unknownKeysAreIgnoredAndAUserSenderIsTheUser() {
        val s = R1Json.decodeFromString<StateDto>(
            """{"volume":30,"ota_url":"x","chat":[{"sender":"user","text":"hi","time":5},{"sender":"assistant","text":"ok","time":6}]}""",
        )
        assertEquals(listOf(Sender.User, Sender.Assistant), s.toChat().map { it.sender })
        assertEquals(5L, s.toChat().first().timeMillis)
    }

    /** `state_idle.json` was captured before the listening controls: no `wake_enabled`, so neither control is drawn. */
    @Test fun aStateWithoutWakeEnabledMapsToAbsentControls() {
        val s = parse<StateDto>("state_idle.json")
        assertEquals(null, s.wakeEnabled)
        assertEquals(VoiceControls.Absent, s.toVoiceControls())
    }

    @Test fun wakeEnabledPresentIsTheSwitchWhicheverWayItIs() {
        assertEquals(VoiceControls.Present(true), StateDto(wakeEnabled = true).toVoiceControls())
        assertEquals(VoiceControls.Present(false), StateDto(wakeEnabled = false).toVoiceControls())
    }

    @Test fun fixturesFlowThroughToTheCard() {
        assertEquals(NowPlaying.Nothing, snapshotOf("state_idle.json", "media_idle.json", "bt_idle.json").nowPlaying)
        assertEquals(NowPlaying.Nothing, snapshotOf("state_idle.json", "media_stopped.json", "bt_idle.json").nowPlaying)
        val playing = assertIs<NowPlaying.Music>(snapshotOf("state_idle.json", "media_playing.json", "bt_idle.json").nowPlaying)
        assertEquals("Thành Tâm Sám Hối", playing.title)
        assertEquals(NowPlaying.Phase.Playing, playing.phase)
        assertEquals(NowPlaying.Phase.Paused, assertIs<NowPlaying.Music>(snapshotOf("state_idle.json", "media_paused.json", "bt_idle.json").nowPlaying).phase)
    }

    /**
     * B-12. After a stop or the end of a queue the R1 reopens its microphone in the same session
     * and reports `LISTENING`, awake (charter §8, measured 2026-11-07), with its own "loading song"
     * line as the newest chat. Nobody spoke: the card is Nothing. A session that times out
     * clears the player to `idle`, so a wake after it still shows as listening.
     */
    @Test fun theListeningTheR1LeavesAfterAStopIsNothing() {
        val songLine = listOf(ChatMessage(Sender.Assistant, "Đang tải bài Cho Đời Chút Ơn của Hoàng Yến Chibi", 1))
        fun listening(media: String) = snapshotOf("state_idle.json", media, "bt_idle.json")
            .copy(voice = VoiceActivity.Listening, voiceAwake = true, chat = songLine)
        assertEquals(NowPlaying.Nothing, listening("media_stopped.json").nowPlaying)
        assertEquals(
            NowPlaying.Assistant(NowPlaying.Activity.Listening, songLine.last().text),
            listening("media_idle.json").nowPlaying,
        )
        val speaking = snapshotOf("state_idle.json", "media_stopped.json", "bt_idle.json")
            .copy(voice = VoiceActivity.Speaking, voiceAwake = true, chat = songLine)
        assertIs<NowPlaying.Assistant>(speaking.nowPlaying, "an answer after a stop is still the assistant speaking")
    }
}
