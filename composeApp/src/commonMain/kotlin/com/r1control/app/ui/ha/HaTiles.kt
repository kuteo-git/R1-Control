package com.r1control.app.ui.ha

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaCoverCommand
import com.r1control.app.domain.model.ha.HaDashboard
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaTap
import com.r1control.app.domain.model.ha.HaTile
import com.r1control.app.domain.model.ha.isLitBy
import com.r1control.app.domain.model.ha.isPressable
import com.r1control.app.domain.model.ha.isUnavailableWith
import com.r1control.app.domain.model.ha.tapAction
import com.r1control.app.domain.model.ha.titleFor
import com.r1control.app.domain.model.ha.visibleSections
import com.r1control.app.ui.components.IconTap
import com.r1control.app.ui.components.R1Icon
import com.r1control.app.ui.components.SectionLabel
import com.r1control.app.ui.components.tappable
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.icons.MdiIcons
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.Radius
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** What a tile can ask for. The screen hands these to the view model; previews hand [None]. */
class HaTileActions(
    val onTile: (HaTile) -> Unit,
    val onCover: (HaTile.Cover, HaCoverCommand) -> Unit,
    val onMedia: (HaTile.Media) -> Unit,
    val onMediaNext: (HaTile.Media) -> Unit,
    val onMediaStop: (HaTile.Media) -> Unit,
    val onMediaSeek: (HaTile.Media, Int) -> Unit,
    val onMediaVolume: (HaTile.Media, Float) -> Unit,
    /** A tile whose entity has controls of its own (a dimmable light, a fan) shows them instead of toggling. */
    val openControls: (HaEntityId) -> Unit,
    /** Home Assistant's live rendering of a template; collected only while the tile is on screen. */
    val rendered: (template: String) -> Flow<String>,
) {
    companion object {
        val None = HaTileActions({}, { _, _ -> }, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, { emptyFlow() })
    }
}

/** Tiles per row on a phone. */
private const val TILES_PER_ROW = 2

private fun HaTile.takesWholeRow(): Boolean = this is HaTile.Cover || this is HaTile.Media

/** Tiles with controls of their own take a whole row; the rest sit two to a row, in order. */
fun tileRowsOf(tiles: List<HaTile>): List<List<HaTile>> {
    val rows = mutableListOf<List<HaTile>>()
    val pending = mutableListOf<HaTile>()
    fun flush() {
        if (pending.isNotEmpty()) rows += pending.toList()
        pending.clear()
    }
    for (tile in tiles) {
        if (tile.takesWholeRow()) {
            flush()
            rows += listOf(tile)
        } else {
            pending += tile
            if (pending.size == TILES_PER_ROW) flush()
        }
    }
    flush()
    return rows
}

/**
 * The glyph for a tile: the exact Material Design Icon Home Assistant named for the entity, drawn from the
 * app's generated [MdiIcons] set. A name not in the set, or an entity that named none, falls back to one by
 * what the tile does. [lit] picks an outline bulb for a light that is off and has no icon of its own.
 */
fun glyphFor(tile: HaTile, lit: Boolean): ImageVector = MdiIcons.byName(iconNameFor(tile, lit)) ?: R1Icons.Power

/** The mdi name a tile draws: its own seeded icon when the set has it, else one by what the tile does. */
fun iconNameFor(tile: HaTile, lit: Boolean): String {
    val own = tile.icon.removePrefix("mdi:")
    if (own.isNotEmpty() && MdiIcons.has(own)) return own
    return defaultMdiFor(tile, lit)
}

/** The mdi name for a tile that carries none, by its domain and what it does. */
private fun defaultMdiFor(tile: HaTile, lit: Boolean): String = when (tile) {
    is HaTile.Toggle -> when (tile.entity.domainName) {
        "light" -> if (lit) "lightbulb" else "lightbulb-outline"
        "fan" -> "fan"
        "climate" -> "thermostat"
        "camera" -> "cctv"
        "switch" -> "toggle-switch"
        else -> "power"
    }
    is HaTile.Action -> "play"
    is HaTile.Readout -> "gauge"
    is HaTile.Cover -> "window-shutter"
    is HaTile.Media -> "speaker"
    is HaTile.Template -> "tune"
}

