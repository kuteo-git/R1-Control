package com.r1control.app.data.repository

import com.r1control.app.data.remote.HaMessage
import com.r1control.app.data.remote.HaSocket
import com.r1control.app.data.remote.HaSocketOpener
import com.r1control.app.data.remote.KtorHaSocketOpener
import com.r1control.app.data.remote.applying
import com.r1control.app.data.remote.authFrame
import com.r1control.app.data.remote.callServiceFrame
import com.r1control.app.data.remote.cameraStreamFrame
import com.r1control.app.data.remote.cameraStreamUrlFrom
import com.r1control.app.data.remote.lovelaceConfigFrame
import com.r1control.app.data.remote.parseHaMessage
import com.r1control.app.data.remote.pingFrame
import com.r1control.app.data.remote.renderTemplateFrame
import com.r1control.app.data.remote.subscribeEntitiesFrame
import com.r1control.app.data.remote.unsubscribeEventsFrame
import com.r1control.app.domain.model.ha.HaAddress
import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaConnectionState
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.repository.HaException
import com.r1control.app.domain.repository.HaRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** How long to wait before reconnect number [attempt] (0 is the first retry): 1 s doubling to 30 s. */
internal fun reconnectDelayMillis(attempt: Int): Long = minOf(30_000L, 1_000L shl minOf(attempt, 5))

internal const val HANDSHAKE_TIMEOUT_MS = 10_000L
internal const val PING_EVERY_MS = 20_000L

/** Home Assistant answers a ping at once; this long with no frame at all means the link is dead (a phone that changed wifi gets no close). */
internal const val SILENCE_LIMIT_MS = 60_000L
internal const val CALL_TIMEOUT_MS = 10_000L

/**
 * Home Assistant over its WebSocket API: `auth_required`, our token, `auth_ok`, then
 * `subscribe_entities`, whose first event is every entity and whose later ones are changes.
 * A dropped link is retried with a growing delay; a refused token is not, since the same
 * token would be refused again.
 */
