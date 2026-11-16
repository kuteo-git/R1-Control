package com.r1control.app.domain.model.ha

import kotlin.math.roundToInt

/**
 * What a light or a fan can be asked to do, read from what Home Assistant says it supports. A device that is
 * off still advertises its abilities, so nothing here reads a live value (a light's `brightness` is absent
 * while it is off) to decide whether a control exists. Every helper is gated by the entity's domain, because
 * `supported_features` is a different bitmask on a cover or a media player.
 */

private const val SUPPORTED_COLOR_MODES = "supported_color_modes"
private const val SUPPORTED_FEATURES = "supported_features"
private const val ON_OFF_ONLY = "onoff"

private const val FAN_SET_SPEED = 1
private const val FAN_OSCILLATE = 2
private const val FAN_PRESET_MODE = 8

val HaEntity.isOn: Boolean get() = state == "on"

/** The entity is there but Home Assistant cannot reach the device: nothing can be set. */
val HaEntity.isUnavailable: Boolean get() = state == "unavailable"

private fun HaEntity.number(key: String): Double? = (attributes[key] as? HaAttribute.Number)?.value

private fun HaEntity.features(): Int = number(SUPPORTED_FEATURES)?.toInt() ?: 0

// ---- light ----

/** A light with any colour mode other than plain on/off can be dimmed. */
val HaEntity.supportsBrightness: Boolean
    get() = domain == HaDomain.Light &&
        (attributes[SUPPORTED_COLOR_MODES] as? HaAttribute.Structured)?.let { modes ->
            jsonStringArray(modes.json).any { it != ON_OFF_ONLY }
        } == true

/** The light's brightness, 0 to 100. Home Assistant says 0 to 255, and nothing while the light is off. */
val HaEntity.brightnessPercent: Int
    get() = ((number("brightness") ?: 0.0) / 255.0 * 100.0).roundToInt().coerceIn(0, 100)

// ---- fan ----

val HaEntity.supportsFanSpeed: Boolean get() = domain == HaDomain.Fan && (features() and FAN_SET_SPEED) != 0

val HaEntity.supportsOscillate: Boolean get() = domain == HaDomain.Fan && (features() and FAN_OSCILLATE) != 0

val HaEntity.supportsFanPreset: Boolean get() = domain == HaDomain.Fan && (features() and FAN_PRESET_MODE) != 0

/** The fan's speed, 0 to 100; 0 when Home Assistant says none. */
val HaEntity.fanPercent: Int get() = (number("percentage") ?: 0.0).roundToInt().coerceIn(0, 100)

val HaEntity.isOscillating: Boolean get() = (attributes["oscillating"] as? HaAttribute.Flag)?.value == true

/** The fan's current preset, empty when it has none. */
val HaEntity.fanPresetMode: String get() = (attributes["preset_mode"] as? HaAttribute.Text)?.value.orEmpty()

val HaEntity.fanPresetModes: List<String>
    get() = (attributes["preset_modes"] as? HaAttribute.Structured)?.let { jsonStringArray(it.json) } ?: emptyList()

/** Whether the entity has anything to set beyond on and off. */
val HaEntity.hasControls: Boolean
    get() = supportsBrightness || supportsFanSpeed || supportsOscillate || supportsFanPreset

/**
 * The strings of a compact JSON array of strings (`["a","b"]`), in order. Anything that is not an array of
 * strings gives what could be read from it, never an error: Home Assistant's attribute is shown, not trusted.
 * Only the escapes `\"` and `\\` are understood.
 */
fun jsonStringArray(json: String): List<String> {
    val out = mutableListOf<String>()
    var i = json.indexOf('[')
    if (i < 0) return out
    i++
    while (i < json.length) {
        when (json[i]) {
            ']' -> return out
            '"' -> {
                val value = StringBuilder()
                i++
                while (i < json.length && json[i] != '"') {
                    if (json[i] == '\\' && i + 1 < json.length) i++
                    value.append(json[i])
                    i++
                }
                out += value.toString()
            }
        }
        i++
    }
    return out
}

/**
 * The integers of a compact JSON array of numbers (`[255, 152, 0]`), in order. Anything that is not an array
 * of integers gives what could be read from it, never an error. Decimals are read up to the point, which is
 * enough for `rgb_color`.
 */
