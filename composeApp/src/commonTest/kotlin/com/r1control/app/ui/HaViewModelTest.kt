package com.r1control.app.ui

import com.r1control.app.domain.model.ha.HaAddress
import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaConnectionState
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaCoverCommand
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaServiceCall
import com.r1control.app.domain.model.ha.HaTile
import com.r1control.app.domain.model.ha.TileId
import com.r1control.app.domain.model.ha.HaDashboard
import com.r1control.app.domain.model.ha.HaTabs
import com.r1control.app.domain.model.ha.HaSection
import com.r1control.app.domain.model.ha.SectionId
import com.r1control.app.domain.model.ha.isUnavailableWith
import com.r1control.app.domain.model.ha.HaInput
import com.r1control.app.domain.model.ha.SavedHaAddress
import com.r1control.app.data.remote.importDashboard
import com.r1control.app.domain.repository.HaException
import com.r1control.app.domain.usecase.ConnectHa
import com.r1control.app.domain.usecase.ConnectHaOutcome
import com.r1control.app.domain.usecase.Foreground
import com.r1control.app.testing.FakeHa
import com.r1control.app.testing.MemoryHaLayout
import com.r1control.app.testing.MemoryHaSettings
import com.r1control.app.ui.ha.HaFormStatus
import com.r1control.app.ui.ha.HaSettingsViewModel
import com.r1control.app.ui.ha.HaDashboardLoad
import com.r1control.app.ui.ha.HaEditButton
import com.r1control.app.ui.ha.addressOf
import com.r1control.app.ui.ha.editButtonOf
import com.r1control.app.ui.ha.HaResync
import com.r1control.app.ui.ha.HaState
import com.r1control.app.ui.ha.HaStored
import com.r1control.app.ui.ha.HaViewModel
import com.r1control.app.ui.ha.haStateOf
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val ADDRESS = HaAddress("ha.local", 8123, "tok")

private fun entity(id: String, name: String) =
    HaEntity(HaEntityId(id), "on", mapOf("friendly_name" to HaAttribute.Text(name)))

class HaStateTest {
    private val two = listOf(entity("switch.b", "B"), entity("light.a", "A")).associateBy { it.id }

    @Test fun nothingIsDecidedUntilTheSavedConnectionIsRead() {
        assertEquals(HaState.Loading, haStateOf(HaStored.Loading, HaConnectionState.Connected, two, HaDashboardLoad.Pending, false))
    }

    @Test fun nothingSavedIsTheForm() {
        assertEquals(HaState.NeedsSetup, haStateOf(HaStored.Known(SavedHaAddress.Unset), HaConnectionState.Disconnected, emptyMap(), HaDashboardLoad.Pending, false))
    }

    @Test fun aSavedConnectionShowsTheLiveListInListOrder() {
        val live = assertIs<HaState.Live>(haStateOf(HaStored.Known(SavedHaAddress.Set(ADDRESS)), HaConnectionState.Connected, two, HaDashboardLoad.Pending, false))
        assertEquals(HaConnectionState.Connected, live.connection)
        assertEquals(listOf("light.a", "switch.b"), live.entities.map { it.id.value })
    }

    @Test fun aRefusedTokenIsTheFormAgain() {
        assertEquals(HaState.TokenRefused, haStateOf(HaStored.Known(SavedHaAddress.Set(ADDRESS)), HaConnectionState.AuthInvalid, emptyMap(), HaDashboardLoad.Pending, false))
    }

    @Test fun anUnreachableServerKeepsTheListAndSaysSo() {
        val live = assertIs<HaState.Live>(haStateOf(HaStored.Known(SavedHaAddress.Set(ADDRESS)), HaConnectionState.Unreachable, two, HaDashboardLoad.Pending, false))
        assertEquals(HaConnectionState.Unreachable, live.connection)
        assertEquals(2, live.entities.size)
    }

    @Test fun theSubtitleAddressIsTheSavedHostAndPortOrEmpty() {
        assertEquals("", addressOf(HaStored.Loading))
        assertEquals("", addressOf(HaStored.Known(SavedHaAddress.Unset)))
        assertEquals("ha.local:8123", addressOf(HaStored.Known(SavedHaAddress.Set(ADDRESS))))
    }
}

private fun on(id: String): Map<String, HaAttribute> = mapOf("entity_id" to HaAttribute.Text(id))