class WebSocketHaRepository internal constructor(
    private val opener: HaSocketOpener,
    private val scope: CoroutineScope,
    private val retryDelayMillis: (attempt: Int) -> Long = ::reconnectDelayMillis,
) : HaRepository {

    constructor(client: HttpClient, scope: CoroutineScope) : this(KtorHaSocketOpener(client), scope)

    private val connection = MutableStateFlow(HaConnectionState.Disconnected)
    private val entityMap = MutableStateFlow<Map<HaEntityId, HaEntity>>(emptyMap())
    private val openLink = MutableStateFlow<Link?>(null)
    private var running: Job? = null
    private var runningFor: HaAddress? = null

    override val connectionState: StateFlow<HaConnectionState> = connection.asStateFlow()
    override val entities: StateFlow<Map<HaEntityId, HaEntity>> = entityMap.asStateFlow()

    /** Asking again for what is already running changes nothing, so a screen and the app shell can both ask. */
    override fun connect(address: HaAddress) {
        if (running?.isActive == true && runningFor == address) return
        running?.cancel()
        entityMap.value = emptyMap()
        connection.value = HaConnectionState.Connecting
        runningFor = address
        running = scope.launch { keepConnected(address) }
    }

    override fun disconnect() {
        running?.cancel()
        running = null
        runningFor = null
        openLink.value = null
        entityMap.value = emptyMap()
        connection.value = HaConnectionState.Disconnected
    }

    override suspend fun callService(domain: String, service: String, data: Map<String, HaAttribute>) {
        request { id -> callServiceFrame(id, domain, service, data) }
    }

    override suspend fun dashboardConfig(): String = request { id -> lovelaceConfigFrame(id) }.payload

    override suspend fun cameraStreamPath(entityId: HaEntityId): String =
        cameraStreamUrlFrom(request { id -> cameraStreamFrame(id, entityId.value) }.payload)

    /**
     * One `render_template` subscription per collection. Its events are routed to this collector by id
     * ([route]), so a slow collector never holds the read loop: the channel is unbounded and only ever
     * written with a non-blocking send.
     */
    override fun renderedTemplate(template: String): Flow<String> = flow {
        val link = openLink.value ?: return@flow
        val updates = Channel<String>(Channel.UNLIMITED)
        val id = try {
            link.subscribeTemplate(template, updates)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@flow
        }
        try {
            for (text in updates) emit(text)
        } finally {
            withContext(NonCancellable) { link.unsubscribeTemplate(id) }
        }
    }

    /** One command and its result; a refusal or a silence is a [HaException] that carries no reply text. */
    private suspend fun request(build: (id: Int) -> String): HaMessage.Result {
        val link = openLink.value ?: throw HaException.NotConnected()
        val result = try {
            withTimeout(CALL_TIMEOUT_MS) { link.call(build) }
        } catch (e: TimeoutCancellationException) {
            throw HaException.TimedOut()
        }
        if (!result.success) throw HaException.Refused(result.errorCode)
        return result
    }

    private suspend fun keepConnected(address: HaAddress) {
        var failures = 0
        while (true) {
            connection.value = HaConnectionState.Connecting
            var authenticated = false
            val end = try {
                var ended = SessionEnd.Closed
                opener.open(address) { socket -> ended = serve(socket, address.token) { authenticated = true } }
                ended
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SessionEnd.Closed
            }
            if (end == SessionEnd.Refused) {
                connection.value = HaConnectionState.AuthInvalid
                return
            }
            failures = if (authenticated) 0 else failures + 1
            connection.value = HaConnectionState.Unreachable
            delay(retryDelayMillis(maxOf(failures - 1, 0)))
        }
    }

    private enum class SessionEnd { Closed, Refused }

    private suspend fun serve(socket: HaSocket, token: String, onAuthenticated: () -> Unit): SessionEnd = coroutineScope {
        if (socket.nextMessage(HANDSHAKE_TIMEOUT_MS) != HaMessage.AuthRequired) return@coroutineScope SessionEnd.Closed
        socket.send(authFrame(token))
        when (socket.nextMessage(HANDSHAKE_TIMEOUT_MS)) {
            HaMessage.AuthOk -> Unit
            HaMessage.AuthInvalid -> return@coroutineScope SessionEnd.Refused
            else -> return@coroutineScope SessionEnd.Closed
        }
        onAuthenticated()

        val link = Link(socket)
        val subscription = link.send { id -> subscribeEntitiesFrame(id) }
        openLink.value = link
        connection.value = HaConnectionState.Connected
        val pinger = launch {
            while (true) {
                delay(PING_EVERY_MS)
                link.send { id -> pingFrame(id) }
            }
        }
        try {
            route(socket, link, subscription)
        } finally {
            pinger.cancel()
            openLink.value = null
            link.failAll()
        }
    }

    /**
     * Reads until the link goes quiet or closes, and hands each frame to whoever asked for it by id: the
     * entities subscription, a template subscription, or a command waiting for its result. A frame for an
     * id nobody holds is dropped.
     */
    private suspend fun route(socket: HaSocket, link: Link, subscription: Int): SessionEnd {
        // The first event after subscribing is the whole state and replaces what a
        // dropped link left behind; every later one is a change on top of it.
        var snapshot = true
        while (true) {
            val message = socket.nextMessage(SILENCE_LIMIT_MS) ?: return SessionEnd.Closed
            when (message) {
                is HaMessage.Entities -> if (message.id == subscription) {
                    entityMap.value = if (snapshot) message.delta.added else entityMap.value.applying(message.delta)
                    snapshot = false
                }
                is HaMessage.Result -> {
                    // Home Assistant older than 2022.4 has no subscribe_entities: nothing here could ever fill.
                    if (message.id == subscription && !message.success) return SessionEnd.Closed
                    link.answer(message)
                }
                is HaMessage.TemplateEvent -> link.deliver(message)
                else -> Unit
            }
        }
    }

    /** The next frame read as a message, or null on silence for [timeoutMillis] or a closed socket. */
    private suspend fun HaSocket.nextMessage(timeoutMillis: Long): HaMessage? =
        withTimeoutOrNull(timeoutMillis) { receive() }?.let(::parseHaMessage)

    /** The open socket with Home Assistant's message numbering: every command carries an id one higher than the last. */
    private class Link(private val socket: HaSocket) {
        private val lock = Mutex()
        private var lastId = 0
        private val waiting = mutableMapOf<Int, CompletableDeferred<HaMessage.Result>>()
        private val templates = mutableMapOf<Int, SendChannel<String>>()
        private var closed = false

        /** Sends the frame [build] makes for the next id, and returns that id. */
        suspend fun send(build: (id: Int) -> String): Int = lock.withLock {
            val id = ++lastId
            socket.send(build(id))
            id
        }

        /** Sends a command and waits for its result. */
        suspend fun call(build: (id: Int) -> String): HaMessage.Result {
            val reply = CompletableDeferred<HaMessage.Result>()
            val id = lock.withLock {
                val next = ++lastId
                waiting[next] = reply
                try {
                    socket.send(build(next))
                } catch (e: Throwable) {
                    waiting.remove(next)
                    throw e
                }
                next
            }
            try {
                return reply.await()
            } finally {
                withContext(NonCancellable) { lock.withLock { waiting.remove(id) } }
            }
        }

        /**
         * The reply to a command. A refused `render_template` is the whole answer to its subscription: there
         * will be no events, so its collector is let go.
         */
        suspend fun answer(result: HaMessage.Result) {
            val refused = lock.withLock {
                waiting.remove(result.id)?.complete(result)
                if (result.success) null else templates.remove(result.id)
            }
            refused?.close()
        }

        /** Registers [into] under a fresh id, then asks Home Assistant to render [template] for it; returns the id. */
        suspend fun subscribeTemplate(template: String, into: SendChannel<String>): Int = lock.withLock {
            if (closed) throw HaException.NotConnected()
            val id = ++lastId
            templates[id] = into
            try {
                socket.send(renderTemplateFrame(id, template))
            } catch (e: Throwable) {
                templates.remove(id)
                throw e
            }
            id
        }

        /** Forgets subscription [id] and tells Home Assistant to stop it, unless the link or the subscription is already gone. */
        suspend fun unsubscribeTemplate(id: Int) {
            val live = lock.withLock { templates.remove(id) != null && !closed }
            if (!live) return
            try {
                send { next -> unsubscribeEventsFrame(next, id) }
            } catch (_: Exception) {
                // The link went down meanwhile; Home Assistant drops the subscription with it.
            }
        }

        /** One rendering for the collector that holds the subscription. Never waits for it. */
        suspend fun deliver(event: HaMessage.TemplateEvent) {
            lock.withLock { templates[event.id] }?.trySend(event.rendered)
        }

        /** The link is gone: every caller still waiting is told so, and every template collector's flow ends. */
        suspend fun failAll() {
            val (stranded, subscribed) = withContext(NonCancellable) {
                lock.withLock {
                    closed = true
                    Pair(waiting.values.toList(), templates.values.toList()).also {
                        waiting.clear()
                        templates.clear()
                    }
                }
            }
            stranded.forEach { it.completeExceptionally(HaException.NotConnected()) }
            subscribed.forEach { it.close() }
        }
    }
}