fun jsonIntArray(json: String): List<Int> {
    val out = mutableListOf<Int>()
    var i = json.indexOf('[')
    if (i < 0) return out
    i++
    val number = StringBuilder()
    fun flush() {
        number.toString().toIntOrNull()?.let { out += it }
        number.clear()
    }
    while (i < json.length) {
        val c = json[i]
        when {
            c == ']' -> { flush(); return out }
            c == ',' -> flush()
            c == '-' || c.isDigit() -> number.append(c)
        }
        i++
    }
    flush()
    return out
}

// ---- service calls ----

private fun fieldCall(entity: HaEntityId, domain: String, service: String, field: String, value: HaAttribute) =
    HaServiceCall(domain, service, mapOf(HaServiceCall.ENTITY_ID to HaAttribute.Text(entity.value), field to value))

/** On or off, whichever it is not now: a light's or a fan's own `toggle`. */
fun powerToggleCall(entity: HaEntityId): HaServiceCall = HaServiceCall.on(entity, entity.domainName, "toggle")

/** Sets the brightness, 0 to 100; Home Assistant turns the light on with it. */
fun brightnessCall(entity: HaEntityId, percent: Int): HaServiceCall =
    fieldCall(entity, "light", "turn_on", "brightness_pct", HaAttribute.Number(percent.coerceIn(0, 100).toDouble()))

fun fanSpeedCall(entity: HaEntityId, percent: Int): HaServiceCall =
    fieldCall(entity, "fan", "set_percentage", "percentage", HaAttribute.Number(percent.coerceIn(0, 100).toDouble()))

fun oscillateCall(entity: HaEntityId, on: Boolean): HaServiceCall =
    fieldCall(entity, "fan", "oscillate", "oscillating", HaAttribute.Flag(on))

fun presetCall(entity: HaEntityId, mode: String): HaServiceCall =
    fieldCall(entity, "fan", "set_preset_mode", "preset_mode", HaAttribute.Text(mode))

/** Sets the light's colour; Home Assistant turns it on with it. The array is sent as Home Assistant's `rgb_color`. */
fun colorCall(entity: HaEntityId, rgb: HaRgb): HaServiceCall =
    fieldCall(entity, "light", "turn_on", "rgb_color", HaAttribute.Structured("[${rgb.r}, ${rgb.g}, ${rgb.b}]"))

/** Sets the light's white temperature in kelvin; Home Assistant turns it on with it. */
fun whiteCall(entity: HaEntityId, kelvin: Int): HaServiceCall =
    fieldCall(entity, "light", "turn_on", "color_temp_kelvin", HaAttribute.Number(kelvin.toDouble()))

// ---- the sheet ----

/** The one continuous value a light or fan has: a light's brightness or a fan's speed. Each wears its own glyph. */
enum class HaLevelKind { Brightness, FanSpeed }

/** The draggable level of the controls sheet, or [None] for a device that has only switches and presets. */
sealed interface HaLevel {
    data object None : HaLevel
    data class Present(val kind: HaLevelKind, val percent: Int) : HaLevel
}

/**
 * One chip in the controls sheet: an on/off or a pick-one, drawn in a row under the level. [active] lit means
 * on or chosen. The kinds the UI knows how to draw; a [Preset] carries the mode name, which is Home
 * Assistant's own word and never translated.
 */
sealed interface HaChip {
    val active: Boolean
    data class Power(override val active: Boolean) : HaChip
    data class Oscillate(override val active: Boolean) : HaChip
    data class Preset(val mode: String, override val active: Boolean) : HaChip
}

/** The sheet's single draggable value: a light's brightness, else a fan's speed, else nothing to drag. */
fun HaEntity.sheetLevel(): HaLevel = when {
    supportsBrightness -> HaLevel.Present(HaLevelKind.Brightness, brightnessPercent)
    supportsFanSpeed -> HaLevel.Present(HaLevelKind.FanSpeed, fanPercent)
    else -> HaLevel.None
}

/** The sheet's chips, in order: power first, then a fan's oscillation, then one chip per preset mode. */
fun HaEntity.sheetChips(): List<HaChip> = buildList {
    add(HaChip.Power(isOn))
    if (supportsOscillate) add(HaChip.Oscillate(isOscillating))
    if (supportsFanPreset) fanPresetModes.forEach { add(HaChip.Preset(it, it == fanPresetMode)) }
}

