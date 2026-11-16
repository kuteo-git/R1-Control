package com.r1control.app.data.remote

import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaCondition
import com.r1control.app.domain.model.ha.HaDashboard
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaSection
import com.r1control.app.domain.model.ha.HaServiceCall
import com.r1control.app.domain.model.ha.HaTile
import com.r1control.app.domain.model.ha.SectionId
import com.r1control.app.domain.model.ha.TileId
import com.r1control.app.domain.model.ha.allOf
import com.r1control.app.domain.model.ha.defaultTile
import com.r1control.app.domain.model.ha.withCondition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

// Home Assistant's Lovelace configuration, read once at the boundary and turned into the app's own
// dashboard. Nothing past this file sees JSON. The configuration holds the household's entity names,
// so nothing here prints it, and the exception for a file that is not a configuration carries no text of it.

/** The dashboard, and the cards that were seen but not drawn, counted by card type (`picture-entity`, `chip:template`). */
internal data class DashboardImport(val dashboard: HaDashboard, val skipped: Map<String, Int>)

private val lovelaceJson = Json { ignoreUnknownKeys = true }

/** Throws [IllegalArgumentException] when [configJson] is not a JSON object. */
internal fun importDashboard(configJson: String): HaDashboard = importLovelace(configJson).dashboard

internal fun importLovelace(configJson: String): DashboardImport {
    val root = try {
        lovelaceJson.parseToJsonElement(configJson) as? JsonObject
    } catch (_: Exception) {
        null
    } ?: throw IllegalArgumentException("not a Lovelace configuration")
    val builder = DashboardBuilder()
    for (view in root.objects("views")) {
        builder.startSection(view.text("title").orEmpty())
        // A classic view lists `cards`; a sections view lists sections, each with its own `cards`.
        view.objects("cards").forEach(builder::card)
        view.objects("sections").forEach { section -> section.objects("cards").forEach(builder::card) }
    }
    return builder.finish()
}

/**
 * Walks the cards in order. A view opens a section named for it, a title or heading card opens the next,
 * and every tile lands in the section that is open. Stacks and grids are walked into, not drawn.
 */
private class DashboardBuilder {
    private val sections = mutableListOf<HaSection>()
    private val skipped = mutableMapOf<String, Int>()
    private var title = ""
    private var tiles = mutableListOf<HaTile>()
    private var nextTile = 0

    /** The conditions of the `conditional` cards being walked, outermost first; a tile added meanwhile carries them all. */
    private val conditions = mutableListOf<HaCondition>()

    fun startSection(next: String) {
        closeSection()
        title = next.trim()
    }

    private fun closeSection() {
        // A section nothing landed in is not drawn: a view's own title when its first card is a heading.
        if (tiles.isNotEmpty()) sections += HaSection(SectionId("s${sections.size}"), title, tiles)
        tiles = mutableListOf()
    }

    fun finish(): DashboardImport {
        closeSection()
        return DashboardImport(HaDashboard(sections.toList()), skipped.toMap())
    }

    private fun skip(kind: String) {
        skipped[kind] = (skipped[kind] ?: 0) + 1
    }

    private fun add(build: (TileId) -> HaTile?) {
        val tile = build(TileId("t$nextTile")) ?: return
        nextTile++
        tiles += if (conditions.isEmpty()) tile else tile.withCondition(allOf(conditions))
    }

