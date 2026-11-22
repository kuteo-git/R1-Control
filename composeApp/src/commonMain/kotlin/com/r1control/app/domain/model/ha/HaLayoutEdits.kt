package com.r1control.app.domain.model.ha

import com.r1control.app.domain.model.moved

// The edits a person can make to the dashboard they own. Every one is pure: it takes the layout and
// returns the next one, never throws, and treats an unknown id or an index out of range as the
// smallest sensible thing (a no-op, or the nearest end). Ids are chosen by the caller, so the same
// edit applied twice converges to the same layout.

/** Appended last. Ignored when a section with [id] is already there. */
fun HaDashboard.addSection(id: SectionId, title: String): HaDashboard =
    if (sections.any { it.id == id }) this else HaDashboard(sections + HaSection(id, title, emptyList()))

fun HaDashboard.renameSection(id: SectionId, title: String): HaDashboard =
    changeSection(id) { it.copy(title = title) }

/** The section and every tile in it. */
fun HaDashboard.deleteSection(id: SectionId): HaDashboard =
    if (sections.none { it.id == id }) this else HaDashboard(sections.filterNot { it.id == id })

/** The section at [from] ends up at index [to] of the result; [to] is clamped into the list. */
fun HaDashboard.moveSection(from: Int, to: Int): HaDashboard {
    if (sections.isEmpty()) return this
    val next = sections.moved(from, to.coerceIn(sections.indices))
    return if (next === sections) this else HaDashboard(next)
}

/** Appended to the section. Ignored when the section is unknown or a tile with the same id is already somewhere. */
fun HaDashboard.addTile(sectionId: SectionId, tile: HaTile): HaDashboard {
    if (sections.none { it.id == sectionId } || hasTile(tile.id)) return this
    return changeSection(sectionId) { it.copy(tiles = it.tiles + tile) }
}

fun HaDashboard.renameTile(id: TileId, name: String): HaDashboard =
    if (!hasTile(id)) this else HaDashboard(sections.map { s -> s.copy(tiles = s.tiles.map { if (it.id == id) it.renamed(name) else it }) })

fun HaDashboard.deleteTile(id: TileId): HaDashboard =
    if (!hasTile(id)) this else HaDashboard(sections.map { s -> s.copy(tiles = s.tiles.filterNot { it.id == id }) })

/**
 * The tile ends up at index [toIndex] of [toSection]'s tiles as they are after it has left its old place,
 * so the same call reorders within a section and moves across sections. [toIndex] is clamped into the
 * section. An unknown tile or section leaves the layout as it was.
 */
fun HaDashboard.moveTile(id: TileId, toSection: SectionId, toIndex: Int): HaDashboard {
    val tile = sections.firstNotNullOfOrNull { s -> s.tiles.firstOrNull { it.id == id } } ?: return this
    if (sections.none { it.id == toSection }) return this
    return HaDashboard(
        sections.map { s ->
            val without = s.tiles.filterNot { it.id == id }
            if (s.id == toSection) s.copy(tiles = without.toMutableList().also { it.add(toIndex.coerceIn(0, without.size), tile) }) else s.copy(tiles = without)
        },
    )
}

fun HaDashboard.hasTile(id: TileId): Boolean = sections.any { s -> s.tiles.any { it.id == id } }

private fun HaDashboard.changeSection(id: SectionId, change: (HaSection) -> HaSection): HaDashboard =
    if (sections.none { it.id == id }) this else HaDashboard(sections.map { if (it.id == id) change(it) else it })

/** The tile with another name; an empty name means the entity's own live name again. */
fun HaTile.renamed(name: String): HaTile = when (this) {
    is HaTile.Toggle -> copy(name = name)
    is HaTile.Action -> copy(name = name)
    is HaTile.Readout -> copy(name = name)
    is HaTile.Cover -> copy(name = name)
    is HaTile.Media -> copy(name = name)
    is HaTile.Template -> copy(name = name)
    is HaTile.Camera -> copy(name = name)
}

// ---- ids ----

/** A section id no section here has: one past the highest `s<number>` in use. */
fun HaDashboard.nextSectionId(): SectionId =
    SectionId("s" + (1 + (sections.maxOfOrNull { numberAfter(it.id.value, 's') } ?: -1)))

/** A tile id no tile here has: one past the highest `t<number>` in use. */
fun HaDashboard.nextTileId(): TileId =
    TileId("t" + (1 + (sections.maxOfOrNull { s -> s.tiles.maxOfOrNull { numberAfter(it.id.value, 't') } ?: -1 } ?: -1)))

