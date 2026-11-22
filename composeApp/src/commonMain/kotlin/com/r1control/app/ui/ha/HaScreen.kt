package com.r1control.app.ui.ha

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaCondition
import com.r1control.app.domain.model.ha.HaConnectionState
import com.r1control.app.domain.model.ha.haBaseUrl
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaDashboard
import com.r1control.app.domain.model.ha.HaCamera
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaSection
import com.r1control.app.domain.model.ha.HaServiceCall
import com.r1control.app.domain.model.ha.HaTabs
import com.r1control.app.domain.model.ha.HaTile
import com.r1control.app.domain.model.ha.SectionId
import com.r1control.app.domain.model.ha.TileId
import com.r1control.app.ui.camera.cameraItems
import com.r1control.app.ui.components.LoadingBox
import com.r1control.app.ui.components.TonalButton
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The Home Assistant tab: the household's own dashboard, seeded from Home Assistant and drawn in this
 * app's material, its tiles bound to the live entities. While that dashboard is not read (or cannot be)
 * the tab lists every entity with its state instead.
 */
@Composable
fun HaScreen(viewModel: HaViewModel, setup: HaSettingsViewModel) {
    val state by viewModel.state.collectAsState()
    val address by setup.address.collectAsState()
    val token by setup.token.collectAsState()
    val status by setup.status.collectAsState()
    val actions = remember(viewModel) {
        HaTileActions(
            viewModel::onTile, viewModel::onCover, viewModel::onMedia, viewModel::onMediaNext, viewModel::onMediaStop,
            viewModel::onMediaSeek, viewModel::openControls, viewModel::renderedTemplate,
        )
    }
    val edit = remember(viewModel) {
        HaEditActions(
            viewModel::startEmpty, viewModel::addSection, viewModel::renameSection, viewModel::deleteSection,
            viewModel::addTile, viewModel::renameTile, viewModel::deleteTile, viewModel::moveRow,
        )
    }
    val tabActions = remember(viewModel) {
        HaTabActions(
            viewModel::selectTab, viewModel::addTab, viewModel::renameTab,
            viewModel::setTabIcon, viewModel::deleteTab, viewModel::moveTab,
        )
    }
    val cameras by viewModel.cameras.collectAsState()
    HaContent(state, address, token, status, setup::onAddress, setup::onToken, setup::connect, actions, edit, tabActions, cameras, viewModel::cameraStreamPath, viewModel::openCamera)
    val target by viewModel.controlTarget.collectAsState()
    val controls = remember(viewModel) {
        HaControlActions(
            viewModel::togglePower, viewModel::setBrightness, viewModel::setFanSpeed,
            viewModel::setOscillate, viewModel::setPreset, viewModel::setColor, viewModel::setWhite,
        )
    }
    // The sheet reads the live entity, so it follows what Home Assistant says; one that is gone closes it.
    (state as? HaState.Live)?.entities?.firstOrNull { it.id == target }?.let { live ->
        HaControlDialog(live, controls, viewModel::closeControls)
    }
}

@Composable
fun HaContent(
    state: HaState,
    address: String,
    token: String,
    status: HaFormStatus,
    onAddress: (String) -> Unit,
    onToken: (String) -> Unit,
    onConnect: () -> Unit,
    actions: HaTileActions,
    edit: HaEditActions,
    tabs: HaTabActions = HaTabActions.None,
    cameras: List<HaCamera> = emptyList(),
    cameraPath: suspend (HaEntityId) -> String = { "" },
    onOpenCamera: (HaCamera) -> Unit = {},
) {
    when (state) {
        HaState.Loading -> LoadingBox()
        HaState.NeedsSetup -> HaSetupContent(address, token, status, false, onAddress, onToken, onConnect)
        HaState.TokenRefused -> HaSetupContent(address, token, status, true, onAddress, onToken, onConnect)
        is HaState.Live -> HaLive(state, haBaseUrl(address), actions, edit, tabs, cameras, cameraPath, onOpenCamera)
    }
}

@Composable
private fun HaLive(
    state: HaState.Live,
    baseUrl: String,
    actions: HaTileActions,
    edit: HaEditActions,
    tabActions: HaTabActions,
    cameras: List<HaCamera>,
    cameraPath: suspend (HaEntityId) -> String,
    onOpenCamera: (HaCamera) -> Unit,
) {
    val strings = LocalStrings.current
    val connected = state.connection == HaConnectionState.Connected
    val dashboard = state.dashboard
    // The cameras are hidden while the layout is edited; the fullscreen one is drawn by the shell, over everything.
    val showCameras = cameras.takeUnless { state.editing }.orEmpty()
    Column(Modifier.fillMaxSize()) {
        // More than one tab earns a bar to switch between them; the edit mode shows it even for one, to add or edit.
        state.tabs?.takeIf { state.editing || it.tabs.size > 1 }?.let { HaTabBar(it, state.editing, tabActions) }
        when {
            dashboard != null && state.editing -> HaEditList(dashboard, state.entities, edit)
            dashboard != null && (dashboard.tileCount > 0 || showCameras.isNotEmpty()) -> {
                val byId = remember(state.entities) { state.entities.associateBy { it.id } }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.XLarge)) {
                    cameraItems(showCameras, baseUrl, cameraPath, onOpenCamera)
                    dashboardItems(dashboard, byId, connected, baseUrl, actions)
                }
            }
            dashboard != null -> Note(strings.haDashboardEmpty)
            else -> HaEntityList(state, if (state.dashboardFailed) strings.haDashboardFailed else if (connected) strings.haDashboardLoading else "", edit.onStartEmpty)
        }
    }
}

