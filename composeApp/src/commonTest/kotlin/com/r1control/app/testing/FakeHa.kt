package com.r1control.app.testing

import com.r1control.app.domain.model.ha.HaAddress
import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaConnectionState
import com.r1control.app.domain.model.ha.HaTabs
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.SavedHaAddress
import com.r1control.app.domain.repository.HaException
import com.r1control.app.domain.repository.HaLayoutRepository
import com.r1control.app.domain.repository.HaRepository
import com.r1control.app.domain.repository.HaSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A Home Assistant link a test steers: [answer] is what the state becomes once a [connect] is made. */
class FakeHa : HaRepository {
    val state = MutableStateFlow(HaConnectionState.Disconnected)
    val map = MutableStateFlow<Map<HaEntityId, HaEntity>>(emptyMap())
    val connects = mutableListOf<HaAddress>()
    var disconnects = 0
    var answer: HaConnectionState = HaConnectionState.Connected

    /** Every service called, in order, and what makes the next call fail. */
    val calls = mutableListOf<Triple<String, String, Map<String, HaAttribute>>>()
    var callFailure: HaException? = null

    /** What [dashboardConfig] answers, and what makes it fail instead. */
    var config = """{"views":[]}"""
    var configFailure: Exception? = null

    /** What a template renders as: push to [renderings] under the template's text. [rendersAsked] lists every subscription made, in order. */
    val renderings = mutableMapOf<String, MutableSharedFlow<String>>()
    val rendersAsked = mutableListOf<String>()

    override val connectionState: StateFlow<HaConnectionState> = state
    override val entities: StateFlow<Map<HaEntityId, HaEntity>> = map

    override fun connect(address: HaAddress) {
        connects += address
        state.value = HaConnectionState.Connecting
        state.value = answer
    }

    override fun disconnect() {
        disconnects++
        state.value = HaConnectionState.Disconnected
    }

    override suspend fun callService(domain: String, service: String, data: Map<String, HaAttribute>) {
        calls += Triple(domain, service, data)
        callFailure?.let { throw it }
    }

    override fun renderedTemplate(template: String): Flow<String> {
        rendersAsked += template
        return renderings.getOrPut(template) { MutableSharedFlow(replay = 1) }
    }

    override suspend fun dashboardConfig(): String {
        configFailure?.let { throw it }
        return config
    }
}

class MemoryHaSettings(var saved: SavedHaAddress = SavedHaAddress.Unset) : HaSettingsRepository {
    override suspend fun saved(): SavedHaAddress = saved
    override suspend fun save(address: HaAddress) {
        saved = SavedHaAddress.Set(address)
    }
}

/** The layout kept in memory. [saves] counts every write, so a test can tell a layout that was kept from one that was rewritten. */
class MemoryHaLayout(var saved: HaTabs? = null) : HaLayoutRepository {
    var saves = 0
    override suspend fun load(): HaTabs? = saved
    override suspend fun save(tabs: HaTabs) {
        saves++
        saved = tabs
    }
}
