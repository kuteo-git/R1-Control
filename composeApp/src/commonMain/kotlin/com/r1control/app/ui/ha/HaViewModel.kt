package com.r1control.app.ui.ha

import com.r1control.app.domain.model.ha.HaAddress
import com.r1control.app.domain.model.ha.HaConnectionState
import com.r1control.app.domain.model.ha.HaCoverCommand
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaServiceCall
import com.r1control.app.domain.model.ha.HaTile
import com.r1control.app.domain.model.ha.HaCamera
import com.r1control.app.domain.model.ha.HaDashboard
import com.r1control.app.domain.model.ha.HaTabs
import com.r1control.app.domain.model.ha.SavedHaAddress
import com.r1control.app.domain.model.ha.SectionId
import com.r1control.app.domain.model.ha.TabId
import com.r1control.app.domain.model.ha.TileId
import com.r1control.app.domain.model.ha.addSection
import com.r1control.app.domain.model.ha.addTab
import com.r1control.app.domain.model.ha.addTile
import com.r1control.app.domain.model.ha.deleteTab
import com.r1control.app.domain.model.ha.moveTab
import com.r1control.app.domain.model.ha.nextTabId
import com.r1control.app.domain.model.ha.onCurrent
import com.r1control.app.domain.model.ha.renameTab
import com.r1control.app.domain.model.ha.selectTab
import com.r1control.app.domain.model.ha.setTabIcon
import com.r1control.app.domain.model.ha.HaRgb
import com.r1control.app.domain.model.ha.brightnessCall
import com.r1control.app.domain.model.ha.colorCall
import com.r1control.app.domain.model.ha.fanSpeedCall
import com.r1control.app.domain.model.ha.oscillateCall
import com.r1control.app.domain.model.ha.powerToggleCall
import com.r1control.app.domain.model.ha.presetCall
import com.r1control.app.domain.model.ha.whiteCall
import com.r1control.app.domain.model.ha.call
import com.r1control.app.domain.model.ha.defaultTile
import com.r1control.app.domain.model.ha.deleteSection
import com.r1control.app.domain.model.ha.deleteTile
import com.r1control.app.domain.model.ha.moveRow
import com.r1control.app.domain.model.ha.moveSection
import com.r1control.app.domain.model.ha.moveTile
import com.r1control.app.domain.model.ha.nextSectionId
import com.r1control.app.domain.model.ha.nextTileId
import com.r1control.app.domain.model.ha.renameSection
import com.r1control.app.domain.model.ha.renameTile
import com.r1control.app.domain.model.ha.nextCall
import com.r1control.app.domain.model.ha.playPauseCall
import com.r1control.app.domain.model.ha.seekCall
import com.r1control.app.domain.model.ha.stopCall
import com.r1control.app.domain.model.ha.volumeCall
import com.r1control.app.domain.model.ha.sortedForList
import com.r1control.app.domain.model.ha.tapCall
import com.r1control.app.domain.repository.HaException
import com.r1control.app.domain.repository.HaLayoutRepository
import com.r1control.app.domain.repository.HaRepository
import com.r1control.app.domain.repository.HaSettingsRepository
import com.r1control.app.domain.usecase.Foreground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Home Assistant tab shows. */
sealed interface HaState {
    data object Loading : HaState

    /** Nothing saved yet: the tab is the connection form. */
    data object NeedsSetup : HaState

    /** The saved token was refused: the tab is the connection form again. */
    data object TokenRefused : HaState

    /**
     * [entities] are in list order. [tabs] is the person's own layout: null until it is known (read from the
     * phone, or seeded once from Home Assistant's), and it stays null when there was none and Home Assistant's
     * could not be read ([dashboardFailed]); the screen then falls back to the list of entities. [dashboard]
     * is the open tab's, the body under the tab bar. [editing] is only ever true with [tabs] to edit.
     */
    data class Live(
        val connection: HaConnectionState,
        val entities: List<HaEntity>,
        val tabs: HaTabs?,
        val dashboardFailed: Boolean,
        val editing: Boolean,
    ) : HaState {
        /** The open tab's dashboard, or null when the layout is unknown or empty of tabs. */
        val dashboard: HaDashboard? get() = tabs?.current?.dashboard
    }
}

/**
 * Where the layout stands. It is read from the phone when there is one. With none, the first connection seeds it
 * from Home Assistant's dashboard, and a failed seed is tried again on the next connection. Once it is [Loaded]
 * Home Assistant is never read for it again.
 */
