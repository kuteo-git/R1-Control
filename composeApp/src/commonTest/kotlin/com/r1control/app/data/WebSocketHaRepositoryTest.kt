package com.r1control.app.data

import com.r1control.app.data.repository.WebSocketHaRepository
import com.r1control.app.data.repository.reconnectDelayMillis
import com.r1control.app.domain.model.ha.HaAddress
import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaConnectionState
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.repository.HaException
import com.r1control.app.testing.FakeHaOpener
import com.r1control.app.testing.HA_AUTH_INVALID
import com.r1control.app.testing.HA_AUTH_OK
import com.r1control.app.testing.HA_AUTH_REQUIRED
import com.r1control.app.testing.haSnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebSocketHaRepositoryTest {
    private val address = HaAddress("ha.local", 8123, "SECRET")
    private val opener = FakeHaOpener()

    private fun TestScope.repo() = WebSocketHaRepository(opener, backgroundScope)

    private fun TestScope.connected(repo: WebSocketHaRepository, snapshot: String = haSnapshot("light.a" to "on")) {
        repo.connect(address)
        runCurrent()
        opener.last.push(HA_AUTH_REQUIRED)
        runCurrent()
        opener.last.push(HA_AUTH_OK)
        runCurrent()
        opener.last.push(snapshot)
        runCurrent()
    }

    @Test fun theHandshakeSendsTheTokenThenSubscribesAndFillsTheEntities() = runTest {
        val repo = repo()
        assertEquals(HaConnectionState.Disconnected, repo.connectionState.value)
        repo.connect(address)
        assertEquals(HaConnectionState.Connecting, repo.connectionState.value)
        runCurrent()
        opener.last.push(HA_AUTH_REQUIRED)
        runCurrent()
        assertEquals(listOf("""{"type":"auth","access_token":"SECRET"}"""), opener.last.sent)
        assertEquals(HaConnectionState.Connecting, repo.connectionState.value, "not connected until Home Assistant says so")

        opener.last.push(HA_AUTH_OK)
        runCurrent()
        assertEquals(HaConnectionState.Connected, repo.connectionState.value)
        assertEquals("""{"id":1,"type":"subscribe_entities"}""", opener.last.sent[1])

        opener.last.push(haSnapshot("light.a" to "on", "switch.b" to "off"))
        runCurrent()
        assertEquals(setOf("light.a", "switch.b"), repo.entities.value.keys.map { it.value }.toSet())
        assertEquals("on", repo.entities.value.getValue(HaEntityId("light.a")).state)
    }

    @Test fun liveChangesUpdateTheEntities() = runTest {
        val repo = repo()
        connected(repo, haSnapshot("light.a" to "on", "switch.b" to "off"))
        opener.last.push("""{"id":1,"type":"event","event":{"c":{"light.a":{"+":{"s":"off"}}},"r":["switch.b"]}}""")
        runCurrent()
        assertEquals(setOf(HaEntityId("light.a")), repo.entities.value.keys)
        assertEquals("off", repo.entities.value.getValue(HaEntityId("light.a")).state)
    }

    @Test fun aRefusedTokenIsFinalAndIsNotRetried() = runTest {
        val repo = repo()
        repo.connect(address)
        runCurrent()
        opener.last.push(HA_AUTH_REQUIRED)
        runCurrent()
        opener.last.push(HA_AUTH_INVALID)
        runCurrent()
        assertEquals(HaConnectionState.AuthInvalid, repo.connectionState.value)
        advanceTimeBy(10 * 60_000L)
        runCurrent()
        assertEquals(1, opener.opened, "the same token would be refused again")
        assertEquals(HaConnectionState.AuthInvalid, repo.connectionState.value)
    }

    @Test fun somethingThatIsNotHomeAssistantIsUnreachable() = runTest {
        val repo = repo()
        repo.connect(address)
        runCurrent()
        opener.last.push("""{"hello":"world"}""")
        runCurrent()
        assertEquals(HaConnectionState.Unreachable, repo.connectionState.value)
        assertFalse(opener.last.sent.any { "SECRET" in it }, "the token goes only to something that said auth_required")
    }

    @Test fun aClosedSocketIsRetriedAndTheNewSnapshotReplacesTheOld() = runTest {
        val repo = repo()
        connected(repo, haSnapshot("light.a" to "on", "switch.gone" to "on"))
        opener.last.close()
        runCurrent()
        assertEquals(HaConnectionState.Unreachable, repo.connectionState.value)
        assertEquals(2, repo.entities.value.size, "what was known stays on screen while the link is down")

        advanceTimeBy(reconnectDelayMillis(0) + 1)
        runCurrent()
        assertEquals(2, opener.opened)
        assertEquals(HaConnectionState.Connecting, repo.connectionState.value)
        opener.last.push(HA_AUTH_REQUIRED)
        runCurrent()
        opener.last.push(HA_AUTH_OK)
        runCurrent()
        opener.last.push(haSnapshot("light.a" to "off"))
        runCurrent()
        assertEquals(HaConnectionState.Connected, repo.connectionState.value)
        assertEquals(setOf(HaEntityId("light.a")), repo.entities.value.keys, "an entity removed while the link was down is gone")
    }

    @Test fun anAddressThatNeverOpensBacksOffAndKeepsTrying() = runTest {
        opener.refuseOpens = true
        val repo = repo()
        repo.connect(address)
        runCurrent()
        assertEquals(HaConnectionState.Unreachable, repo.connectionState.value)
        assertEquals(1, opener.opened)
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(2, opener.opened)
        advanceTimeBy(1_998)
        runCurrent()
        assertEquals(2, opener.opened, "the second wait is two seconds")
        advanceTimeBy(3)
        runCurrent()
        assertEquals(3, opener.opened)
    }

    @Test fun aLinkThatGoesQuietIsDroppedAndRetried() = runTest {
        val repo = repo()
        connected(repo)
        advanceTimeBy(60_500)
        runCurrent()
        assertEquals(HaConnectionState.Unreachable, repo.connectionState.value)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, opener.opened)
    }

    @Test fun pingsKeepTheLinkAlive() = runTest {
        val repo = repo()
        connected(repo)
        advanceTimeBy(45_000)
        runCurrent()
        val pings = opener.last.sent.filter { """"type":"ping"""" in it }
        assertEquals(2, pings.size)
        assertEquals("""{"id":2,"type":"ping"}""", pings.first())
    }

    @Test fun connectingAgainToTheSameAddressChangesNothing() = runTest {
        val repo = repo()
        connected(repo)
        repo.connect(address)
        runCurrent()
        assertEquals(1, opener.opened)
        assertEquals(HaConnectionState.Connected, repo.connectionState.value)
        assertEquals(1, repo.entities.value.size)
    }

    @Test fun connectingToAnotherAddressReplacesTheLink() = runTest {
        val repo = repo()
        connected(repo)
        repo.connect(HaAddress("other.local", 8123, "other"))
        assertEquals(HaConnectionState.Connecting, repo.connectionState.value)
        assertTrue(repo.entities.value.isEmpty(), "the old house's entities are not the new one's")
        runCurrent()
        assertEquals(2, opener.opened)
    }

    @Test fun disconnectClosesTheLinkAndForgetsTheEntities() = runTest {
        val repo = repo()
        connected(repo)
        repo.disconnect()
        runCurrent()
        assertEquals(HaConnectionState.Disconnected, repo.connectionState.value)
        assertTrue(repo.entities.value.isEmpty())
        advanceTimeBy(5 * 60_000L)
        runCurrent()
        assertEquals(1, opener.opened, "a closed link is not reopened")
        assertFailsWith<HaException.NotConnected> { repo.callService("light", "turn_on", emptyMap()) }
    }

    @Test fun aServiceCallIsSentWithTheNextIdAndAnswersOnItsResult() = runTest {
        val repo = repo()
        connected(repo)
        val call = async { repo.callService("light", "turn_on", mapOf("entity_id" to HaAttribute.Text("light.a"))) }
        runCurrent()
        assertEquals(
            """{"id":2,"type":"call_service","domain":"light","service":"turn_on","service_data":{"entity_id":"light.a"}}""",
            opener.last.sent.last(),
        )
        assertFalse(call.isCompleted)
        opener.last.push("""{"id":2,"type":"result","success":true,"result":null}""")
        runCurrent()
        call.await()
    }

    @Test fun aRefusedServiceCallThrowsItsCodeAndNothingElse() = runTest {
        val repo = repo()
        connected(repo)
        val call = async { runCatching { repo.callService("light", "nope", emptyMap()) } }
        runCurrent()
        opener.last.push("""{"id":2,"type":"result","success":false,"error":{"code":"not_found","message":"Service light.nope not found."}}""")
        runCurrent()
        val failure = assertFailsWith<HaException.Refused> { call.await().getOrThrow() }
        assertEquals("not_found", failure.code)
        assertFalse("Service light" in (failure.message ?: ""), "the reply's own text is not carried")
    }

    @Test fun aServiceCallWithNoAnswerTimesOut() = runTest {
        val repo = repo()
        connected(repo)
        val call = async { runCatching { repo.callService("light", "turn_on", emptyMap()) } }
        runCurrent()
        advanceTimeBy(10_001)
        runCurrent()
        assertFailsWith<HaException.TimedOut> { call.await().getOrThrow() }
    }

    @Test fun aServiceCallStrandedByADropIsToldTheLinkIsGone() = runTest {
        val repo = repo()
        connected(repo)
        val call = async { runCatching { repo.callService("light", "turn_on", emptyMap()) } }
        runCurrent()
        opener.last.close()
        runCurrent()
        assertFailsWith<HaException.NotConnected> { call.await().getOrThrow() }
    }

    @Test fun theDashboardConfigIsAskedForAndReturnedAsText() = runTest {
        val repo = repo()
        connected(repo)
        val config = async { repo.dashboardConfig() }
        runCurrent()
        assertEquals("""{"id":2,"type":"lovelace/config"}""", opener.last.sent.last())
        assertFalse(config.isCompleted)
        opener.last.push("""{"id":2,"type":"result","success":true,"result":{"views":[{"title":"Home"}]}}""")
        runCurrent()
        assertEquals("""{"views":[{"title":"Home"}]}""", config.await())
    }

    @Test fun aDashboardHomeAssistantDoesNotHoldIsARefusalWithItsCodeOnly() = runTest {
        val repo = repo()
        connected(repo)
        val config = async { runCatching { repo.dashboardConfig() } }
        runCurrent()
        opener.last.push("""{"id":2,"type":"result","success":false,"error":{"code":"config_not_found","message":"No config found."}}""")
        runCurrent()
        assertEquals("config_not_found", assertFailsWith<HaException.Refused> { config.await().getOrThrow() }.code)
    }

    @Test fun theDashboardConfigNeedsAnOpenLink() = runTest {
        assertFailsWith<HaException.NotConnected> { repo().dashboardConfig() }
    }

    // ---- template subscriptions ----

    private fun event(id: Int, result: String) = """{"id":$id,"type":"event","event":{"result":"$result","listeners":{"all":false,"entities":["sensor.a"]}}}"""

    private fun TestScope.collecting(repo: WebSocketHaRepository, template: String, into: MutableList<String>) =
        launch { repo.renderedTemplate(template).collect { into += it } }

    @Test fun aTemplateIsSubscribedWithTheNextIdAndEmitsEachRenderingInOrder() = runTest {
        val repo = repo()
        connected(repo)
        val seen = mutableListOf<String>()
        val job = collecting(repo, "{{ states('sensor.a') }}", seen)
        runCurrent()
        assertEquals("""{"id":2,"type":"render_template","template":"{{ states('sensor.a') }}"}""", opener.last.sent.last())
        opener.last.push("""{"id":2,"type":"result","success":true,"result":null}""")
        opener.last.push(event(2, "one"))
        opener.last.push(event(2, "two"))
        runCurrent()
        assertEquals(listOf("one", "two"), seen)
        job.cancel()
    }

    @Test fun stoppingTheCollectionSendsUnsubscribeEventsOnceAndLaterEventsAreDropped() = runTest {
        val repo = repo()
        connected(repo)
        val seen = mutableListOf<String>()
        val job = collecting(repo, "x", seen)
        runCurrent()
        opener.last.push(event(2, "one"))
        runCurrent()
        job.cancel()
        runCurrent()
        assertEquals("""{"id":3,"type":"unsubscribe_events","subscription":2}""", opener.last.sent.last())
        opener.last.push(event(2, "late"))
        runCurrent()
        assertEquals(listOf("one"), seen)
        assertEquals(1, opener.last.sent.count { "unsubscribe_events" in it })
    }

    @Test fun aLinkThatGoesDownEndsTheFlowWithoutSendingAnythingToTheDeadSocket() = runTest {
        val repo = repo()
        connected(repo)
        val seen = mutableListOf<String>()
        val job = collecting(repo, "x", seen)
        runCurrent()
        opener.last.push(event(2, "one"))
        runCurrent()
        val sent = opener.last.sent.size
        opener.last.close()
        runCurrent()
        assertTrue(job.isCompleted, "the flow ends when the link does")
        assertEquals(listOf("one"), seen)
        assertEquals(sent, opener.last.sent.size, "no unsubscribe goes to a closed link")
    }

    @Test fun collectingAgainOnTheNewLinkSubscribesAgain() = runTest {
        val repo = repo()
        connected(repo)
        val first = mutableListOf<String>()
        val job = collecting(repo, "x", first)
        runCurrent()
        opener.last.close()
        runCurrent()
        assertTrue(job.isCompleted)
        advanceTimeBy(reconnectDelayMillis(0) + 1)
        runCurrent()
        opener.last.push(HA_AUTH_REQUIRED)
        runCurrent()
        opener.last.push(HA_AUTH_OK)
        runCurrent()
        opener.last.push(haSnapshot("light.a" to "on"))
        runCurrent()
        val second = mutableListOf<String>()
        val again = collecting(repo, "x", second)
        runCurrent()
        assertEquals("""{"id":2,"type":"render_template","template":"x"}""", opener.last.sent.last())
        opener.last.push(event(2, "back"))
        runCurrent()
        assertEquals(listOf("back"), second)
        again.cancel()
    }

    @Test fun theEntitiesKeepUpdatingWhileATemplateIsActiveAndNeitherLeaksIntoTheOther() = runTest {
        val repo = repo()
        connected(repo, haSnapshot("light.a" to "on"))
        val seen = mutableListOf<String>()
        val job = collecting(repo, "x", seen)
        runCurrent()
        opener.last.push(event(2, "one"))
        opener.last.push("""{"id":1,"type":"event","event":{"c":{"light.a":{"+":{"s":"off"}}}}}""")
        opener.last.push(event(2, "two"))
        runCurrent()
        assertEquals("off", repo.entities.value.getValue(HaEntityId("light.a")).state)
        assertEquals(listOf("one", "two"), seen)
        job.cancel()
    }

    @Test fun twoTemplatesGetTheirOwnIdsAndTheirOwnRenderings() = runTest {
        val repo = repo()
        connected(repo)
        val a = mutableListOf<String>()
        val b = mutableListOf<String>()
        val first = collecting(repo, "a", a)
        runCurrent()
        val second = collecting(repo, "b", b)
        runCurrent()
        assertEquals(listOf(2, 3), opener.last.sent.filter { "render_template" in it }.map { Regex(""""id":(\d+)""").find(it)!!.groupValues[1].toInt() })
        opener.last.push(event(3, "for b"))
        opener.last.push(event(2, "for a"))
        opener.last.push(event(9, "nobody"))
        runCurrent()
        assertEquals(listOf("for a"), a)
        assertEquals(listOf("for b"), b)
        first.cancel()
        second.cancel()
    }

    @Test fun aTemplateAndAServiceCallNeverShareAnId() = runTest {
        val repo = repo()
        connected(repo)
        val job = collecting(repo, "x", mutableListOf())
        runCurrent()
        val call = async { repo.callService("light", "turn_on", emptyMap()) }
        runCurrent()
        assertTrue(""""id":3,""" in opener.last.sent.last())
        opener.last.push("""{"id":3,"type":"result","success":true,"result":null}""")
        runCurrent()
        call.await()
        job.cancel()
    }

    @Test fun aTemplateHomeAssistantRefusesEndsItsFlowAndSendsNoUnsubscribe() = runTest {
        val repo = repo()
        connected(repo)
        val job = collecting(repo, "{{ oops", mutableListOf())
        runCurrent()
        opener.last.push("""{"id":2,"type":"result","success":false,"error":{"code":"template_error","message":"bad"}}""")
        runCurrent()
        assertTrue(job.isCompleted)
        assertFalse(opener.last.sent.any { "unsubscribe_events" in it })
    }

    @Test fun withNoOpenLinkTheFlowEndsAtOnceWithNothing() = runTest {
        val seen = mutableListOf<String>()
        collecting(repo(), "x", seen).join()
        assertEquals(emptyList(), seen)
        assertEquals(emptyList(), opener.sockets)
    }

    @Test fun reconnectDelaysDoubleToACap() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), (0..6).map(::reconnectDelayMillis))
    }
}
