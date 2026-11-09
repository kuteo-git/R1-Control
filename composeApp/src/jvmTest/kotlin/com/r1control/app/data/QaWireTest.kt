package com.r1control.app.data

import com.r1control.app.data.remote.dto.MediaStateDto
import com.r1control.app.data.remote.newHttpClient
import com.r1control.app.data.remote.toDomain
import com.r1control.app.data.repository.HttpSpeakerRepository
import com.r1control.app.data.repository.HttpVoiceRepository
import com.r1control.app.domain.model.NowPlaying
import com.r1control.app.domain.model.R1Address
import com.r1control.app.domain.model.SavedAddress
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.testing.MemoryPreferences
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class QaWireTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val prefs = MemoryPreferences(address = SavedAddress.Set(R1Address("10.0.0.5", 8088)))

    /** Answers like the R1: `application/json` with no charset, bodies from docs/fixtures. */
    private fun engine(state: String = fixture("state_idle.json"), media: String = fixture("media_playing.json")) = MockEngine { request ->
        requests += request
        val body = when (request.url.encodedPath) {
            "/api/state" -> state
            "/api/media/state" -> media
            "/api/bt/state" -> fixture("bt_idle.json")
            else -> """{"ok":true}"""
        }
        respond(body.encodeToByteArray(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private fun repo(state: String = fixture("state_idle.json"), media: String = fixture("media_playing.json")) =
        HttpSpeakerRepository(newHttpClient(engine(state, media)), prefs)

    /** state_idle.json with the chat line the charter measured ("Đang tải bài ... của ..."). */
    private fun stateWithChat(): String {
        val state = Json.parseToJsonElement(fixture("state_idle.json")).jsonObject
        val line = JsonObject(
            mapOf(
                "sender" to JsonPrimitive("assistant"),
                "text" to JsonPrimitive("Đang tải bài Thành Tâm Sám Hối của NSƯT Thanh Ngân"),
                "time" to JsonPrimitive(1_762_500_010_000L),
            ),
        )
        return JsonObject(state + ("chat" to JsonArray(listOf(line)))).toString()
    }

    @Test fun aVietnameseChatLineInAReplyWithNoCharsetReadsAsUtf8() = runTest {
        val chat = repo(state = stateWithChat()).snapshot().chat
        assertEquals("Đang tải bài Thành Tâm Sám Hối của NSƯT Thanh Ngân", chat.single().text)
    }

    @Test fun aStoppedPlayerFromTheFixtureIsNothingWithNoNext() = runTest {
        val snapshot = repo(media = fixture("media_stopped.json")).snapshot()
        assertEquals("", snapshot.media.videoId)
        assertTrue(snapshot.media.queue.isNotEmpty(), "the queue is kept")
        assertTrue(!snapshot.media.hasNext)
        assertEquals(NowPlaying.Nothing, snapshot.nowPlaying)
    }

    @Test fun thePlayingFixtureHasNextOnlyIfItsSongIsNotLastInTheQueue() {
        val media = fixture("media_playing.json").let { Json { ignoreUnknownKeys = true }.decodeFromString<MediaStateDto>(it) }.toDomain()
        val at = media.queue.indexOfFirst { it.id == media.videoId }
        assertEquals(at >= 0 && at < media.queue.lastIndex, media.hasNext)
    }

    @Test fun aPlusAndAnAmpersandInAChatLineReachTheSpeakerAsTyped() = runTest {
        repo().say("1+1=2 & còn gì?")
        val request = requests.single()
        assertEquals("1+1=2 & còn gì?", request.url.parameters["text"])
        assertTrue("%2B" in request.url.encodedQuery, "a raw + is read as a space by NanoHTTPD: ${request.url.encodedQuery}")
    }

    @Test fun aVietnameseSearchKeywordIsSentPercentEncodedAsUtf8() = runTest {
        repo().search("Sương Trắng")
        val request = requests.single()
        assertEquals("Sương Trắng", request.url.parameters["q"])
        assertTrue("S%C6%B0%C6%A1ng" in request.url.encodedQuery, request.url.encodedQuery)
    }

    @Test fun aSpeakerThatAnswersNotFoundIsARefusalNotUnreachable() = runTest {
        val engine = MockEngine { respond("404", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "text/plain")) }
        val error = assertFailsWith<SpeakerException> { HttpSpeakerRepository(newHttpClient(engine), prefs).snapshot() }
        assertIs<SpeakerException.Refused>(error)
    }

    /** `tts_host` comes from the R1; an unusable one is the R1's bad answer, not the wifi. */
    @Test fun anUnusableTtsHostIsABadReplyNotUnreachable() = runTest {
        val engine = MockEngine { respond("{}", HttpStatusCode.OK) }
        val error = assertFailsWith<SpeakerException> { HttpVoiceRepository(newHttpClient(engine)).voices("http://tts:port") }
        assertIs<SpeakerException.BadReply>(error, "got ${error::class.simpleName}")
    }
}
