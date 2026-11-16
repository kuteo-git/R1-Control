package com.r1control.app.domain.model.ha

/** The dashboard the app shows: the person's own layout, seeded from Home Assistant once and edited here since (HaLayoutEdits.kt). Ordered. */
data class HaDashboard(val sections: List<HaSection>) {
    val tileCount: Int get() = sections.sumOf { it.tiles.size }

    companion object {
        val Empty = HaDashboard(emptyList())
    }
}

/** A titled group of tiles. An empty [title] is an untitled group. */
data class HaSection(val id: SectionId, val title: String, val tiles: List<HaTile>)

/** App-assigned and stable, so a section can be named by an edit later. */
@kotlin.jvm.JvmInline
value class SectionId(val value: String)

@kotlin.jvm.JvmInline
value class TileId(val value: String)

/** Maps straight onto `HaRepository.callService`. */
data class HaServiceCall(val domain: String, val service: String, val data: Map<String, HaAttribute>) {
    companion object {
        /** `<domain>.<service>` on one entity, the shape almost every tap takes. */
        fun on(entity: HaEntityId, domain: String, service: String) =
            HaServiceCall(domain, service, mapOf(ENTITY_ID to HaAttribute.Text(entity.value)))

        const val ENTITY_ID = "entity_id"

        /** A tap that does nothing: the call of a template whose card names no action. */
        val None = HaServiceCall("", "", emptyMap())
    }

    val isNone: Boolean get() = domain.isEmpty() && service.isEmpty()
}

/**
 * A tile is layout, not state. It carries the entity id and the seeded display (name, icon) only.
 * The live state is looked up in `HaRepository.entities` by [entity] when the tile is drawn, so a
 * tile and its entity can never disagree.
 */
sealed interface HaTile {
    val id: TileId

    /** Home Assistant's own name for what to call it; empty means the entity's live `friendly_name`. */
    val name: String

    /** An mdi icon name as Home Assistant gave it (`mdi:lightbulb`), or empty. Which glyph it becomes is the screen's job. */
    val icon: String

    /** The entity this tile is about; [HaEntityId.None] for an action that names none. */
    val entity: HaEntityId

    /** When the tile is drawn: [HaCondition.Always] unless it came from a `conditional` card. The edit mode shows every tile. */
    val condition: HaCondition

    /** A light, switch, fan or input_boolean: a tap toggles it. */
    data class Toggle(override val id: TileId, override val name: String, override val icon: String, override val entity: HaEntityId, override val condition: HaCondition = HaCondition.Always) : HaTile

    /** A tap runs [call]: a script, a button, a scene, or the service a chip names. */
    data class Action(override val id: TileId, override val name: String, override val icon: String, override val entity: HaEntityId, val call: HaServiceCall, override val condition: HaCondition = HaCondition.Always) : HaTile

    /** A sensor, select, number, text, or anything not handled: shown, never pressed. */
    data class Readout(override val id: TileId, override val name: String, override val icon: String, override val entity: HaEntityId, override val condition: HaCondition = HaCondition.Always) : HaTile

    /** A cover: three controls (open, stop, close) rather than one tap. */
    data class Cover(override val id: TileId, override val name: String, override val icon: String, override val entity: HaEntityId, override val condition: HaCondition = HaCondition.Always) : HaTile

    /** A media player: its state and title, and one control for play or pause. */
    data class Media(override val id: TileId, override val name: String, override val icon: String, override val entity: HaEntityId, override val condition: HaCondition = HaCondition.Always) : HaTile

    /**
     * A card whose text is a Jinja [template], rendered live by Home Assistant (never here). A tap runs [call],
     * or does nothing when it is [HaServiceCall.None]. [entity] is [HaEntityId.None] when the card names none.
     */
    data class Template(
        override val id: TileId,
        override val name: String,
        override val icon: String,
        override val entity: HaEntityId,
        override val condition: HaCondition = HaCondition.Always,
        val template: String,
        val call: HaServiceCall,
    ) : HaTile
}

enum class HaCoverCommand(val service: String) {
    Open("open_cover"),
    Close("close_cover"),
    Stop("stop_cover"),
}

/** What a tap on the tile runs, or null when a tap does nothing (a readout, or a tile with its own controls). */
fun HaTile.tapCall(): HaServiceCall? = when (this) {
    is HaTile.Toggle -> HaServiceCall.on(entity, entity.domainName, "toggle")
    is HaTile.Action -> call
    is HaTile.Template -> call.takeUnless { it.isNone }
    is HaTile.Readout, is HaTile.Cover, is HaTile.Media -> null
}

fun HaTile.Cover.call(command: HaCoverCommand): HaServiceCall = HaServiceCall.on(entity, "cover", command.service)

fun HaTile.Media.playPauseCall(): HaServiceCall = HaServiceCall.on(entity, "media_player", "media_play_pause")

/** Whether the tile is drawn lit: a toggle that is on, a cover that is not closed, a player that is playing. */
fun HaTile.isLitBy(entity: HaEntity?): Boolean {
    val state = entity?.state ?: return false
    return when (this) {
        is HaTile.Toggle -> state == "on"
        is HaTile.Cover -> state == "open" || state == "opening" || state == "closing"
        is HaTile.Media -> state == "playing"
        is HaTile.Action, is HaTile.Readout, is HaTile.Template -> false
    }
}

/** The seeded name, else the entity's own live name, else the id. A template tile with no name is named by its text ([templateSummary]). */
fun HaTile.titleFor(entity: HaEntity?): String = when (this) {
    is HaTile.Template -> name.ifBlank { templateSummary(template) }
    else -> name.ifBlank { entity?.friendlyName ?: this.entity.value }
}

private val templateTags = Regex("""\{%.*?%\}|\{#.*?#\}""", RegexOption.DOT_MATCHES_ALL)

/**
 * A line that says roughly what the template shows, for a place that cannot render it (the edit list): the
 * first non-blank line left once the `{% %}` and `{# #}` tags are removed, its blanks collapsed. The template
 * itself when nothing is left.
 */
fun templateSummary(template: String): String =
    template.replace(templateTags, " ").lineSequence()
        .map { it.trim().replace(Regex("\\s+"), " ") }
        .firstOrNull { it.isNotEmpty() }
        ?: template.trim()

/** Whether a tap does anything at all. */
val HaTile.isPressable: Boolean get() = tapCall() != null