private const val KITCHEN_CONFIG =
    """{"views":[{"title":"Home","cards":[{"type":"vertical-stack","cards":[""" +
        """{"type":"custom:mushroom-title-card","title":"Kitchen"},""" +
        """{"type":"custom:mushroom-entity-card","entity":"light.kitchen","name":"Ceiling"}]}]}]}"""

class HaViewModelTest {
    private val ha = FakeHa()
    private val foreground = Foreground()

    private fun TestScope.model(saved: SavedHaAddress): HaViewModel {
        val vm = HaViewModel(MemoryHaSettings(saved), ha, foreground, backgroundScope, MemoryHaLayout(), ::importDashboard)
        runCurrent()
        return vm
    }

    @Test fun aSavedConnectionOpensTheLinkWhenTheAppIsOnScreen() = runTest {
        val vm = model(SavedHaAddress.Set(ADDRESS))
        assertEquals(listOf(ADDRESS), ha.connects)
        assertIs<HaState.Live>(vm.state.value)
    }

    @Test fun nothingSavedOpensNothing() = runTest {
        val vm = model(SavedHaAddress.Unset)
        assertEquals(emptyList(), ha.connects)
        assertEquals(HaState.NeedsSetup, vm.state.value)
    }

    @Test fun theLinkClosesInTheBackgroundAndOpensAgainOnReturn() = runTest {
        model(SavedHaAddress.Set(ADDRESS))
        foreground.set(false)
        runCurrent()
        assertEquals(HaConnectionState.Disconnected, ha.state.value)
        foreground.set(true)
        runCurrent()
        assertEquals(listOf(ADDRESS, ADDRESS), ha.connects)
        assertEquals(HaConnectionState.Connected, ha.state.value)
    }

    @Test fun signingInOpensTheLinkForTheNewAddress() = runTest {
        val vm = model(SavedHaAddress.Unset)
        vm.signedIn(ADDRESS)
        runCurrent()
        assertEquals(listOf(ADDRESS), ha.connects)
        assertIs<HaState.Live>(vm.state.value)
    }
}

class HaDashboardViewModelTest {
    private val ha = FakeHa().also { it.config = KITCHEN_CONFIG }
    private val foreground = Foreground()
    private val layout = MemoryHaLayout()

    private fun TestScope.model(): HaViewModel {
        val vm = HaViewModel(MemoryHaSettings(SavedHaAddress.Set(ADDRESS)), ha, foreground, backgroundScope, layout, ::importDashboard)
        runCurrent()
        return vm
    }

    private fun HaViewModel.live() = assertIs<HaState.Live>(state.value)

    @Test fun theDashboardIsSeededOnceTheLinkIsConnected() = runTest {
        val live = model().live()
        val dashboard = assertNotNull(live.dashboard)
        assertEquals(listOf("Kitchen"), dashboard.sections.map { it.title })
        assertEquals(listOf(HaEntityId("light.kitchen")), dashboard.sections.single().tiles.map { it.entity })
        assertFalse(live.dashboardFailed)
    }

    @Test fun theFirstSeedIsSavedOnThePhone() = runTest {
        val live = model().live()
        assertEquals(live.dashboard, layout.savedDashboard())
    }

    @Test fun nothingIsReadWhileTheLinkIsNotUp() = runTest {
        ha.answer = HaConnectionState.Unreachable
        val live = model().live()
        assertNull(live.dashboard)
        assertFalse(live.dashboardFailed)
    }

    @Test fun aFailedReadLeavesNoDashboardAndTheTabStillStands() = runTest {
        ha.configFailure = HaException.Refused("config_not_found")
        val live = model().live()
        assertNull(live.dashboard)
        assertTrue(live.dashboardFailed)
    }

    @Test fun aConfigurationThatIsNotJsonIsAFailedReadToo() = runTest {
        ha.config = "not json"
        assertTrue(model().live().dashboardFailed)
    }

    @Test fun aFreshConnectionDoesNotReadTheDashboardAgain() = runTest {
        val vm = model()
        ha.config = KITCHEN_CONFIG.replace("Kitchen", "Hall")
        foreground.set(false)
        runCurrent()
        foreground.set(true)
        runCurrent()
        assertEquals(listOf("Kitchen"), assertNotNull(vm.live().dashboard).sections.map { it.title })
        assertEquals(1, layout.saves)
    }