sealed interface HaDashboardLoad {
    data object Pending : HaDashboardLoad
    data object Failed : HaDashboardLoad
    data class Loaded(val tabs: HaTabs) : HaDashboardLoad
}

/** What is known of the saved connection: not read yet, or read. */
sealed interface HaStored {
    data object Loading : HaStored
    data class Known(val saved: SavedHaAddress) : HaStored
}

/** What the top bar's edit button is: absent without a layout to edit, a pencil to start, a tick to finish. */
enum class HaEditButton { Hidden, Edit, Done }

/** Where a manual re-import of the dashboard from Home Assistant stands, shown in Home Assistant's settings. */
enum class HaResync { Idle, Running, Done, Failed }

fun editButtonOf(state: HaState): HaEditButton = when {
    state !is HaState.Live || state.tabs == null -> HaEditButton.Hidden
    state.editing -> HaEditButton.Done
    else -> HaEditButton.Edit
}

/** The saved address as `host:port`, or empty when nothing is saved yet. */
fun addressOf(stored: HaStored): String = when (stored) {
    HaStored.Loading -> ""
    is HaStored.Known -> when (val saved = stored.saved) {
        SavedHaAddress.Unset -> ""
        is SavedHaAddress.Set -> saved.address.hostAndPort
    }
}

/** The tab's state, decided once from the five things that make it. */
fun haStateOf(
    stored: HaStored,
    connection: HaConnectionState,
    entities: Map<HaEntityId, HaEntity>,
    dashboard: HaDashboardLoad,
    editing: Boolean,
): HaState = when (stored) {
    HaStored.Loading -> HaState.Loading
    is HaStored.Known -> when (stored.saved) {
        SavedHaAddress.Unset -> HaState.NeedsSetup
        is SavedHaAddress.Set ->
            if (connection == HaConnectionState.AuthInvalid) HaState.TokenRefused
            else HaState.Live(
                connection,
                entities.values.toList().sortedForList(),
                (dashboard as? HaDashboardLoad.Loaded)?.tabs,
                dashboard == HaDashboardLoad.Failed,
                editing && dashboard is HaDashboardLoad.Loaded,
            )
    }
}

/** Whether the link should be open: only with a saved address and while the app is on screen. */
private sealed interface Want {
    data object Closed : Want
    data class Open(val address: HaAddress) : Want
}

/**
 * Owns the Home Assistant link's lifetime: open while the app is on screen and a connection is
 * saved, closed otherwise (a locked phone keeps no socket, and the first frame after the screen
 * comes back is the whole state again). It also owns the dashboard layout, which is the person's:
 * read from [layout], seeded once from Home Assistant through [importDashboard] (a function, so `ui`
 * never names the wire) when there is none, and changed only by the edit intents below, each of which
 * is saved at once. A tap on a tile is a service call whose failure is dropped, never thrown at the
 * screen; while editing a tap does not call anything.
 */
