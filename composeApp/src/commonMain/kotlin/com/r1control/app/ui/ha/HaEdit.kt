package com.r1control.app.ui.ha

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.ha.HaCondition
import com.r1control.app.domain.model.ha.HaDashboard
import com.r1control.app.domain.model.ha.HaEditRow
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaTile
import com.r1control.app.domain.model.ha.SectionId
import com.r1control.app.domain.model.ha.TileId
import com.r1control.app.domain.model.ha.editRows
import com.r1control.app.domain.model.ha.pickableEntities
import com.r1control.app.domain.model.ha.titleFor
import com.r1control.app.ui.components.ConfirmDialog
import com.r1control.app.ui.components.IconTap
import com.r1control.app.ui.components.NameDialog
import com.r1control.app.ui.components.OutlinePill
import com.r1control.app.ui.components.R1TextField
import com.r1control.app.ui.components.TonalButton
import com.r1control.app.ui.components.editableItems
import com.r1control.app.ui.components.rememberListDrag
import com.r1control.app.ui.components.tappable
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.Radius
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens

/** What the edit mode can ask for. The screen hands these to the view model; previews hand [None]. */
class HaEditActions(
    val onStartEmpty: () -> Unit,
    val onAddSection: (String) -> Unit,
    val onRenameSection: (SectionId, String) -> Unit,
    val onDeleteSection: (SectionId) -> Unit,
    val onAddTile: (SectionId, HaEntityId) -> Unit,
    val onRenameTile: (TileId, String) -> Unit,
    val onDeleteTile: (TileId) -> Unit,
    /** A drag of the flat column, in `editRows` indexes. */
    val onMoveRow: (from: Int, to: Int) -> Unit,
) {
    companion object {
        val None = HaEditActions({}, {}, { _, _ -> }, {}, { _, _ -> }, { _, _ -> }, {}, { _, _ -> })
    }
}

/** The question the edit mode has open over the list. */
private sealed interface HaAsk {
    data object None : HaAsk
    data object NewSection : HaAsk
    data class RenameSection(val id: SectionId, val title: String) : HaAsk
    data class DeleteSection(val id: SectionId, val title: String, val tiles: Int) : HaAsk
    data class RenameTile(val id: TileId, val name: String) : HaAsk
    data class PickEntity(val section: SectionId) : HaAsk
}

/**
 * The dashboard as one column, so that dragging is plain: every section's header, then its tiles, one to a row.
 * A tile's handle drags it within its section and into another; a header's handle drags the whole section.
 */