/** The dashboard as lazy items: a title, then rows of tiles, per section. A tile whose condition does not hold is left out, and a section left with nothing is too. */
fun LazyListScope.dashboardItems(dashboard: HaDashboard, entities: Map<HaEntityId, HaEntity>, connected: Boolean, baseUrl: String, actions: HaTileActions) {
    dashboard.visibleSections(entities).forEach { section ->
        if (section.title.isNotEmpty()) {
            item(key = "title:${section.id.value}") {
                SectionLabel(section.title, Modifier.padding(horizontal = Space.Gutter))
            }
        }
        tileRowsOf(section.tiles).forEach { row ->
            item(key = "row:${row.first().id.value}") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.Gutter, vertical = Space.Small / 2),
                    horizontalArrangement = Arrangement.spacedBy(Space.Small),
                ) {
                    row.forEach { tile ->
                        HaTileCard(tile, entities[tile.entity], connected, baseUrl, actions, Modifier.weight(1f))
                    }
                    // A lone tile in a pair keeps its half of the row.
                    if (row.size < TILES_PER_ROW && !row.single().takesWholeRow()) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun HaTileCard(tile: HaTile, entity: HaEntity?, connected: Boolean, baseUrl: String, actions: HaTileActions, modifier: Modifier = Modifier) {
    if (tile is HaTile.Media) {
        HaMediaCard(tile, entity, actions, baseUrl, modifier)
        return
    }
    val lit = tile.isLitBy(entity)
    val unavailable = tile.isUnavailableWith(entity)
    val shape = RoundedCornerShape(Radius.Field)
    val content = if (lit) Tokens.Ground else Tokens.OnGround
    val muted = if (lit) Tokens.Ground.copy(alpha = 0.7f) else Tokens.OnGroundMuted
    val pressable = tile.isPressable
    Row(
        modifier
            .heightIn(min = 72.dp)
            .alpha(if (unavailable) 0.5f else 1f)
            .clip(shape)
            .background(if (lit) Tokens.Accent else Tokens.Card)
            .let {
                if (pressable) {
                    it.tappable(role = if (tile is HaTile.Toggle) Role.Switch else Role.Button) {
                        when (tapAction(entity, tile)) {
                            HaTap.OpenControls -> actions.openControls(tile.entity)
                            HaTap.RunTile -> actions.onTile(tile)
                        }
                    }
                } else it
            }
            .padding(horizontal = Space.Medium, vertical = Space.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Medium),
    ) {
        // A template draws an icon only when its card named one: the text is the tile.
        if (tile !is HaTile.Template || tile.icon.isNotBlank()) R1Icon(glyphFor(tile, lit), null, tint = content)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.Lines)) {
            if (tile is HaTile.Template) {
                TemplateText(tile, content, muted, connected, actions)
            } else {
                Text(tile.titleFor(entity), style = MaterialTheme.typography.labelLarge, color = content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val line = secondLine(tile, entity)
                if (line.isNotEmpty()) {
                    Text(line, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        when (tile) {
            is HaTile.Cover -> CoverControls(tile, lit, actions)
            else -> Unit
        }
    }
}

/**
 * A template tile's body: its name when it has one, then Home Assistant's rendering of its text. The
 * subscription lives only while this is composed, so a tile scrolled away, or a list replaced by the edit
 * mode, lets it go. A link that is down ends the subscription, and coming back up subscribes again; the
 * last text stays in between.
 */
@Composable
private fun TemplateText(tile: HaTile.Template, content: Color, muted: Color, connected: Boolean, actions: HaTileActions) {
    var rendered by remember(tile.template) { mutableStateOf("") }
    if (connected) {
        LaunchedEffect(tile.template) { actions.rendered(tile.template).collect { rendered = it.trim() } }
    }
    if (tile.name.isNotBlank()) {
        Text(tile.name, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Text(rendered, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** Home Assistant's own word for the state; a player says its song instead when it has one, and an action says nothing. */
internal fun secondLine(tile: HaTile, entity: HaEntity?): String {
    if (entity == null) return ""
    return when (tile) {
        is HaTile.Action, is HaTile.Template -> ""
        is HaTile.Media -> (entity.attributes["media_title"] as? HaAttribute.Text)?.value?.takeIf { it.isNotBlank() } ?: entity.state
        else -> stateLabel(entity)
    }
}

@Composable
private fun CoverControls(tile: HaTile.Cover, lit: Boolean, actions: HaTileActions) {
    val strings = LocalStrings.current
    val tint = if (lit) Tokens.Ground else Tokens.OnGround
    Row {
        IconTap(R1Icons.ExpandLess, strings.haCoverOpen, { actions.onCover(tile, HaCoverCommand.Open) }, tint = tint)
        IconTap(R1Icons.Stop, strings.haCoverStop, { actions.onCover(tile, HaCoverCommand.Stop) }, tint = tint)
        IconTap(R1Icons.ExpandMore, strings.haCoverClose, { actions.onCover(tile, HaCoverCommand.Close) }, tint = tint)
    }
}

