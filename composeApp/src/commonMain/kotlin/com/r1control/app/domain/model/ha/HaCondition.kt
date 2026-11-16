package com.r1control.app.domain.model.ha

/**
 * When a tile is drawn. Home Assistant's `conditional` card shows its inner card only while its conditions
 * hold; the importer turns them into one of these on the tile, and the screen asks [holds] against the live
 * entities. Only what [holds] can decide with an entity's state alone is representable.
 */
sealed interface HaCondition {
    /** A tile with no condition. */
    data object Always : HaCondition

    /** The entity's state is one of [oneOf]. */
    data class StateIs(val entity: HaEntityId, val oneOf: List<String>) : HaCondition

    /** The entity's state is none of [noneOf]. */
    data class StateIsNot(val entity: HaEntityId, val noneOf: List<String>) : HaCondition

    /** The entity's state is a number above [value]. */
    data class Above(val entity: HaEntityId, val value: Double) : HaCondition

    /** The entity's state is a number below [value]. */
    data class Below(val entity: HaEntityId, val value: Double) : HaCondition

    /** Every one of [conditions] holds. */
    data class All(val conditions: List<HaCondition>) : HaCondition
}

/**
 * Whether this condition holds for the live [entities]. An entity that is not there fails every state and
 * numeric test, [HaCondition.StateIsNot] included: a tile waiting on an entity nobody has heard of stays hidden.
 * A state that is not a number fails [HaCondition.Above] and [HaCondition.Below].
 */
fun HaCondition.holds(entities: Map<HaEntityId, HaEntity>): Boolean = when (this) {
    HaCondition.Always -> true
    is HaCondition.StateIs -> entities[entity]?.let { it.state in oneOf } ?: false
    is HaCondition.StateIsNot -> entities[entity]?.let { it.state !in noneOf } ?: false
    is HaCondition.Above -> entities[entity]?.state?.toDoubleOrNull()?.let { it > value } ?: false
    is HaCondition.Below -> entities[entity]?.state?.toDoubleOrNull()?.let { it < value } ?: false
    is HaCondition.All -> conditions.all { it.holds(entities) }
}

/** The conditions as one: [HaCondition.Always] for none, the condition itself for one, else [HaCondition.All] with nested ones flattened. */
fun allOf(conditions: List<HaCondition>): HaCondition {
    val parts = conditions.flatMap { if (it is HaCondition.All) it.conditions else listOf(it) }.filterNot { it == HaCondition.Always }
    return when (parts.size) {
        0 -> HaCondition.Always
        1 -> parts.single()
        else -> HaCondition.All(parts)
    }
}

/** Whether the tile is drawn at all (in view mode; the edit mode shows every tile). */
fun HaTile.isVisibleWith(entities: Map<HaEntityId, HaEntity>): Boolean = condition.holds(entities)

/** The tile with another condition. */
fun HaTile.withCondition(condition: HaCondition): HaTile = when (this) {
    is HaTile.Toggle -> copy(condition = condition)
    is HaTile.Action -> copy(condition = condition)
    is HaTile.Readout -> copy(condition = condition)
    is HaTile.Cover -> copy(condition = condition)
    is HaTile.Media -> copy(condition = condition)
    is HaTile.Template -> copy(condition = condition)
}

/**
 * The sections the view mode draws: each with the tiles whose condition holds, and none that had tiles
 * and now have none, so a title never stands over nothing. A section that was empty to begin with stays.
 */
fun HaDashboard.visibleSections(entities: Map<HaEntityId, HaEntity>): List<HaSection> =
    sections.mapNotNull { section ->
        val shown = section.tiles.filter { it.isVisibleWith(entities) }
        if (shown.isEmpty() && section.tiles.isNotEmpty()) null else section.copy(tiles = shown)
    }
