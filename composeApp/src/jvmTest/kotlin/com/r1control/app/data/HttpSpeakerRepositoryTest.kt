package com.r1control.app.data

import com.r1control.app.data.remote.newHttpClient
import com.r1control.app.data.repository.HttpSpeakerRepository
import com.r1control.app.data.repository.HttpVoiceRepository
import com.r1control.app.domain.model.NewsCategory
import com.r1control.app.domain.model.NewsSettings
import com.r1control.app.domain.model.R1Address
import com.r1control.app.domain.model.SavedAddress
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.model.VoiceControls
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.testing.MemoryPreferences
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HttpSpeakerRepositoryTest {
    private val address = R1Address("10.0.0.5", 8088)
    private val prefs = MemoryPreferences(address = SavedAddress.Set(address))
    private val requests = mutableListOf<HttpRequestData>()

    private fun repo(handler: (HttpRequestData) -> Pair<HttpStatusCode, String> = { HttpStatusCode.OK to """{"ok":true}""" }): HttpSpeakerRepository {
        val engine = MockEngine { request ->
            requests += request
            val (status, body) = handler(request)
            respond(body.encodeToByteArray(), status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return HttpSpeakerRepository(newHttpClient(engine), prefs)
    }

    private val vietnamese = listOf(
        Track("qkXf2c5mlA8", "Thành Tâm Sám Hối", "NSƯT Thanh Ngân", "10:05", "https://yt3.googleusercontent.com/x=w120"),
        Track("l2wNFecE_aU", "Chiều Mưa Qua Sông", "Thanh Ngân, Gia Tiến", "5:03", ""),
    )

    /** Measured on the device: without the charset NanoHTTPD reads the body as ASCII and every accent turns to `��`. */
    @Test fun playBodyIsSentAsJsonWithAnExplicitUtf8Charset() = runTest {
        repo().play(vietnamese, 1)
        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/media/play", request.url.encodedPath)
        val body = assertIs<OutgoingContent.ByteArrayContent>(request.body)
        assertEquals("application/json; charset=utf-8", body.contentType.toString())
    }

    @Test fun playBodyBytesAreUtf8AndCarryTheWholeQueue() = runTest {
        repo().play(vietnamese, 1)
        val bytes = (requests.single().body as OutgoingContent.ByteArrayContent).bytes()
        val text = bytes.decodeToString()
        assertTrue("Thành Tâm Sám Hối" in text, text)
        assertTrue("\"start_index\":1" in text, text)
        assertTrue("\"video_id\":\"l2wNFecE_aU\"" in text, text)
        assertTrue("\"thumbnail\":\"\"" in text, "empty fields are sent as empty strings, not omitted: $text")
        assertTrue(bytes.size > text.length, "the accents are multi-byte")
    }

    @Test fun newsSaveUsesTheSameCharsetBecauseItCarriesAVietnameseVoiceName() = runTest {
        repo().saveNews(NewsSettings(true, "09:15", "Thiền Tâm Đức", listOf(NewsCategory("tech", true), NewsCategory("power", false))))
        val body = assertIs<OutgoingContent.ByteArrayContent>(requests.single().body)
        assertEquals("application/json; charset=utf-8", body.contentType.toString())
        assertEquals(
            """{"enabled":true,"time":"09:15","voice":"Thiền Tâm Đức","categories":[{"key":"tech","enabled":true},{"key":"power","enabled":false}]}""",
            body.bytes().decodeToString(),
        )
    }

    @Test fun sayPutsTheTrimmedTextInTheQueryPercentEncoded() = runTest {
        repo().say("xin chào & tạm biệt")
        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/say", request.url.encodedPath)
        assertEquals("xin chào & tạm biệt", request.url.parameters["text"])
        assertTrue("%26" in request.url.encodedQuery, request.url.encodedQuery)
    }

    @Test fun volumeAndSeekAreQueryWrites() = runTest {
        val r = repo()
        r.setVolume(40)
        r.seek(125)
        r.next()
        assertEquals(listOf("/api/set", "/api/media/seek", "/api/media/next"), requests.map { it.url.encodedPath })
        assertEquals("volume", requests[0].url.parameters["key"])
        assertEquals("40", requests[0].url.parameters["value"])
        assertEquals("125", requests[1].url.parameters["position_s"])
        assertTrue(requests.all { it.method == HttpMethod.Post })
        assertTrue(requests.all { it.url.host == "10.0.0.5" && it.url.port == 8088 && it.url.protocol.name == "http" })
    }

    @Test fun bluetoothWritesUseTheirPaths() = runTest {
        val r = repo()
        r.setBluetoothEnabled(true)
        r.startBluetoothScan()
        r.connect("14:C1:4E:75:70:64")
        r.setAutoReconnect(false)
        assertEquals(
            listOf("/api/bt/enable", "/api/bt/scan/start", "/api/bt/connect", "/api/bt/auto"),
            requests.map { it.url.encodedPath },
        )
        assertEquals("1", requests[0].url.parameters["on"])
        assertEquals("14:C1:4E:75:70:64", requests[2].url.parameters["addr"])
        assertEquals("0", requests[3].url.parameters["on"])
    }

    /** The R1 reads anything but `on=1` as off, so the flag is sent for both values and never omitted. */
    @Test fun theWakeWordAndEndSessionWritesUseTheirPathsAndPinTheFlag() = runTest {
        val r = repo()
        r.setWakeWord(true)
        r.setWakeWord(false)
        r.endVoiceSession()
        assertEquals(
            listOf("/api/wake/enable", "/api/wake/enable", "/api/voice/sleep"),
            requests.map { it.url.encodedPath },
        )
        assertEquals(listOf("1", "0", null), requests.map { it.url.parameters["on"] })
        assertTrue(requests.all { it.method == HttpMethod.Post })
    }

    @Test fun aRefusedWakeWordWriteIsARefusal() = runTest {
        val r = repo { HttpStatusCode.OK to """{"ok":false,"error":"busy"}""" }
        assertFailsWith<SpeakerException.Refused> { r.setWakeWord(true) }
    }

    @Test fun eqBandsAreCommaJoined() = runTest {
        repo().setEqBands(listOf(1000, 0, -600, 0, 800))
        assertEquals("1000,0,-600,0,800", requests.single().url.parameters["value"])
        assertEquals("eq_bands", requests.single().url.parameters["key"])
    }

    @Test fun searchReadsFixtureResultsAndSendsTheKeywordEncoded() = runTest {
        val r = repo { HttpStatusCode.OK to fixture("search_thanh_ngan.json") }
        val tracks = r.search("Thanh Ngân")
        assertEquals("Thanh Ngân", requests.single().url.parameters["q"])
        assertEquals("/api/media/search", requests.single().url.encodedPath)
        assertEquals("qkXf2c5mlA8", tracks.first().id)
    }

    @Test fun snapshotReadsTheThreeStatesFromTheFixtures() = runTest {
        val r = repo {
            HttpStatusCode.OK to when (it.url.encodedPath) {
                "/api/state" -> fixture("state_idle.json")
                "/api/media/state" -> fixture("media_playing.json")
                "/api/bt/state" -> fixture("bt_idle.json")
                else -> error(it.url.encodedPath)
            }
        }
        val s = r.snapshot()
        assertEquals(3, requests.size)
        assertTrue(requests.all { it.method == HttpMethod.Get })
        assertEquals(100, s.volume.pct)
        assertEquals("Thành Tâm Sám Hối", s.media.title)
        assertEquals(2, s.bluetooth.devices.size)
        assertEquals(VoiceControls.Absent, s.voiceControls, "an R1 build before the listening controls reports no wake_enabled")
    }

    /** A constructed body, the one exception to "copied from the R1": no installed build reports `wake_enabled` yet. */
    @Test fun aBodyWithWakeEnabledCarriesTheSwitchThroughTheSnapshot() = runTest {
        for ((wire, expected) in listOf("true" to true, "false" to false)) {
            val withKey = fixture("state_idle.json").trimEnd().removeSuffix("}") + ""","wake_enabled":$wire}"""
            val r = repo {
                HttpStatusCode.OK to when (it.url.encodedPath) {
                    "/api/state" -> withKey
                    "/api/media/state" -> fixture("media_idle.json")
                    "/api/bt/state" -> fixture("bt_idle.json")
                    else -> error(it.url.encodedPath)
                }
            }
            assertEquals(VoiceControls.Present(expected), r.snapshot().voiceControls)
        }
    }

    @Test fun aSearchThatTheR1RefusesIsARefusal() = runTest {
        val r = repo { HttpStatusCode.OK to """{"ok":false,"error":"search failed"}""" }
        val e = assertFailsWith<SpeakerException.Refused> { r.search("x") }
        assertEquals("search failed", e.reason)
    }

    @Test fun aServerErrorIsARefusalWithoutItsBody() = runTest {
        val r = repo { HttpStatusCode.InternalServerError to "secret-token-abc" }
        val e = assertFailsWith<SpeakerException.Refused> { r.pause() }
        assertEquals("HTTP 500", e.reason)
        assertTrue("secret" !in e.message.orEmpty())
    }

    @Test fun anUnreadableReplyIsBadReplyWithoutItsBody() = runTest {
        val r = repo { HttpStatusCode.OK to "<html>secret-token-abc</html>" }
        val e = assertFailsWith<SpeakerException.BadReply> { r.snapshot() }
        assertTrue("secret" !in e.message.orEmpty())
    }

    @Test fun noRequestWhenNoAddressIsSaved() = runTest {
        prefs.address = SavedAddress.Unset
        assertFailsWith<SpeakerException.NotConfigured> { repo().pause() }
        assertTrue(requests.isEmpty())
    }

    @Test fun aNetworkFailureIsUnreachable() = runTest {
        val engine = MockEngine { throw java.io.IOException("no route") }
        val r = HttpSpeakerRepository(newHttpClient(engine), prefs)
        assertFailsWith<SpeakerException.Unreachable> { r.pause() }
    }

    /**
     * B-10. A speaker that accepts the connection and never answers showed nothing for 9 to 12 s:
     * the read waited out the 10 s request timeout. The R1 answers a read in 30 to 220 ms on the
     * house wifi (measured 2026-11-08), so a read gets [READ_TIMEOUT_MS].
     */
    @Test fun aReadTheSpeakerNeverAnswersFailsWithinTwoSeconds() = runTest {
        val engine = MockEngine { kotlinx.coroutines.delay(30_000); respond("{}") }
        val r = HttpSpeakerRepository(newHttpClient(engine), prefs)
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        assertFailsWith<SpeakerException.Unreachable> { r.snapshot() }
        val took = started.elapsedNow()
        assertTrue(took.inWholeMilliseconds < 3_000, "a hung read took $took to fail")
    }

    /** A search is the R1 asking YouTube: it is not held to a read's time. */
    @Test fun aSlowSearchIsNotCutShortLikeARead() = runTest {
        val engine = MockEngine {
            kotlinx.coroutines.delay(2_500)
            respond(fixture("search_thanh_ngan.json").encodeToByteArray(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val r = HttpSpeakerRepository(newHttpClient(engine), prefs)
        assertEquals("qkXf2c5mlA8", r.search("Thanh Ngân").first().id)
    }

    @Test fun probeAsksTheGivenAddressNotTheSavedOne() = runTest {
        val r = repo { HttpStatusCode.OK to fixture("state_idle.json") }
        r.probe(R1Address("192.168.9.9", 9999))
        assertEquals("192.168.9.9", requests.single().url.host)
        assertEquals(9999, requests.single().url.port)
    }

    @Test fun aNewlySavedAddressIsUsedByTheNextCall() = runTest {
        val r = repo()
        r.pause()
        prefs.saveAddress(R1Address("10.0.0.9", 8088))
        r.pause()
        assertEquals(listOf("10.0.0.5", "10.0.0.9"), requests.map { it.url.host })
    }

    @Test fun voicesAreAskedAtTheTtsHost() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond(fixture("tts_voice.json").encodeToByteArray(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val repo = HttpVoiceRepository(newHttpClient(engine))
        val voices = repo.voices("http://10.25.113.151:8002")
        repo.setVoice("http://10.25.113.151:8002", "Thiền Tâm Đức")
        assertEquals("Lê Yến", voices.current)
        assertEquals("10.25.113.151", requests[0].url.host)
        assertEquals(8002, requests[0].url.port)
        assertEquals("/voice", requests[0].url.encodedPath)
        assertEquals(HttpMethod.Post, requests[1].method)
        assertEquals("Thiền Tâm Đức", requests[1].url.parameters["name"])
        assertFailsWith<SpeakerException.BadReply> { repo.voices("") }
    }
}
