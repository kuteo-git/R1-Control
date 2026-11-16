package com.r1control.app.domain.model.ha

@kotlin.jvm.JvmInline
value class HaEntityId(val value: String) {
    /** The part before the dot: `light` in `light.kitchen`. */
    val domainName: String get() = value.substringBefore('.')

    /** The part after the dot, empty when the id has none. */
    val objectName: String get() = if ('.' in value) value.substringAfter('.') else ""

    /** An action that names no entity. */
    val isNone: Boolean get() = value.isEmpty()

    companion object {
        val None = HaEntityId("")
    }
}

/** The kinds of entity this app draws differently; everything else is [Other]. */
enum class HaDomain(val prefix: String) {
    Light("light"),
    Switch("switch"),
    Script("script"),
    Fan("fan"),
    Sensor("sensor"),
    BinarySensor("binary_sensor"),
    MediaPlayer("media_player"),
    Cover("cover"),
    Climate("climate"),
    InputBoolean("input_boolean"),
    Button("button"),
    Scene("scene"),
    Other(""),
    ;

    companion object {
        fun of(id: HaEntityId): HaDomain = entries.firstOrNull { it != Other && it.prefix == id.domainName } ?: Other
    }
}

/** An attribute value, with Home Assistant's arrays and objects kept as their compact text: no screen reads inside them yet. */
sealed interface HaAttribute {
    data class Text(val value: String) : HaAttribute
    data class Number(val value: Double) : HaAttribute
    data class Flag(val value: Boolean) : HaAttribute
    data class Structured(val json: String) : HaAttribute
}

/**
 * One entity as Home Assistant last said it. [state] is Home Assistant's own word
 * (`on`, `unavailable`, `21.5`), never translated here. The domain and the name
 * are read from the id and the attributes, so no entity can disagree with itself.
 */
data class HaEntity(
    val id: HaEntityId,
    val state: String,
    val attributes: Map<String, HaAttribute>,
) {
    val domain: HaDomain get() = HaDomain.of(id)

    /** `friendly_name`, or the id's own name when Home Assistant gave none. */
    val friendlyName: String
        get() = (attributes[FRIENDLY_NAME] as? HaAttribute.Text)?.value?.takeIf { it.isNotBlank() } ?: id.value

    /** Sensors carry the unit their number is in; empty for everything else. */
    val unit: String get() = (attributes[UNIT] as? HaAttribute.Text)?.value.orEmpty()

    companion object {
        const val FRIENDLY_NAME = "friendly_name"
        const val UNIT = "unit_of_measurement"
    }
}

/** The entities in the order a list shows them: by domain name, then by display name, ignoring case. */
fun List<HaEntity>.sortedForList(): List<HaEntity> =
    sortedWith(compareBy<HaEntity>({ it.id.domainName }, { it.friendlyName.lowercase() }, { it.id.value }))
