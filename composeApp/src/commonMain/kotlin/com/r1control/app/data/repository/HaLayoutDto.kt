package com.r1control.app.data.repository

import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaCondition
import com.r1control.app.domain.model.ha.HaDashboard
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaSection
import com.r1control.app.domain.model.ha.HaServiceCall
import com.r1control.app.domain.model.ha.HaTile
import com.r1control.app.domain.model.ha.SectionId
import com.r1control.app.domain.model.ha.TileId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The dashboard layout as it is stored. The domain model carries no wire annotation, so these shapes
// mirror it here and the mappers are the only place that knows both. Nothing in this file prints a value:
// a layout holds the household's device names.

@Serializable
internal data class LayoutDto(val version: Int = VERSION, val sections: List<SectionDto>) {
    companion object {
        const val VERSION = 1
    }
}

@Serializable
internal data class SectionDto(val id: String, val title: String, val tiles: List<TileDto>)

/** One shape for every tile kind: [kind] names which, [call] is present only for an action, or a template that has a tap. */
@Serializable
internal data class TileDto(
    val kind: String,
    val id: String,
    val name: String,
    val icon: String,
    val entity: String,
    val call: CallDto? = null,
    /** Present only for a template: its Jinja text. */
    val template: String? = null,
    /** Absent for a tile that is always drawn, which is also what a layout written before conditions reads as. */
    val condition: ConditionDto? = null,
)

/** [type] is `state`, `state_not`, `above`, `below` or `all`; the other fields are read by the type that names them. */
@Serializable
internal data class ConditionDto(
    val type: String,
    val entity: String = "",
    val states: List<String> = emptyList(),
    val value: Double = 0.0,
    val all: List<ConditionDto> = emptyList(),
)

@Serializable
internal data class CallDto(val domain: String, val service: String, val data: Map<String, AttributeDto>)

/** [type] is `text`, `number`, `flag` or `json`; [text] holds a text, or the compact text of a structured value. */
@Serializable
internal data class AttributeDto(val type: String, val text: String = "", val number: Double = 0.0, val flag: Boolean = false)

private val layoutJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal fun encodeLayout(dashboard: HaDashboard): String = layoutJson.encodeToString(LayoutDto.serializer(), dashboard.toDto())

/** Null when [text] is not a layout this app wrote: not JSON, a newer version, or a tile of a kind it does not know. */
internal fun decodeLayout(text: String): HaDashboard? = try {
    val dto = layoutJson.decodeFromString(LayoutDto.serializer(), text)
    if (dto.version == LayoutDto.VERSION) dto.toDomain() else null
} catch (_: Exception) {
    null
}

private fun HaDashboard.toDto() = LayoutDto(
    sections = sections.map { s -> SectionDto(s.id.value, s.title, s.tiles.map { it.toDto() }) },
)

private fun HaTile.toDto(): TileDto {
    val base = TileDto(kind(), id.value, name, icon, entity.value, condition = condition.toDto())
    return when (this) {
        is HaTile.Action -> base.copy(call = call.toDto())
        is HaTile.Template -> base.copy(template = template, call = call.takeUnless { it.isNone }?.toDto())
        else -> base
    }
}

private fun HaCondition.toDto(): ConditionDto? = when (this) {
    HaCondition.Always -> null
    is HaCondition.StateIs -> ConditionDto(STATE, entity.value, states = oneOf)
    is HaCondition.StateIsNot -> ConditionDto(STATE_NOT, entity.value, states = noneOf)
    is HaCondition.Above -> ConditionDto(ABOVE, entity.value, value = value)
    is HaCondition.Below -> ConditionDto(BELOW, entity.value, value = value)
    is HaCondition.All -> ConditionDto(ALL, all = conditions.mapNotNull { it.toDto() })
}

/** Throws for a type this app does not know, which [decodeLayout] reads as an unreadable layout. */
private fun ConditionDto?.toDomain(): HaCondition = when (this?.type) {
    null -> HaCondition.Always
    STATE -> HaCondition.StateIs(HaEntityId(entity), states)
    STATE_NOT -> HaCondition.StateIsNot(HaEntityId(entity), states)
    ABOVE -> HaCondition.Above(HaEntityId(entity), value)
    BELOW -> HaCondition.Below(HaEntityId(entity), value)
    ALL -> HaCondition.All(all.map { it.toDomain() })
    else -> throw IllegalArgumentException("unknown condition type")
}

private fun HaTile.kind(): String = when (this) {
    is HaTile.Toggle -> TOGGLE
    is HaTile.Action -> ACTION
    is HaTile.Readout -> READOUT
    is HaTile.Cover -> COVER
    is HaTile.Media -> MEDIA
    is HaTile.Template -> TEMPLATE
}

private fun HaServiceCall.toDto() = CallDto(domain, service, data.mapValues { it.value.toDto() })

private fun HaAttribute.toDto(): AttributeDto = when (this) {
    is HaAttribute.Text -> AttributeDto("text", text = value)
    is HaAttribute.Number -> AttributeDto("number", number = value)
    is HaAttribute.Flag -> AttributeDto("flag", flag = value)
    is HaAttribute.Structured -> AttributeDto("json", text = json)
}

private fun LayoutDto.toDomain() = HaDashboard(
    sections.map { s -> HaSection(SectionId(s.id), s.title, s.tiles.map { it.toDomain() }) },
)

/** Throws for a kind this app does not know, which [decodeLayout] reads as an unreadable layout. */
private fun TileDto.toDomain(): HaTile {
    val tile = TileId(id)
    val about = HaEntityId(entity)
    val shownWhen = condition.toDomain()
    return when (kind) {
        TOGGLE -> HaTile.Toggle(tile, name, icon, about, shownWhen)
        ACTION -> HaTile.Action(tile, name, icon, about, requireNotNull(call) { "an action without a call" }.toDomain(), shownWhen)
        READOUT -> HaTile.Readout(tile, name, icon, about, shownWhen)
        COVER -> HaTile.Cover(tile, name, icon, about, shownWhen)
        MEDIA -> HaTile.Media(tile, name, icon, about, shownWhen)
        TEMPLATE -> HaTile.Template(
            tile, name, icon, about, shownWhen,
            template = requireNotNull(template) { "a template without its text" },
            call = call?.toDomain() ?: HaServiceCall.None,
        )
        else -> throw IllegalArgumentException("unknown tile kind")
    }
}

private fun CallDto.toDomain() = HaServiceCall(domain, service, data.mapValues { it.value.toDomain() })

private fun AttributeDto.toDomain(): HaAttribute = when (type) {
    "text" -> HaAttribute.Text(text)
    "number" -> HaAttribute.Number(number)
    "flag" -> HaAttribute.Flag(flag)
    "json" -> HaAttribute.Structured(text)
    else -> throw IllegalArgumentException("unknown attribute type")
}

private const val TOGGLE = "toggle"
private const val ACTION = "action"
private const val READOUT = "readout"
private const val COVER = "cover"
private const val MEDIA = "media"
private const val TEMPLATE = "template"

private const val STATE = "state"
private const val STATE_NOT = "state_not"
private const val ABOVE = "above"
private const val BELOW = "below"
private const val ALL = "all"