/** The number after the prefix letter, or -1 for an id of another shape (it then takes no part in the count). */
private fun numberAfter(id: String, prefix: Char): Int =
    if (id.startsWith(prefix)) id.drop(1).toIntOrNull()?.takeIf { it >= 0 } ?: -1 else -1

// ---- the tile an entity becomes ----

/**
 * The one table of what an entity becomes when nothing says otherwise: the importer of Home Assistant's own
 * dashboard and the picker of the edit mode both come here, so a light is a toggle in both.
 */
fun defaultTile(id: TileId, entity: HaEntityId, name: String = "", icon: String = ""): HaTile = when (entity.domainName) {
    "light", "switch", "fan", "input_boolean" -> HaTile.Toggle(id, name, icon, entity)
    // Home Assistant runs a script by calling the service of its own name.
    "script" -> HaTile.Action(id, name, icon, entity, HaServiceCall("script", entity.objectName, emptyMap()))
    "button" -> HaTile.Action(id, name, icon, entity, HaServiceCall.on(entity, "button", "press"))
    "scene" -> HaTile.Action(id, name, icon, entity, HaServiceCall.on(entity, "scene", "turn_on"))
    "cover" -> HaTile.Cover(id, name, icon, entity)
    "media_player" -> HaTile.Media(id, name, icon, entity)
    "camera" -> HaTile.Camera(id, name, icon, entity)
    else -> HaTile.Readout(id, name, icon, entity)
}

/** The entities the picker offers: those no tile is about yet, whose name or id holds [query] (ignoring case). Order is kept. */
fun pickableEntities(entities: List<HaEntity>, dashboard: HaDashboard, query: String): List<HaEntity> {
    val used = dashboard.sections.flatMapTo(mutableSetOf()) { s -> s.tiles.map { it.entity } }
    val needle = query.trim().lowercase()
    return entities.filter { e ->
        e.id !in used && (needle.isEmpty() || needle in e.friendlyName.lowercase() || needle in e.id.value.lowercase())
    }
}

// ---- availability ----

/**
 * Whether the tile is drawn as unavailable: Home Assistant says so, or does not know the entity at all
 * (removed there, or the link is down and nothing has been heard). A tile naming no entity is never so.
 */
fun HaTile.isUnavailableWith(entity: HaEntity?): Boolean =
    !this.entity.isNone && (entity == null || entity.state == "unavailable")

// ---- the edit list ----

/**
 * The dashboard as one flat column, which is what makes a drag across sections an ordinary reorder:
 * each section's header, then its tiles. A row carries only what draws it, so the column the finger
 * rearranged and the column the layout then yields are equal whenever the layout took the move as it was.
 */
sealed interface HaEditRow {
    /** What a lazy list keys the row by. */
    val key: String

    data class Header(val id: SectionId, val title: String) : HaEditRow {
        override val key: String get() = "section:${id.value}"
    }

    data class TileRow(val tile: HaTile) : HaEditRow {
        override val key: String get() = "tile:${tile.id.value}"
    }
}

fun HaDashboard.editRows(): List<HaEditRow> =
    sections.flatMap { s -> listOf<HaEditRow>(HaEditRow.Header(s.id, s.title)) + s.tiles.map { HaEditRow.TileRow(it) } }

/**
 * What a drag of the flat column from [from] to [to] means for the layout.
 * A tile lands in the section whose header is above where it was dropped, at its place among that
 * section's tiles; dropped above the first header it is the first tile of the first section.
 * A header carries its section: the section lands among the sections where its header was dropped.
 * An index out of range, or no move, changes nothing.
 */
fun HaDashboard.moveRow(from: Int, to: Int): HaDashboard {
    val rows = editRows()
    if (from !in rows.indices || to !in rows.indices || from == to) return this
    val after = rows.moved(from, to)
    return when (val row = rows[from]) {
        is HaEditRow.Header -> moveSection(sections.indexOfFirst { it.id == row.id }, after.take(to).count { it is HaEditRow.Header })
        is HaEditRow.TileRow -> {
            val header = after.take(to).indexOfLast { it is HaEditRow.Header }
            if (header < 0) {
                moveTile(row.tile.id, sections.first().id, 0)
            } else {
                moveTile(row.tile.id, (after[header] as HaEditRow.Header).id, after.subList(header + 1, to).size)
            }
        }
    }
}