    @Test fun aFailedSeedIsTriedAgainOnTheNextConnection() = runTest {
        ha.configFailure = HaException.Refused("config_not_found")
        val vm = model()
        assertTrue(vm.live().dashboardFailed)
        ha.configFailure = null
        foreground.set(false)
        runCurrent()
        foreground.set(true)
        runCurrent()
        assertEquals(listOf("Kitchen"), assertNotNull(vm.live().dashboard).sections.map { it.title })
        assertEquals(1, layout.saves)
    }

    @Test fun withNothingToSeedFromThePersonBuildsAnEmptyLayout() = runTest {
        ha.configFailure = HaException.Refused("config_not_found")
        val vm = model()
        vm.startEmpty()
        runCurrent()
        val live = vm.live()
        assertEquals(HaDashboard.Empty, live.dashboard)
        assertTrue(live.editing)
        assertEquals(HaDashboard.Empty, layout.savedDashboard())
    }

    @Test fun theDashboardStaysWhileTheLinkIsDown() = runTest {
        val vm = model()
        foreground.set(false)
        runCurrent()
        assertNotNull(vm.live().dashboard)
    }

    private fun tile(vm: HaViewModel): HaTile = assertNotNull(vm.live().dashboard).sections.single().tiles.single()

    @Test fun aToggleTapCallsTheDomainsToggleOnItsEntity() = runTest {
        val vm = model()
        vm.onTile(tile(vm))
        runCurrent()
        assertEquals(listOf(Triple("light", "toggle", on("light.kitchen"))), ha.calls)
    }

    @Test fun anActionTapCallsItsOwnService() = runTest {
        val vm = model()
        val call = HaServiceCall("script", "daikin_turn_off", on("script.daikin_turn_off"))
        vm.onTile(HaTile.Action(TileId("t9"), "Off", "", HaEntityId("script.daikin_turn_off"), call))
        runCurrent()
        assertEquals(listOf(Triple("script", "daikin_turn_off", call.data)), ha.calls)
    }

    @Test fun aTemplateTapCallsItsServiceAndANoOpTemplateCallsNothing() = runTest {
        val vm = model()
        val next = HaServiceCall("pyscript", "pytube_next_song", on("media_player.kitchen_audio"))
        vm.onTile(HaTile.Template(TileId("t9"), "", "", HaEntityId.None, template = "Tiếp", call = next))
        vm.onTile(HaTile.Template(TileId("t8"), "", "", HaEntityId.None, template = "x", call = HaServiceCall.None))
        runCurrent()
        assertEquals(listOf(Triple("pyscript", "pytube_next_song", next.data)), ha.calls)
    }

    @Test fun aTemplatesRenderingComesFromTheRepositoryAndOnlyWhileCollected() = runTest {
        val vm = model()
        assertEquals(emptyList(), ha.rendersAsked)
        val seen = mutableListOf<String>()
        val job = backgroundScope.launch { vm.renderedTemplate("{{ x }}").collect { seen += it } }
        runCurrent()
        ha.renderings.getValue("{{ x }}").emit("one")
        ha.renderings.getValue("{{ x }}").emit("two")
        runCurrent()
        assertEquals(listOf("one", "two"), seen)
        assertEquals(listOf("{{ x }}"), ha.rendersAsked)
        job.cancel()
    }

    @Test fun aReadoutIsNotPressed() = runTest {
        val vm = model()
        vm.onTile(HaTile.Readout(TileId("t9"), "", "", HaEntityId("sensor.temp")))
        runCurrent()
        assertEquals(0, ha.calls.size)
    }

    @Test fun aCoverAndAPlayerRunTheirOwnServices() = runTest {
        val vm = model()
        vm.onCover(HaTile.Cover(TileId("t1"), "", "", HaEntityId("cover.blind")), HaCoverCommand.Stop)
        vm.onMedia(HaTile.Media(TileId("t2"), "", "", HaEntityId("media_player.kitchen")))
        runCurrent()
        assertEquals(
            listOf(
                Triple("cover", "stop_cover", on("cover.blind")),
                Triple("media_player", "media_play_pause", on("media_player.kitchen")),
            ),
            ha.calls,
        )
    }

    @Test fun aRefusedCallIsDroppedAndTheNextOneStillGoes() = runTest {
        val vm = model()
        ha.callFailure = HaException.TimedOut()
        vm.onTile(tile(vm))
        runCurrent()
        ha.callFailure = null
        vm.onTile(tile(vm))
        runCurrent()
        assertEquals(2, ha.calls.size)
        assertIs<HaState.Live>(vm.state.value)
    }
}

