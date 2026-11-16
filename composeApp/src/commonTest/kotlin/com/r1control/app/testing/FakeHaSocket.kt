package com.r1control.app.testing

import com.r1control.app.data.remote.HaSocket
import com.r1control.app.data.remote.HaSocketOpener
import com.r1control.app.domain.model.ha.HaAddress
import kotlinx.coroutines.channels.Channel

/** One socket a test drives by pushing the server's frames; `null` is the server closing. */
internal class FakeHaSocket : HaSocket {
    val incoming = Channel<String?>(Channel.UNLIMITED)
    val sent = mutableListOf<String>()

    override suspend fun receive(): String? = incoming.receive()
    override suspend fun send(text: String) {
        sent += text
    }

    fun push(frame: String) {
        incoming.trySend(frame)
    }

    fun close() {
        incoming.trySend(null)
    }
}

/** Hands out a fresh [FakeHaSocket] per open, and can refuse to open. */
internal class FakeHaOpener : HaSocketOpener {
    val sockets = mutableListOf<FakeHaSocket>()
    val opened get() = sockets.size + refused
    var refused = 0
    var refuseOpens = false

    override suspend fun open(address: HaAddress, session: suspend (HaSocket) -> Unit) {
        if (refuseOpens) {
            refused++
            throw RuntimeException("connection refused")
        }
        val socket = FakeHaSocket()
        sockets += socket
        session(socket)
    }

    val last get() = sockets.last()
}

const val HA_AUTH_REQUIRED = """{"type":"auth_required","ha_version":"2026.10.4"}"""
const val HA_AUTH_OK = """{"type":"auth_ok","ha_version":"2026.10.4"}"""
const val HA_AUTH_INVALID = """{"type":"auth_invalid","message":"Invalid access token or password"}"""

fun haSnapshot(vararg entities: Pair<String, String>): String =
    """{"id":1,"type":"event","event":{"a":{""" +
        entities.joinToString(",") { (id, state) -> """"$id":{"s":"$state","a":{"friendly_name":"$id"}}""" } +
        "}}}"
