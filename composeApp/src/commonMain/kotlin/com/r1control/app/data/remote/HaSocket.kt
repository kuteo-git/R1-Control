package com.r1control.app.data.remote

import com.r1control.app.domain.model.ha.HaAddress
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText

/** One open text WebSocket, as the protocol sees it. The seam the repository's tests fake. */
internal interface HaSocket {
    /** The next text frame, or null once the socket is closed. */
    suspend fun receive(): String?

    suspend fun send(text: String)
}

/** Opens a socket to Home Assistant and runs [session] on it; returns when the socket is closed, throws when it cannot be opened. */
internal fun interface HaSocketOpener {
    suspend fun open(address: HaAddress, session: suspend (HaSocket) -> Unit)
}

/**
 * A Ktor client for Home Assistant's WebSocket. No `Logging` plugin, on purpose: the first frame
 * a client sends is its token. (ArchitectureGuardTest holds this line.) Plain `ws://`: the
 * household's Home Assistant is on the house wifi, as the R1 is.
 */
fun newHaClient(): HttpClient = HttpClient {
    install(WebSockets)
}

internal class KtorHaSocketOpener(private val client: HttpClient) : HaSocketOpener {
    override suspend fun open(address: HaAddress, session: suspend (HaSocket) -> Unit) {
        client.webSocket(host = address.host, port = address.port, path = HA_SOCKET_PATH) {
            session(KtorHaSocket(this))
        }
    }
}

internal const val HA_SOCKET_PATH = "/api/websocket"

private class KtorHaSocket(private val socket: DefaultClientWebSocketSession) : HaSocket {
    override suspend fun receive(): String? {
        while (true) {
            val frame = socket.incoming.receiveCatching().getOrNull() ?: return null
            when (frame) {
                is Frame.Text -> return frame.readText()
                is Frame.Close -> return null
                else -> Unit
            }
        }
    }

    override suspend fun send(text: String) = socket.send(Frame.Text(text))
}