private val SAVED_LAYOUT = HaDashboard(
    listOf(
        HaSection(
            SectionId("s0"), "Mine",
            listOf(
                HaTile.Toggle(TileId("t0"), "Lamp", "", HaEntityId("light.lamp")),
                HaTile.Readout(TileId("t1"), "", "", HaEntityId("sensor.gone")),
            ),
        ),
    ),
)

/** The saved layout as the one tab it lives in on the phone. */
private val SAVED_TABS = HaTabs.of(SAVED_LAYOUT)

/** The open tab's dashboard, for a test that compares what was saved against the dashboard on screen. */
private fun MemoryHaLayout.savedDashboard(): HaDashboard? = saved?.current?.dashboard

/** The layout is the person's: read from the phone, seeded from Home Assistant only when there is none. */
class HaLayoutReconcileTest {
    private val ha = FakeHa().also { it.config = KITCHEN_CONFIG }
    private val foreground = Foreground()

    private fun TestScope.model(layout: MemoryHaLayout): HaViewModel {
        val vm = HaViewModel(MemoryHaSettings(SavedHaAddress.Set(ADDRESS)), ha, foreground, backgroundScope, layout, ::importDashboard)
        runCurrent()
        return vm
    }

    private fun HaViewModel.live() = assertIs<HaState.Live>(state.value)

    @Test fun aSavedLayoutBeatsHomeAssistantsDashboard() = runTest {
        val layout = MemoryHaLayout(SAVED_TABS)
        val vm = model(layout)
        assertEquals(SAVED_LAYOUT, vm.live().dashboard)
        assertEquals(0, layout.saves)
    }

    @Test fun aSavedLayoutIsNeverReadFromHomeAssistantEvenOnAFreshConnection() = runTest {
        val layout = MemoryHaLayout(SAVED_TABS)
        val vm = model(layout)
        foreground.set(false)
        runCurrent()
        foreground.set(true)
        runCurrent()
        assertEquals(SAVED_LAYOUT, vm.live().dashboard)
        assertEquals(0, layout.saves)
    }

    @Test fun withNoSavedLayoutTheFirstConnectionSeedsAndSavesIt() = runTest {
        val layout = MemoryHaLayout()
        val vm = model(layout)
        assertEquals(listOf("Kitchen"), assertNotNull(vm.live().dashboard).sections.map { it.title })
        assertEquals(vm.live().dashboard, layout.savedDashboard())
    }

    @Test fun aSavedLayoutShowsWithTheLinkDownAndNothingElseToGoOn() = runTest {
        ha.answer = HaConnectionState.Unreachable
        val live = model(MemoryHaLayout(SAVED_TABS)).live()
        assertEquals(SAVED_LAYOUT, live.dashboard)
        assertEquals(emptyList(), live.entities)
    }

    @Test fun anEntityHomeAssistantDoesNotKnowKeepsItsTileAndIsUnavailable() = runTest {
        ha.map.value = mapOf(HaEntityId("light.lamp") to entity("light.lamp", "Lamp"))
        val live = model(MemoryHaLayout(SAVED_TABS)).live()
        val tiles = assertNotNull(live.dashboard).sections.single().tiles
        val byId = live.entities.associateBy { it.id }
        assertEquals(listOf(false, true), tiles.map { it.isUnavailableWith(byId[it.entity]) })
    }

    @Test fun nothingIsEditedBeforeALayoutIsThere() = runTest {
        ha.answer = HaConnectionState.Unreachable
        val layout = MemoryHaLayout()
        val vm = model(layout)
        vm.toggleEditing()
        vm.addSection("Nope")
        runCurrent()
        assertNull(vm.live().dashboard)
        assertFalse(vm.live().editing)
        assertEquals(0, layout.saves)
    }

    @Test fun anEditIsSavedAtOnce() = runTest {
        val layout = MemoryHaLayout(SAVED_TABS)
        val vm = model(layout)
        vm.addSection("Hall")
        runCurrent()
        assertEquals(listOf("Mine", "Hall"), assertNotNull(vm.live().dashboard).sections.map { it.title })
        assertEquals(vm.live().dashboard, layout.savedDashboard())
    }