@Composable
fun HaEditList(dashboard: HaDashboard, entities: List<HaEntity>, actions: HaEditActions) {
    val strings = LocalStrings.current
    val byId = remember(entities) { entities.associateBy { it.id } }
    val rows = remember(dashboard) { dashboard.editRows() }
    val list = rememberLazyListState()
    val drag = rememberListDrag(rows)
    var ask by remember { mutableStateOf<HaAsk>(HaAsk.None) }

    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = Space.XLarge)) {
        if (rows.isEmpty()) item(key = "empty") { Note(strings.haEmptyEdit) }
        editableItems(
            items = rows,
            key = { it.key },
            drag = drag,
            list = list,
            enabled = true,
            onMove = actions.onMoveRow,
            onRemove = { row ->
                when (row) {
                    is HaEditRow.TileRow -> actions.onDeleteTile(row.tile.id)
                    is HaEditRow.Header -> {
                        val tiles = dashboard.sections.firstOrNull { it.id == row.id }?.tiles?.size ?: 0
                        if (tiles == 0) actions.onDeleteSection(row.id) else ask = HaAsk.DeleteSection(row.id, row.title, tiles)
                    }
                }
            },
            canRemove = true,
            handleLabel = { row ->
                when (row) {
                    is HaEditRow.Header -> strings.haReorderSection(row.title.ifEmpty { strings.haUntitledSection })
                    is HaEditRow.TileRow -> strings.haReorderTile(row.tile.titleFor(byId[row.tile.entity]))
                }
            },
            removeLabel = { row ->
                when (row) {
                    is HaEditRow.Header -> strings.haRemoveSection(row.title.ifEmpty { strings.haUntitledSection })
                    is HaEditRow.TileRow -> strings.haRemoveTile(row.tile.titleFor(byId[row.tile.entity]))
                }
            },
            rowModifier = Modifier.padding(horizontal = Space.Small, vertical = Space.Hair),
        ) { row ->
            when (row) {
                is HaEditRow.Header -> HeaderRow(row, onRename = { ask = HaAsk.RenameSection(row.id, row.title) }, onAdd = { ask = HaAsk.PickEntity(row.id) })
                is HaEditRow.TileRow -> TileEditRow(row, byId[row.tile.entity], onRename = { ask = HaAsk.RenameTile(row.tile.id, row.tile.name) })
            }
        }
        item(key = "add-section") {
            OutlinePill(
                strings.haAddSection,
                { ask = HaAsk.NewSection },
                Modifier.padding(horizontal = Space.Gutter, vertical = Space.Large),
                icon = R1Icons.Add,
            )
        }
    }

    val close = { ask = HaAsk.None }
    when (val open = ask) {
        HaAsk.None -> Unit
        HaAsk.NewSection -> NameDialog(
            strings.haNewSection, "", strings.haSectionName, strings.create, strings.cancel,
            onConfirm = { actions.onAddSection(it); close() }, onCancel = close,
        )
        is HaAsk.RenameSection -> NameDialog(
            strings.haRenameSection, open.title, strings.haSectionName, strings.save, strings.cancel,
            onConfirm = { actions.onRenameSection(open.id, it); close() }, onCancel = close,
        )
        is HaAsk.DeleteSection -> ConfirmDialog(
            strings.haDeleteSectionQuestion(open.title.ifEmpty { strings.haUntitledSection }, open.tiles),
            strings.delete, strings.cancel,
            onConfirm = { actions.onDeleteSection(open.id); close() }, onCancel = close,
        )
        is HaAsk.RenameTile -> NameDialog(
            strings.haRenameTile, open.name, strings.haTileName, strings.save, strings.cancel,
            onConfirm = { actions.onRenameTile(open.id, it); close() }, onCancel = close,
        )
        is HaAsk.PickEntity -> EntityPickerDialog(
            entities, dashboard,
            onPick = { actions.onAddTile(open.section, it); close() }, onCancel = close,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderRow(row: HaEditRow.Header, onRename: () -> Unit, onAdd: () -> Unit) {
    val strings = LocalStrings.current
    Text(
        row.title.ifEmpty { strings.haUntitledSection },
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(Radius.Row))
            .tappable(role = Role.Button, onClickLabel = strings.haRenameSection, onClick = onRename)
            .padding(horizontal = Space.Small, vertical = Space.Medium),
        style = MaterialTheme.typography.titleMedium,
        color = if (row.title.isEmpty()) Tokens.OnGroundMuted else MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    IconTap(R1Icons.Add, strings.haAddTile, onAdd, tint = Tokens.Accent)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TileEditRow(row: HaEditRow.TileRow, entity: HaEntity?, onRename: () -> Unit) {
    val strings = LocalStrings.current
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(Radius.Row))
            .background(Tokens.Card)
            .tappable(role = Role.Button, onClickLabel = strings.haRenameTile, onClick = onRename)
            .padding(horizontal = Space.Medium, vertical = Space.Small),
        verticalArrangement = Arrangement.spacedBy(Space.Lines),
    ) {
        Text(row.tile.titleFor(entity), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            tileSubtitle(row.tile, strings),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What an edit row says under the tile's name: the entity it is about, a mark for a template, and a mark when it is drawn only under a condition. */
internal fun tileSubtitle(tile: HaTile, strings: Strings): String =
    listOfNotNull(
        tile.entity.value.takeIf { it.isNotEmpty() },
        strings.haTemplateTile.takeIf { tile is HaTile.Template },
        strings.haConditionalTile.takeIf { tile.condition != HaCondition.Always },
    ).joinToString(" · ")

/** The live entities no tile is about yet, searchable by name or id. */
@Composable
private fun EntityPickerDialog(entities: List<HaEntity>, dashboard: HaDashboard, onPick: (HaEntityId) -> Unit, onCancel: () -> Unit) {
    val strings = LocalStrings.current
    var query by remember { mutableStateOf("") }
    val offered = remember(entities, dashboard, query) { pickableEntities(entities, dashboard, query) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).clip(RoundedCornerShape(Radius.Card)).background(Tokens.CardRaised).padding(Space.XLarge),
            verticalArrangement = Arrangement.spacedBy(Space.Large),
        ) {
            Text(strings.haPickEntity, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            R1TextField(query, { query = it }, strings.haSearchEntities)
            if (offered.isEmpty()) {
                Text(
                    if (query.isBlank()) strings.haNothingToPick else strings.haNoEntityMatches,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(offered, key = { it.id.value }) { entity ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clip(RoundedCornerShape(Radius.Row))
                                .tappable(role = Role.Button) { onPick(entity.id) }
                                .padding(horizontal = Space.Small, vertical = Space.Small),
                            verticalArrangement = Arrangement.spacedBy(Space.Lines),
                        ) {
                            Text(entity.friendlyName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                entity.id.value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) { TonalButton(strings.cancel, onCancel, Modifier.fillMaxWidth()) }
        }
    }
}
