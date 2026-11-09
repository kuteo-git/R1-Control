package com.r1control.app.data.remote

import com.r1control.app.domain.repository.SpeakerException
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * The content type of every body this app sends.
 *
 * `charset=utf-8` is spelled out, and in this case, because it was measured:
 * the R1's NanoHTTPD decodes a body without a charset as ASCII, so every
 * Vietnamese title became `Th��nh T��m` on the speaker and in the assistant's
 * spoken line. Pinned by a test.
 */
internal val JsonUtf8: ContentType = ContentType.parse("application/json; charset=utf-8")

internal val R1Json = Json { ignoreUnknownKeys = true }

/**
 * A Ktor client for the R1 and the TTS server. No `Logging` plugin, on
 * purpose: `/api/state` carries masked secrets and the household's device
 * names, and a log line is a copy nobody meant to make. (ArchitectureGuardTest
 * holds this line.)
 */
fun newHttpClient(): HttpClient = HttpClient {
    configure()
}

fun newHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    configure()
}

private fun io.ktor.client.HttpClientConfig<*>.configure() {
    install(HttpTimeout) {
        connectTimeoutMillis = 4_000
        requestTimeoutMillis = 10_000
        socketTimeoutMillis = 10_000
    }
}

/**
 * How long a read of the R1's state may take. Measured on the house wifi
 * (2026-11-08): every read answered in 30 to 220 ms. With 10 s a speaker that
 * stopped answering left a live-looking card for 9 to 12 s; with this and the
 * 1 s poll a hang shows within 3 s. Commands keep the client's 10 s: a search
 * is the R1 asking YouTube.
 */
internal const val READ_TIMEOUT_MS = 2_000L

/** Sends a request and hands back the body text, or throws a [SpeakerException]. */
internal class R1Http(private val client: HttpClient) {

    suspend fun call(
        method: HttpMethod,
        describeUrl: URLBuilder.() -> Unit,
        body: String? = null,
    ): String {
        val response = try {
            client.request {
                this.method = method
                // Named so that `build()` cannot resolve to the builders' own member.
                url { describeUrl() }
                if (body != null) setBody(TextContent(body, JsonUtf8))
                if (method == HttpMethod.Get) timeout { requestTimeoutMillis = READ_TIMEOUT_MS }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw SpeakerException.Unreachable(e)
        }
        if (!response.status.isSuccess()) {
            throw SpeakerException.Refused("HTTP ${response.status.value}")
        }
        // Decoded as UTF-8 whatever the R1 declares: its replies carry no charset.
        return try {
            response.bodyAsBytes().decodeToString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw SpeakerException.Unreachable(e)
        }
    }
}