    @Test fun aRunOfEditsEndsWithTheLastOneSaved() = runTest {
        val layout = MemoryHaLayout(SAVED_TABS)
        val vm = model(layout)
        vm.addSection("A")
        vm.addSection("B")
        vm.renameSection(SectionId("s0"), "Renamed")
        vm.deleteTile(TileId("t1"))
        runCurrent()
        assertEquals(vm.live().dashboard, layout.savedDashboard())
        assertEquals(listOf("Renamed", "A", "B"), assertNotNull(layout.savedDashboard()).sections.map { it.title })
    }

    @Test fun anEditThatChangesNothingIsNotSaved() = runTest {
        val layout = MemoryHaLayout(SAVED_TABS)
        val vm = model(layout)
        vm.deleteTile(TileId("nobody"))
        vm.moveTile(TileId("t0"), SectionId("s0"), 0)
        runCurrent()
        assertEquals(0, layout.saves)
    }

    @Test fun newTilesAndSectionsGetIdsNothingHasAndANewTileIsWhatItsDomainSays() = runTest {
        val vm = model(MemoryHaLayout(SAVED_TABS))
        vm.addSection("Hall")
        vm.addTile(SectionId("s1"), HaEntityId("script.cool"))
        runCurrent()
        val sections = assertNotNull(vm.live().dashboard).sections
        assertEquals(listOf("s0", "s1"), sections.map { it.id.value })
        val added = assertIs<HaTile.Action>(sections[1].tiles.single())
        assertEquals("t2", added.id.value)
        assertEquals(HaServiceCall("script", "cool", emptyMap()), added.call)
    }

    @Test fun aDragOfTheEditListMovesATileAcrossSections() = runTest {
        val vm = model(MemoryHaLayout(SAVED_TABS))
        vm.addSection("Hall")
        runCurrent()
        // rows: s0, t0, t1, s1. Drop t0 below the second header.
        vm.moveRow(1, 3)
        runCurrent()
        val sections = assertNotNull(vm.live().dashboard).sections
        assertEquals(listOf(listOf("t1"), listOf("t0")), sections.map { s -> s.tiles.map { it.id.value } })
    }

    @Test fun aTapDoesNotCallAnythingWhileEditing() = runTest {
        val vm = model(MemoryHaLayout(SAVED_TABS))
        vm.toggleEditing()
        runCurrent()
        assertTrue(vm.live().editing)
        vm.onTile(HaTile.Toggle(TileId("t0"), "", "", HaEntityId("light.lamp")))
        vm.onCover(HaTile.Cover(TileId("t5"), "", "", HaEntityId("cover.blind")), HaCoverCommand.Open)
        vm.onMedia(HaTile.Media(TileId("t6"), "", "", HaEntityId("media_player.kitchen")))
        runCurrent()
        assertEquals(0, ha.calls.size)
        vm.toggleEditing()
        vm.onTile(HaTile.Toggle(TileId("t0"), "", "", HaEntityId("light.lamp")))
        runCurrent()
        assertEquals(1, ha.calls.size)
    }

    @Test fun theEditButtonFollowsWhetherThereIsALayoutAndWhetherItIsOpen() = runTest {
        val vm = model(MemoryHaLayout(SAVED_TABS))
        assertEquals(HaEditButton.Edit, vm.editButton.value)
        vm.toggleEditing()
        runCurrent()
        assertEquals(HaEditButton.Done, vm.editButton.value)
        ha.answer = HaConnectionState.Unreachable
        assertEquals(HaEditButton.Hidden, editButtonOf(HaState.NeedsSetup))
        assertEquals(HaEditButton.Hidden, editButtonOf(HaState.Live(HaConnectionState.Connected, emptyList(), null, false, false)))
    }

    @Test fun aResyncReplacesTheSavedLayoutWithHomeAssistants() = runTest {
        val layout = MemoryHaLayout(SAVED_TABS)
        val vm = model(layout)
        assertEquals(listOf("Mine"), assertNotNull(vm.live().dashboard).sections.map { it.title })
        vm.resyncFromHa()
        runCurrent()
        assertEquals(HaResync.Done, vm.resync.value)
        assertEquals(listOf("Kitchen"), assertNotNull(vm.live().dashboard).sections.map { it.title })
        assertEquals(vm.live().dashboard, layout.savedDashboard())
    }

    @Test fun aResyncLeavesEditMode() = runTest {
        val vm = model(MemoryHaLayout(SAVED_TABS))
        vm.toggleEditing()
        runCurrent()
        assertTrue(vm.live().editing)
        vm.resyncFromHa()
        runCurrent()
        assertFalse(vm.live().editing)
    }