    fun card(card: JsonObject) {
        when (val type = card.text("type").orEmpty()) {
            "vertical-stack", "horizontal-stack", "grid" -> card.objects("cards").forEach { card(it) }
            "conditional" -> conditional(card)
            "custom:mushroom-title-card" -> card.text("title")?.takeIf { it.isNotBlank() }?.let { startSection(it) }
            "heading" -> card.text("heading")?.takeIf { it.isNotBlank() }?.let { startSection(it) }
            "entities" -> {
                card.text("title")?.takeIf { it.isNotBlank() }?.let { startSection(it) }
                card.rows("entities").forEach { row -> add { id -> tileFor(id, row.entity, row.name, row.icon, row.tap) } }
            }
            "custom:mushroom-entity-card" -> {
                val entity = card.text("entity").orEmpty()
                if (entity.isBlank()) skip(type) else add { id ->
                    tileFor(id, entity, card.name(), card.icon(), card.obj("tap_action"))
                }
            }
            "custom:mushroom-light-card" -> {
                val entity = card.text("entity").orEmpty()
                if (entity.isBlank()) skip(type) else add { id -> HaTile.Toggle(id, card.name(), card.icon(), HaEntityId(entity)) }
            }
            "custom:mushroom-cover-card" -> {
                val entity = card.text("entity").orEmpty()
                if (entity.isBlank()) skip(type) else add { id -> HaTile.Cover(id, card.name(), card.icon(), HaEntityId(entity)) }
            }
            "media-control" -> {
                val entity = card.text("entity").orEmpty()
                if (entity.isBlank()) skip(type) else add { id -> HaTile.Media(id, card.name(), card.icon(), HaEntityId(entity)) }
            }
            "custom:mushroom-template-card" -> {
                val template = card.text("primary").orEmpty()
                val entity = HaEntityId(card.text("entity").orEmpty().trim())
                // A card with no text of its own has nothing for Home Assistant to render.
                if (template.isBlank()) skip(type) else add { id ->
                    // The card's icon is itself a template in Home Assistant; only a plain mdi name is kept.
                    val icon = card.icon().takeIf { it.startsWith("mdi:") }.orEmpty()
                    HaTile.Template(id, card.name(), icon, entity, template = template, call = templateTapOf(card.obj("tap_action"), entity))
                }
            }
            "custom:mushroom-chips-card" -> card.objects("chips").forEach { chip(it) }
            else -> skip(type)
        }
    }

    /** The inner card, shown while the conditions hold. A condition that cannot be represented skips the card, never shows it unconditionally. */
    private fun conditional(wrapper: JsonObject) {
        val inner = wrapper.obj("card")
        val parsed = wrapper.objects("conditions").map { conditionOf(it) }
        if (inner == null || parsed.any { it == null }) return skip("conditional")
        conditions += parsed.filterNotNull()
        try {
            card(inner)
        } finally {
            repeat(parsed.size) { conditions.removeAt(conditions.lastIndex) }
        }
    }

    private fun chip(chip: JsonObject) {
        val type = chip.text("type").orEmpty()
        val entity = chip.text("entity").orEmpty()
        // A chip that is a template, a menu or a back button has no entity to draw.
        if (entity.isBlank() || type == "template") skip("chip:$type") else add { id ->
            tileFor(id, entity, chip.name(), chip.icon(), chip.obj("tap_action"))
        }
    }
}

/**
 * One Lovelace condition: `state` (or the short form with no `condition` key) with one value or a list,
 * `state_not`, `numeric_state` with a number for `above` and `below`, and `and`. Null for anything else
 * (`or`, `not`, `screen`, `user`, a test of an attribute, a limit that is another entity): it cannot be decided
 * from an entity's state alone.
 */
internal fun conditionOf(condition: JsonObject): HaCondition? {
    if (condition.text("attribute") != null) return null
    val entity = condition.text("entity").orEmpty()
    return when (condition.text("condition") ?: if (condition["state"] != null || condition["state_not"] != null) "state" else "") {
        "state" -> {
            if (entity.isBlank()) return null
            val id = HaEntityId(entity)
            val states = condition["state"]?.let(::statesOf)
            val statesNot = condition["state_not"]?.let(::statesOf)
            when {
                states != null && statesNot == null -> HaCondition.StateIs(id, states)
                statesNot != null && states == null -> HaCondition.StateIsNot(id, statesNot)
                else -> null
            }
        }
        "numeric_state" -> {
            if (entity.isBlank()) return null
            val id = HaEntityId(entity)
            val above = condition["above"]?.let { (it as? JsonPrimitive)?.doubleOrNull ?: return null }
            val below = condition["below"]?.let { (it as? JsonPrimitive)?.doubleOrNull ?: return null }
            val parts = listOfNotNull(above?.let { HaCondition.Above(id, it) }, below?.let { HaCondition.Below(id, it) })
            if (parts.isEmpty()) null else allOf(parts)
        }
        "and" -> {
            val parts = condition.objects("conditions").map { conditionOf(it) ?: return null }
            allOf(parts)
        }
        else -> null
    }
}