@Composable
internal fun Note(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = Space.Gutter + Space.Small, vertical = Space.Large),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Every entity with its state: what the tab shows until a dashboard can take its place. */
@Composable
private fun HaEntityList(state: HaState.Live, note: String, onStartEmpty: () -> Unit) {
    val strings = LocalStrings.current
    if (state.entities.isEmpty()) {
        Note(note.ifEmpty { strings.haNoEntities })
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (note.isNotEmpty()) item(key = "note") { Note(note) }
        // Without a dashboard to seed from, the household builds its own.
        if (state.dashboardFailed) item(key = "start-empty") {
            TonalButton(strings.haStartEmpty, onStartEmpty, Modifier.padding(horizontal = Space.Gutter + Space.Small, vertical = Space.Small))
        }
        items(state.entities, key = { it.id.value }) { EntityRow(it) }
    }
}

@Composable
private fun EntityRow(entity: HaEntity) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Space.Gutter + Space.Small, vertical = Space.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Large),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Lines)) {
            Text(entity.friendlyName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entity.id.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(stateLabel(entity), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Home Assistant's own word for the state, with a sensor's unit after it. */
internal fun stateLabel(entity: HaEntity): String = if (entity.unit.isEmpty()) entity.state else "${entity.state} ${entity.unit}"

// ---- previews ----

@Composable
private fun PreviewHost(strings: Strings, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStrings provides strings) {
        R1Theme { Box(Modifier.background(Tokens.Ground)) { content() } }
    }
}

private fun sample(id: String, state: String, name: String, unit: String = ""): HaEntity {
    val attributes = buildMap<String, HaAttribute> {
        put(HaEntity.FRIENDLY_NAME, HaAttribute.Text(name))
        if (unit.isNotEmpty()) put(HaEntity.UNIT, HaAttribute.Text(unit))
    }
    return HaEntity(HaEntityId(id), state, attributes)
}

private val sampleEntities = listOf(
    sample("light.kitchen", "on", "Kitchen light"),
    sample("sensor.hall_temperature", "21.5", "Hall temperature", "°C"),
    sample("switch.fan", "off", "Fan"),
    sample("switch.hall", "on", "Hall light"),
    sample("cover.blind", "open", "Blind"),
    sample("script.cool", "off", "Cool the bedroom"),
    HaEntity(
        HaEntityId("media_player.kitchen"),
        "playing",
        mapOf(HaEntity.FRIENDLY_NAME to HaAttribute.Text("Kitchen speaker"), "media_title" to HaAttribute.Text("Suong Trang")),
    ),
)

private fun tile(n: Int) = TileId("t$n")

private val sampleDashboard = HaDashboard(
    listOf(
        HaSection(
            SectionId("s0"),
            "Living room",
            listOf(
                HaTile.Toggle(tile(0), "Ceiling", "mdi:lightbulb", HaEntityId("light.kitchen")),
                HaTile.Toggle(tile(1), "Fan", "mdi:fan", HaEntityId("switch.fan")),
                HaTile.Toggle(tile(2), "", "mdi:lightbulb", HaEntityId("switch.hall")),
                HaTile.Readout(tile(3), "Hall", "mdi:coolant-temperature", HaEntityId("sensor.hall_temperature")),
                HaTile.Cover(tile(4), "", "", HaEntityId("cover.blind")),
            ),
        ),
        HaSection(
            SectionId("s1"),
            "Air conditioner",
            listOf(
                HaTile.Action(
                    tile(5), "Cool", "mdi:air-conditioner", HaEntityId("script.cool"),
                    HaServiceCall("script", "cool", emptyMap()),
                ),
                HaTile.Media(tile(6), "", "", HaEntityId("media_player.kitchen")),
            ),
        ),
    ),
)

private val playingKitchen = HaCondition.StateIs(HaEntityId("media_player.kitchen"), listOf("playing"))

/** Template tiles as the household's dashboard has them: short buttons shown while a player plays, and a line of text. */
private val templateDashboard = HaDashboard(
    listOf(
        HaSection(
            SectionId("s0"),
            "Kitchen speaker",
            listOf(
                HaTile.Template(tile(0), "", "", HaEntityId.None, playingKitchen, "Next", HaServiceCall("pyscript", "pytube_next_song", emptyMap())),
                HaTile.Template(tile(1), "", "", HaEntityId.None, playingKitchen, "{% if shuffle %}Shuffle: on{% else %}Shuffle: off{% endif %}", HaServiceCall("pyscript", "pytube_shuffle_toggle", emptyMap())),
                HaTile.Template(
                    tile(2), "", "", HaEntityId.None, HaCondition.StateIs(HaEntityId("media_player.kitchen"), listOf("paused")),
                    "Play", HaServiceCall("pyscript", "pytube_resume", emptyMap()),
                ),
                HaTile.Template(tile(3), "Today", "", HaEntityId("sensor.hall_temperature"), template = "{{ now().day }} / {{ now().month }}", call = HaServiceCall.None),
                HaTile.Toggle(tile(4), "Ceiling", "mdi:lightbulb", HaEntityId("light.kitchen")),
            ),
        ),
    ),
)

private val renderedSamples = mapOf(
    "Next" to "Next",
    "{% if shuffle %}Shuffle: on{% else %}Shuffle: off{% endif %}" to "Shuffle: on",
    "Play" to "Play",
    "{{ now().day }} / {{ now().month }}" to "16 / 11",
)

private val previewActions = HaTileActions({}, { _, _ -> }, {}, {}, {}, { _, _ -> }, {}, { template -> flowOf(renderedSamples[template].orEmpty()) })

private fun live(
    connection: HaConnectionState,
    dashboard: HaDashboard?,
    failed: Boolean = false,
    entities: List<HaEntity> = sampleEntities,
    editing: Boolean = false,
) = HaState.Live(connection, entities, dashboard?.let { HaTabs.of(it) }, failed, editing)

@Composable
private fun Content(state: HaState) = HaContent(state, "", "", HaFormStatus.Idle, {}, {}, {}, HaTileActions.None, HaEditActions.None)

@Preview
@Composable
private fun DashboardPreview() = PreviewHost(EnglishStrings) { Content(live(HaConnectionState.Connected, sampleDashboard)) }

@Preview
@Composable
private fun TemplateTilesPreview() = PreviewHost(EnglishStrings) {
    HaContent(live(HaConnectionState.Connected, templateDashboard), "", "", HaFormStatus.Idle, {}, {}, {}, previewActions, HaEditActions.None)
}

/** The same dashboard with nothing playing: the conditional tiles are gone and no empty row is left. */
@Preview
@Composable
private fun TemplateTilesIdlePreview() = PreviewHost(EnglishStrings) {
    val idle = sampleEntities.filterNot { it.id.value.startsWith("media_player") } + sample("media_player.kitchen", "idle", "Kitchen speaker")
    HaContent(live(HaConnectionState.Connected, templateDashboard, entities = idle), "", "", HaFormStatus.Idle, {}, {}, {}, previewActions, HaEditActions.None)
}

@Preview
@Composable
private fun TemplateTilesEditPreview() = PreviewHost(EnglishStrings) { Content(live(HaConnectionState.Connected, templateDashboard, editing = true)) }

@Preview
@Composable
private fun TemplateTilesVietnamesePreview() = PreviewHost(VietnameseStrings) {
    HaContent(live(HaConnectionState.Connected, templateDashboard), "", "", HaFormStatus.Idle, {}, {}, {}, previewActions, HaEditActions.None)
}

@Preview
@Composable
private fun TemplateTilesEditVietnamesePreview() = PreviewHost(VietnameseStrings) { Content(live(HaConnectionState.Connected, templateDashboard, editing = true)) }

@Preview
@Composable
private fun DashboardLoadingPreview() = PreviewHost(EnglishStrings) { Content(live(HaConnectionState.Connected, null)) }

@Preview
@Composable
private fun DashboardFailedPreview() = PreviewHost(EnglishStrings) { Content(live(HaConnectionState.Connected, null, failed = true)) }

@Preview
@Composable
private fun DashboardEmptyPreview() = PreviewHost(EnglishStrings) { Content(live(HaConnectionState.Connected, HaDashboard.Empty)) }

@Preview
@Composable
private fun ConnectingPreview() = PreviewHost(EnglishStrings) { Content(live(HaConnectionState.Connecting, null, entities = emptyList())) }

/** The layout is the person's own: with the link down it stays, every tile unavailable. */
@Preview
@Composable
private fun UnreachablePreview() = PreviewHost(EnglishStrings) { Content(live(HaConnectionState.Unreachable, sampleDashboard, entities = emptyList())) }

@Preview
@Composable
private fun EditPreview() = PreviewHost(EnglishStrings) { Content(live(HaConnectionState.Connected, sampleDashboard, editing = true)) }

@Preview
@Composable
private fun EditEmptyPreview() = PreviewHost(EnglishStrings) { Content(live(HaConnectionState.Connected, HaDashboard.Empty, editing = true)) }

@Preview
@Composable
private fun EditVietnamesePreview() = PreviewHost(VietnameseStrings) { Content(live(HaConnectionState.Connected, sampleDashboard, editing = true)) }

@Preview
@Composable
private fun LoadingPreview() = PreviewHost(EnglishStrings) { Content(HaState.Loading) }

@Preview
@Composable
private fun NeedsSetupPreview() = PreviewHost(EnglishStrings) { Content(HaState.NeedsSetup) }

@Preview
@Composable
private fun VietnamesePreview() = PreviewHost(VietnameseStrings) { Content(live(HaConnectionState.Connected, sampleDashboard)) }