    @Test fun aResyncThatCannotReadFailsAndKeepsTheLayout() = runTest {
        ha.config = "not json"
        val layout = MemoryHaLayout(SAVED_TABS)
        val vm = model(layout)
        vm.resyncFromHa()
        runCurrent()
        assertEquals(HaResync.Failed, vm.resync.value)
        assertEquals(SAVED_LAYOUT, vm.live().dashboard)
        assertEquals(0, layout.saves)
    }
}

class ConnectHaTest {
    private val ha = FakeHa()

    @Test fun acceptedIsSaved() = runTest {
        val settings = MemoryHaSettings()
        assertEquals(ConnectHaOutcome.Connected, ConnectHa(ha, settings)(ADDRESS))
        assertEquals(SavedHaAddress.Set(ADDRESS), settings.saved)
    }

    @Test fun aRefusedTokenIsNotSavedAndTheOldConnectionComesBack() = runTest {
        val old = HaAddress("old.local", 8123, "old")
        val settings = MemoryHaSettings(SavedHaAddress.Set(old))
        ha.answer = HaConnectionState.AuthInvalid
        assertEquals(ConnectHaOutcome.TokenRefused, ConnectHa(ha, settings)(ADDRESS))
        assertEquals(SavedHaAddress.Set(old), settings.saved)
        assertEquals(listOf(ADDRESS, old), ha.connects)
    }

    @Test fun nothingAnsweringIsNotSavedAndLeavesNoLinkWhenNothingWasSaved() = runTest {
        val settings = MemoryHaSettings()
        ha.answer = HaConnectionState.Unreachable
        assertEquals(ConnectHaOutcome.Unreachable, ConnectHa(ha, settings)(ADDRESS))
        assertEquals(SavedHaAddress.Unset, settings.saved)
        assertEquals(HaConnectionState.Disconnected, ha.state.value)
    }

    @Test fun noVerdictInTimeIsUnreachable() = runTest {
        ha.answer = HaConnectionState.Connecting
        assertEquals(ConnectHaOutcome.Unreachable, ConnectHa(ha, MemoryHaSettings(), verdictTimeoutMillis = 5_000)(ADDRESS))
    }
}

class HaSettingsViewModelTest {
    private val ha = FakeHa()
    private val connected = mutableListOf<HaAddress>()

    private fun TestScope.form(settings: MemoryHaSettings = MemoryHaSettings()) =
        HaSettingsViewModel(settings, ConnectHa(ha, settings), backgroundScope) { connected += it }.also { runCurrent() }

    @Test fun savedValuesFillTheFields() = runTest {
        val vm = form(MemoryHaSettings(SavedHaAddress.Set(ADDRESS)))
        assertEquals("ha.local:8123", vm.address.value)
        assertEquals("tok", vm.token.value)
    }

    @Test fun invalidInputNamesTheReasonAndConnectsNothing() = runTest {
        val vm = form()
        vm.onAddress("ha.local")
        vm.connect()
        runCurrent()
        assertEquals(HaFormStatus.Invalid(HaInput.Reason.EmptyToken), vm.status.value)
        assertEquals(emptyList(), ha.connects)
    }

    @Test fun typingClearsAnOldMessage() = runTest {
        val vm = form()
        vm.connect()
        assertEquals(HaFormStatus.Invalid(HaInput.Reason.EmptyAddress), vm.status.value)
        vm.onAddress("x")
        assertEquals(HaFormStatus.Idle, vm.status.value)
    }

    @Test fun anAcceptedPairIsSavedAndReported() = runTest {
        val vm = form()
        vm.onAddress("ha.local")
        vm.onToken("tok")
        vm.connect()
        runCurrent()
        assertEquals(HaFormStatus.Connected, vm.status.value)
        assertEquals(listOf(ADDRESS), connected)
        assertEquals("ha.local:8123", vm.address.value)
    }

    @Test fun aRefusedTokenAndAnUnreachableServerAreTold() = runTest {
        val vm = form()
        vm.onAddress("ha.local")
        vm.onToken("tok")
        ha.answer = HaConnectionState.AuthInvalid
        vm.connect()
        runCurrent()
        assertEquals(HaFormStatus.TokenRefused, vm.status.value)
        ha.answer = HaConnectionState.Unreachable
        vm.connect()
        runCurrent()
        assertEquals(HaFormStatus.Unreachable("ha.local:8123"), vm.status.value)
        assertEquals(emptyList(), connected)
    }
}