// ---- light colour ----

private const val COLOR_TEMP_MODE = "color_temp"
private val FULL_COLOR_MODES = setOf("hs", "rgb", "rgbw", "rgbww", "xy")

/** An 8-bit colour, as Home Assistant's `rgb_color` is. */
data class HaRgb(val r: Int, val g: Int, val b: Int)

/** A white tone the sheet offers: [kelvin] is what is set, [tint] is roughly how it looks for the circle. */
data class HaWhite(val kelvin: Int, val tint: HaRgb)

/** One colour circle in the sheet, lit when the light is on that colour now. */
data class HaColorSwatch(val rgb: HaRgb, val active: Boolean)

/** One white circle in the sheet, lit when the light is on that tone now. */
data class HaWhiteSwatch(val white: HaWhite, val active: Boolean)

/** The quick colours the sheet offers, like Home Assistant's swatch grid: warm to cool, then the colours. */
val HA_COLORS: List<HaRgb> = listOf(
    HaRgb(255, 152, 0),
    HaRgb(255, 193, 120),
    HaRgb(255, 224, 178),
    HaRgb(255, 255, 255),
    HaRgb(104, 159, 255),
    HaRgb(179, 136, 255),
    HaRgb(240, 128, 240),
    HaRgb(244, 98, 74),
)

/** The quick whites, warmest first; [tint] is a plausible look for each temperature. */
val HA_WHITES: List<HaWhite> = listOf(
    HaWhite(2700, HaRgb(255, 180, 107)),
    HaWhite(3500, HaRgb(255, 209, 163)),
    HaWhite(4500, HaRgb(255, 236, 224)),
    HaWhite(6500, HaRgb(214, 229, 255)),
)

private val HaEntity.colorModes: List<String>
    get() = (attributes[SUPPORTED_COLOR_MODES] as? HaAttribute.Structured)?.let { jsonStringArray(it.json) } ?: emptyList()

/** A light that can show a colour (any mode past white). */
val HaEntity.supportsColor: Boolean get() = domain == HaDomain.Light && colorModes.any { it in FULL_COLOR_MODES }

/** A light whose white can be warmed or cooled. */
val HaEntity.supportsColorTemp: Boolean get() = domain == HaDomain.Light && COLOR_TEMP_MODE in colorModes

/** The light's live colour, empty when it reports none (it is off, or on a white). */
val HaEntity.lightRgb: List<Int> get() = (attributes["rgb_color"] as? HaAttribute.Structured)?.let { jsonIntArray(it.json) } ?: emptyList()

/** The light's live white temperature in kelvin, 0 when it reports none. */
val HaEntity.lightKelvin: Int get() = number("color_temp_kelvin")?.toInt() ?: 0

/** The colour swatches, with the live one marked, or empty when the light has no colour. */
fun HaEntity.colorSwatches(): List<HaColorSwatch> =
    if (!supportsColor) emptyList() else HA_COLORS.map { HaColorSwatch(it, isOn && lightRgb == listOf(it.r, it.g, it.b)) }

/**
 * The white swatches, with the live one marked, or empty when the light cannot show a white. Shown for a
 * colour light too, not only a `color_temp` one: Home Assistant converts a kelvin to the colour it supports,
 * the same as its own more-info does for such a light.
 */
fun HaEntity.whiteSwatches(): List<HaWhiteSwatch> =
    if (!supportsColorTemp && !supportsColor) emptyList() else HA_WHITES.map { HaWhiteSwatch(it, isOn && lightKelvin == it.kelvin) }

// ---- the tap ----

/** What a tap on a tile does: show the entity's controls, or run the tile's own tap. */
enum class HaTap { OpenControls, RunTile }

/**
 * A toggle tile on a live entity that has controls opens them, and its on/off lives in the sheet. Every other
 * tile, and a toggle whose entity is not known or cannot be reached, runs its own tap as before.
 */
fun tapAction(entity: HaEntity?, tile: HaTile): HaTap =
    if (tile is HaTile.Toggle && entity != null && !entity.isUnavailable && entity.hasControls) HaTap.OpenControls else HaTap.RunTile