/** A state a condition names: one value, or a list of them. Null when it is neither or the list is empty. */
private fun statesOf(element: kotlinx.serialization.json.JsonElement): List<String>? = when (element) {
    is JsonPrimitive -> listOf(element.content)
    is JsonArray -> element.map { (it as? JsonPrimitive)?.content ?: return null }.takeIf { it.isNotEmpty() }
    else -> null
}

/** The tile an entity becomes, from its tap action when it has one that does something, else from its domain. */
internal fun tileFor(id: TileId, entityId: String, name: String, icon: String, tap: JsonObject?): HaTile? {
    if (entityId.isBlank()) return null
    val entity = HaEntityId(entityId)
    val byDomain = defaultTile(id, entity, name.trim(), icon)
    return when (tap?.text("action")) {
        // Nothing to press, but a cover and a player keep their own controls.
        "none" -> if (byDomain is HaTile.Toggle || byDomain is HaTile.Action) HaTile.Readout(id, name.trim(), icon, entity) else byDomain
        "toggle" -> HaTile.Toggle(id, name.trim(), icon, entity)
        "call-service", "perform-action" ->
            serviceCallOf(tap)?.let { HaTile.Action(id, name.trim(), icon, entity, it) } ?: byDomain
        else -> byDomain
    }
}

/**
 * What a tap on a template card does: a service, or a toggle of its entity, else nothing. A card that names
 * no action does nothing here, where Home Assistant's own default would toggle the entity.
 */
internal fun templateTapOf(tap: JsonObject?, entity: HaEntityId): HaServiceCall = when (tap?.text("action")) {
    "toggle" -> if (entity.isNone) HaServiceCall.None else HaServiceCall.on(entity, entity.domainName, "toggle")
    "call-service", "perform-action" -> tap?.let(::serviceCallOf) ?: HaServiceCall.None
    else -> HaServiceCall.None
}

/**
 * `service` (or `perform_action`, its newer name) is `<domain>.<service>`; its fields are the card's
 * `target` then `data`, the later one winning. Null when no service is named: an empty `service` is
 * what Home Assistant writes for an action nobody set.
 */
internal fun serviceCallOf(tap: JsonObject): HaServiceCall? {
    val named = tap.text("perform_action")?.takeIf { it.isNotBlank() } ?: tap.text("service").orEmpty()
    val domain = named.substringBefore('.', "")
    val service = named.substringAfter('.', "")
    if (domain.isEmpty() || service.isEmpty()) return null
    val fields = buildMap<String, HaAttribute> {
        tap.obj("target")?.forEach { (key, value) -> attributeOf(value)?.let { put(key, it) } }
        tap.obj("data")?.forEach { (key, value) -> attributeOf(value)?.let { put(key, it) } }
    }
    return HaServiceCall(domain, service, fields)
}

private class Row(val entity: String, val name: String, val icon: String, val tap: JsonObject?)

/** The rows of an `entities` card: each is an id, or an object with the entity and what to call it. */
private fun JsonObject.rows(key: String): List<Row> = (this[key] as? JsonArray).orEmpty().mapNotNull { element ->
    when (element) {
        is JsonObject -> element.text("entity")?.let { Row(it, element.name(), element.icon(), element.obj("tap_action")) }
        else -> (element as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { Row(it, "", "", null) }
    }
}

private fun JsonObject.name(): String = text("name").orEmpty().trim()

private fun JsonObject.icon(): String = text("icon").orEmpty().trim()

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.objects(key: String): List<JsonObject> = (this[key] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