class HaViewModel(
    private val settings: HaSettingsRepository,
    private val ha: HaRepository,
    foreground: Foreground,
    private val scope: CoroutineScope,
    private val layout: HaLayoutRepository,
    private val importDashboard: (configJson: String) -> HaDashboard,
) {
    private val stored = MutableStateFlow<HaStored>(HaStored.Loading)
    private val dashboard = MutableStateFlow<HaDashboardLoad>(HaDashboardLoad.Pending)
    private val editing = MutableStateFlow(false)

    /** The newest layout not yet written. A write that finds a newer one waiting takes that instead, so the saved layout converges on the last edit. */
    private val unsaved = MutableStateFlow<HaTabs?>(null)

    private val resyncState = MutableStateFlow(HaResync.Idle)

    private val controls = MutableStateFlow(HaEntityId.None)

    /** The entity whose controls sheet is open, [HaEntityId.None] when none is. It never names an entity Home Assistant no longer lists. */
    val controlTarget: StateFlow<HaEntityId> = controls.asStateFlow()

    /** Where a manual re-import from Home Assistant stands, for Home Assistant's settings to show. */
    val resync: StateFlow<HaResync> = resyncState.asStateFlow()

    private val expanded = MutableStateFlow<HaCamera?>(null)

    /** The camera filling the screen, or null. It is drawn over the whole app, so it lives above the tab. */
    val expandedCamera: StateFlow<HaCamera?> = expanded.asStateFlow()

    fun openCamera(camera: HaCamera) {
        expanded.value = camera
    }

    fun closeCamera() {
        expanded.value = null
    }

    /** The server path of a live HLS stream of [entity], or empty when the link is down or it cannot stream. */
    suspend fun cameraStreamPath(entity: HaEntityId): String = try {
        ha.cameraStreamPath(entity)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        ""
    }

    val state: StateFlow<HaState> =
        combine(stored, ha.connectionState, ha.entities, dashboard, editing, ::haStateOf)
            .stateIn(scope, SharingStarted.Eagerly, HaState.Loading)

    val editButton: StateFlow<HaEditButton> =
        state.map(::editButtonOf).distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, HaEditButton.Hidden)

    /** The saved Home Assistant address as `host:port`, for the tab's subtitle; empty until one is saved. */
    val address: StateFlow<String> =
        stored.map { addressOf(it) }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, "")

    init {
        scope.launch {
            stored.value = HaStored.Known(
                try {
                    settings.saved()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    SavedHaAddress.Unset
                },
            )
        }
        scope.launch {
            val saved = try {
                layout.load()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (saved != null) {
                dashboard.value = HaDashboardLoad.Loaded(saved)
            } else {
                ha.connectionState.collectLatest { connection ->
                    if (connection == HaConnectionState.Connected && dashboard.value !is HaDashboardLoad.Loaded) seed()
                }
            }
        }
        scope.launch {
            combine(controls, ha.entities) { target, known -> target to known }.collect { (target, known) ->
                if (!target.isNone && target !in known) controls.value = HaEntityId.None
            }
        }
        scope.launch {
            unsaved.filterNotNull().collect { next ->
                try {
                    layout.save(next)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The layout on screen is kept; the next edit writes again.
                }
            }
        }
        scope.launch {
            combine(stored, foreground.isShown) { known, shown ->
                val saved = (known as? HaStored.Known)?.saved
                if (shown && saved is SavedHaAddress.Set) Want.Open(saved.address) else Want.Closed
            }.distinctUntilChanged().collect { want ->
                when (want) {
                    Want.Closed -> ha.disconnect()
                    is Want.Open -> ha.connect(want.address)
                }
            }
        }
    }

    /** A tap on a toggle or an action tile. A readout, a cover and a player have no tap of their own. */
    fun onTile(tile: HaTile) = send(tile.tapCall())

    fun openControls(id: HaEntityId) {
        if (!editing.value) controls.value = id
    }

    fun closeControls() {
        controls.value = HaEntityId.None
    }

    fun togglePower(id: HaEntityId) = send(powerToggleCall(id))

    /** Committed when a drag ends, so a drag is one call and not a flood. */
    fun setBrightness(id: HaEntityId, percent: Int) = send(brightnessCall(id, percent))

    fun setFanSpeed(id: HaEntityId, percent: Int) = send(fanSpeedCall(id, percent))

    fun setOscillate(id: HaEntityId, on: Boolean) = send(oscillateCall(id, on))

    fun setPreset(id: HaEntityId, mode: String) = send(presetCall(id, mode))

    fun setColor(id: HaEntityId, rgb: HaRgb) = send(colorCall(id, rgb))

    fun setWhite(id: HaEntityId, kelvin: Int) = send(whiteCall(id, kelvin))

    fun onCover(tile: HaTile.Cover, command: HaCoverCommand) = send(tile.call(command))

    fun onMedia(tile: HaTile.Media) = send(tile.playPauseCall())

    fun onMediaNext(tile: HaTile.Media) = send(tile.nextCall())

    fun onMediaStop(tile: HaTile.Media) = send(tile.stopCall())

    fun onMediaSeek(tile: HaTile.Media, positionS: Int) = send(tile.seekCall(positionS))

    fun onMediaVolume(tile: HaTile.Media, level: Float) = send(tile.volumeCall(level))

    /** Home Assistant's rendering of a template tile's text, live while collected. Never collected in the edit mode: the list there shows no rendered text. */
    fun renderedTemplate(template: String): Flow<String> = ha.renderedTemplate(template)

    private fun send(call: HaServiceCall?) {
        if (call == null || editing.value) return
        scope.launch {
            try {
                ha.callService(call.domain, call.service, call.data)
            } catch (e: CancellationException) {
                throw e
            } catch (_: HaException) {
                // Home Assistant keeps the real state: a tile that did not change says so by staying as it was.
            }
        }
    }

    // ---- the layout ----

    /** Home Assistant's own dashboard, read once to give a first layout: one tab holding it. */
    private suspend fun seed() {
        dashboard.value = try {
            HaDashboardLoad.Loaded(HaTabs.of(importDashboard(ha.dashboardConfig()))).also { unsaved.value = it.tabs }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            HaDashboardLoad.Failed
        }
    }

    /**
     * Reads Home Assistant's dashboard again and replaces the saved layout with it. The person asks for
     * this in Home Assistant's settings, warned that their own tiles, sections and order are lost. Unlike
     * the one-off [seed], it runs over whatever is already saved.
     */
    fun resyncFromHa() {
        if (resyncState.value == HaResync.Running) return
        scope.launch {
            resyncState.value = HaResync.Running
            resyncState.value = try {
                val fresh = HaTabs.of(importDashboard(ha.dashboardConfig()))
                editing.value = false
                dashboard.value = HaDashboardLoad.Loaded(fresh)
                unsaved.value = fresh
                HaResync.Done
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                HaResync.Failed
            }
        }
    }

    /** For a household whose Home Assistant has no dashboard to seed from: one empty tab to build. */
    fun startEmpty() {
        if (dashboard.value is HaDashboardLoad.Loaded) return
        val empty = HaTabs.of(HaDashboard.Empty)
        dashboard.value = HaDashboardLoad.Loaded(empty)
        unsaved.value = empty
        editing.value = true
    }

    fun toggleEditing() {
        if (editing.value) editing.value = false else if (dashboard.value is HaDashboardLoad.Loaded) editing.value = true
    }

    fun addSection(title: String) = edit { addSection(nextSectionId(), title.trim()) }

    fun renameSection(id: SectionId, title: String) = edit { renameSection(id, title.trim()) }

    fun deleteSection(id: SectionId) = edit { deleteSection(id) }

    fun moveSection(from: Int, to: Int) = edit { moveSection(from, to) }

    /** The new tile is what the entity's domain says it is, named by the entity's own live name until it is renamed. */
    fun addTile(sectionId: SectionId, entity: HaEntityId) = edit { addTile(sectionId, defaultTile(nextTileId(), entity)) }

    fun renameTile(id: TileId, name: String) = edit { renameTile(id, name.trim()) }

    fun deleteTile(id: TileId) = edit { deleteTile(id) }

    fun moveTile(id: TileId, toSection: SectionId, toIndex: Int) = edit { moveTile(id, toSection, toIndex) }

    /** A drag in the edit list: [from] and [to] are rows of `editRows`. */
    fun moveRow(from: Int, to: Int) = edit { moveRow(from, to) }

    // ---- the tabs ----

    /** Opens a tab. A view action, not an edit, so it works without the edit mode. */
    fun selectTab(id: TabId) = editTabs { selectTab(id) }

    /** Adds a new, empty, selected tab. */
    fun addTab(name: String, icon: String) = editTabs { addTab(nextTabId(), name.trim(), icon) }

    fun renameTab(id: TabId, name: String) = editTabs { renameTab(id, name.trim()) }

    fun setTabIcon(id: TabId, icon: String) = editTabs { setTabIcon(id, icon) }

    /** Deletes a tab and its whole dashboard. The caller guards the last tab; the domain allows removing it. */
    fun deleteTab(id: TabId) = editTabs { deleteTab(id) }

    fun moveTab(from: Int, to: Int) = editTabs { moveTab(from, to) }

    /** A per-tab layout edit, applied to the open tab's dashboard. */
    private fun edit(change: HaDashboard.() -> HaDashboard) = editTabs { onCurrent { it.change() } }

    /** A tab-level edit: which tabs there are, and which is open. The one place a change is saved. */
    private fun editTabs(change: HaTabs.() -> HaTabs) {
        val loaded = dashboard.value as? HaDashboardLoad.Loaded ?: return
        val next = loaded.tabs.change()
        if (next == loaded.tabs) return
        dashboard.update { HaDashboardLoad.Loaded(next) }
        unsaved.value = next
    }

    /** The form's try was accepted and saved. */
    fun signedIn(address: HaAddress) {
        stored.value = HaStored.Known(SavedHaAddress.Set(address))
    }
}
