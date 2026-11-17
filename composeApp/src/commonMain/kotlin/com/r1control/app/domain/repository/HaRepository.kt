package com.r1control.app.domain.repository

import com.r1control.app.domain.model.ha.HaAddress
import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaConnectionState
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The live link to Home Assistant: its connection, every entity's state, and the services it can be asked to run. */
interface HaRepository {
    val connectionState: StateFlow<HaConnectionState>

    /** Empty until the first state arrives after [connect]; kept up to date until [disconnect]. */
    val entities: StateFlow<Map<HaEntityId, HaEntity>>

    /** Opens the link and keeps it open, reconnecting when it drops, until [disconnect]. A second call replaces the first. */
    fun connect(address: HaAddress)

    /** Closes the link and forgets the entities. */
    fun disconnect()

    /** Runs `<domain>.<service>`; [data] carries the entity and the service's fields. Throws [HaException]. */
    suspend fun callService(domain: String, service: String, data: Map<String, HaAttribute>)

    /**
     * The household's own dashboard as Home Assistant keeps it (its Lovelace configuration), as JSON
     * text. Throws [HaException]; the reply is never put in the exception.
     */
    suspend fun dashboardConfig(): String

    /**
     * Starts an HLS stream of the camera [entityId] and returns the playlist's path on the server
     * (`/api/hls/.../master.m3u8`), which the caller prefixes with the server address to play. Throws
     * [HaException] when the link is down or the camera cannot stream.
     */
    suspend fun cameraStreamPath(entityId: HaEntityId): String

    /**
     * Home Assistant renders the Jinja [template] and says so again whenever the result changes: the first
     * emission is the text now, each later one a change. Collecting opens the subscription and stopping
     * the collection closes it. The flow ends, without throwing, when the link is not open or goes down,
     * and when Home Assistant refuses the template; collect again once connected to resume. The text can
     * hold the household's own words: it is never logged.
     */
    fun renderedTemplate(template: String): Flow<String>
}
