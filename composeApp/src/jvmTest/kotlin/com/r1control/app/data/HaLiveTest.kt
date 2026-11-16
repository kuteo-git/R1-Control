package com.r1control.app.data

import com.r1control.app.data.remote.HaMessage
import com.r1control.app.data.remote.KtorHaSocketOpener
import com.r1control.app.data.remote.newHaClient
import com.r1control.app.data.remote.parseHaMessage
import com.r1control.app.data.repository.WebSocketHaRepository
import com.r1control.app.domain.model.ha.HaAddress
import com.r1control.app.domain.model.ha.HaConnectionState
import com.r1control.app.domain.model.ha.HaInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Against a real Home Assistant, skipped unless `HA_LIVE_ADDRESS` (`host[:port]`) is set. The first
 * test sends nothing, so a failed login is never recorded on the household's instance; the second also
 * needs `HA_LIVE_TOKEN`. Nothing here prints: a failure says what was expected, not what was received.
 */
class HaLiveTest {
    private val address: String? = System.getenv("HA_LIVE_ADDRESS")?.takeIf { it.isNotBlank() }
    private val token: String? = System.getenv("HA_LIVE_TOKEN")?.takeIf { it.isNotBlank() }

    @Test fun theSocketOpensAndHomeAssistantAsksForAuth() = runBlocking {
        val typed = address ?: return@runBlocking
        val parsed = assertIs<HaInput.Valid>(HaAddress.parse(typed, "unused")).address
        val client = newHaClient()
        try {
            var first = ""
            withTimeout(10_000) { KtorHaSocketOpener(client).open(parsed) { socket -> first = socket.receive().orEmpty() } }
            assertEquals(HaMessage.AuthRequired, parseHaMessage(first))
        } finally {
            client.close()
        }
    }

    @Test fun aRealTokenConnectsAndFillsTheEntities() = runBlocking {
        val typed = address ?: return@runBlocking
        val secret = token ?: return@runBlocking
        val parsed = assertIs<HaInput.Valid>(HaAddress.parse(typed, secret)).address
        val client = newHaClient()
        val repo = WebSocketHaRepository(client, kotlinx.coroutines.CoroutineScope(Dispatchers.Default))
        try {
            repo.connect(parsed)
            withTimeout(15_000) { repo.connectionState.first { it == HaConnectionState.Connected } }
            withTimeout(15_000) { repo.entities.first { it.isNotEmpty() } }
            assertTrue(repo.entities.value.isNotEmpty())
        } finally {
            repo.disconnect()
            client.close()
        }
    }
}
