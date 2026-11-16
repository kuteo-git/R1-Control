package com.r1control.app.ui.ha

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaChip
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaLevel
import com.r1control.app.domain.model.ha.HaLevelKind
import com.r1control.app.domain.model.ha.HaRgb
import com.r1control.app.domain.model.ha.colorSwatches
import com.r1control.app.domain.model.ha.isUnavailable
import com.r1control.app.domain.model.ha.sheetChips
import com.r1control.app.domain.model.ha.sheetLevel
import com.r1control.app.domain.model.ha.whiteSwatches
import com.r1control.app.ui.components.R1Icon
import com.r1control.app.ui.components.VerticalLevelSlider
import com.r1control.app.ui.components.tappable
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Radius
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens
import org.jetbrains.compose.ui.tooling.preview.Preview

/** What the controls sheet can ask for. The screen hands these to the view model; previews hand [None]. */
class HaControlActions(
    val onPower: (HaEntityId) -> Unit,
    val onBrightness: (HaEntityId, Int) -> Unit,
    val onFanSpeed: (HaEntityId, Int) -> Unit,
    val onOscillate: (HaEntityId, Boolean) -> Unit,
    val onPreset: (HaEntityId, String) -> Unit,
    val onColor: (HaEntityId, HaRgb) -> Unit,
    val onWhite: (HaEntityId, Int) -> Unit,
) {
    companion object {
        val None = HaControlActions({}, { _, _ -> }, { _, _ -> }, { _, _ -> }, { _, _ -> }, { _, _ -> }, { _, _ -> })
    }
}

/**
 * The controls of one light or fan over the page. [entity] is the live entity: Home Assistant pushing a new
 * state redraws the sheet, so what it shows is what the device says, not what was last asked of it.
 */
@Composable
fun HaControlDialog(entity: HaEntity, actions: HaControlActions, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        HaControlSheet(entity, actions)
    }
}

/**
 * The sheet is one shape for every controllable thing: a drag of the level (a light's brightness or a fan's
 * speed, when it has one) above a row of chips (power, and a fan's oscillation and preset modes). Everything
 * is centred, with no title and no close: a tap outside the sheet dismisses it. The domain decides which
 * parts exist from what the device supports; this only draws them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HaControlSheet(entity: HaEntity, actions: HaControlActions) {
    val strings = LocalStrings.current
    val id = entity.id
    val reachable = !entity.isUnavailable
    val level = entity.sheetLevel()
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .clip(RoundedCornerShape(Radius.Card))
            .background(Tokens.CardRaised)
            .padding(Space.XLarge)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
            if (level is HaLevel.Present) {
                VerticalLevelSlider(
                    percent = level.percent,
                    enabled = reachable,
                    icon = levelIcon(level.kind),
                    description = levelLabel(level.kind, strings),
                    valueText = strings::percent,
                    onCommit = { value ->
                        when (level.kind) {
                            HaLevelKind.Brightness -> actions.onBrightness(id, value)
                            HaLevelKind.FanSpeed -> actions.onFanSpeed(id, value)
                        }
                    },
                )
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.Small, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Space.Small),
            ) {
                entity.sheetChips().forEach { chip ->
                    IconChip(chipIcon(chip), chipLabel(chip, strings), chip.active, reachable) {
                        when (chip) {
                            is HaChip.Power -> actions.onPower(id)
                            is HaChip.Oscillate -> actions.onOscillate(id, !chip.active)
                            is HaChip.Preset -> actions.onPreset(id, chip.mode)
                        }
                    }
                }
            }
            val colors = entity.colorSwatches()
            if (colors.isNotEmpty()) {
                SwatchRow {
                    colors.forEach { swatch ->
                        Swatch(swatch.rgb, swatch.active, reachable, strings.haColor) { actions.onColor(id, swatch.rgb) }
                    }
                }
            }
            val whites = entity.whiteSwatches()
            if (whites.isNotEmpty()) {
                SwatchRow {
                    whites.forEach { swatch ->
                        Swatch(swatch.white.tint, swatch.active, reachable, strings.haWhite) { actions.onWhite(id, swatch.white.kelvin) }
                    }
                }
            }
    }
}

/** A pill with a glyph and a word, filled when it is on or chosen. Light when active, so the icon reads coloured. */
@Composable
private fun IconChip(icon: ImageVector, label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val background = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val content = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
    Row(
        Modifier
            .heightIn(min = 38.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape)
            .background(background)
            .tappable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Lines),
    ) {
        R1Icon(icon, null, tint = content, size = 16.dp)
        Text(label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

/** A wrapped row of round colour circles, as Home Assistant's swatch grid is. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwatchRow(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.Small, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(Space.Small),
    ) {
        content()
    }
}

/** One colour circle. Lit shows a check in a shade that reads on the colour, so white reads too. */
@Composable
private fun Swatch(rgb: HaRgb, active: Boolean, enabled: Boolean, description: String, onClick: () -> Unit) {
    val color = Color(rgb.r, rgb.g, rgb.b)
    Box(
        Modifier
            .size(36.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape)
            .background(color)
            .tappable(enabled = enabled, role = Role.RadioButton, selected = active, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (active) R1Icon(R1Icons.Check, null, tint = readableOn(rgb), size = 18.dp)
    }
}

/** Black or white, whichever reads on [rgb], by its rough brightness. */
private fun readableOn(rgb: HaRgb): Color =
    if ((rgb.r * 299 + rgb.g * 587 + rgb.b * 114) / 1000 > 150) Color.Black else Color.White

private fun levelIcon(kind: HaLevelKind): ImageVector = when (kind) {
    HaLevelKind.Brightness -> R1Icons.Lightbulb
    HaLevelKind.FanSpeed -> R1Icons.Fan
}

private fun levelLabel(kind: HaLevelKind, strings: Strings): String = when (kind) {
    HaLevelKind.Brightness -> strings.haBrightness
    HaLevelKind.FanSpeed -> strings.haFanSpeed
}

private fun chipIcon(chip: HaChip): ImageVector = when (chip) {
    is HaChip.Power -> R1Icons.Power
    is HaChip.Oscillate -> R1Icons.Oscillate
    is HaChip.Preset -> R1Icons.Fan
}

private fun chipLabel(chip: HaChip, strings: Strings): String = when (chip) {
    is HaChip.Power -> strings.haPower
    is HaChip.Oscillate -> strings.haOscillation
    is HaChip.Preset -> chip.mode
}

// ---- previews ----

@Composable
private fun PreviewHost(strings: Strings, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalStrings provides strings) {
        R1Theme { Box(Modifier.background(Tokens.Ground).padding(Space.XLarge)) { content() } }
    }
}

private val previewLight = HaEntity(
    HaEntityId("light.mom_toilet_light"),
    "on",
    mapOf(
        HaEntity.FRIENDLY_NAME to HaAttribute.Text("Mom toilet light"),
        "supported_color_modes" to HaAttribute.Structured("""["color_temp","xy"]"""),
        "brightness" to HaAttribute.Number(128.0),
        "rgb_color" to HaAttribute.Structured("[255, 152, 0]"),
    ),
)

private val previewFan = HaEntity(
    HaEntityId("fan.bam_vao_day_de_chinh_toc_do"),
    "on",
    mapOf(
        HaEntity.FRIENDLY_NAME to HaAttribute.Text("Bedroom fan"),
        "percentage" to HaAttribute.Number(55.0),
        "oscillating" to HaAttribute.Flag(true),
        "preset_mode" to HaAttribute.Text("Straight Wind"),
        "preset_modes" to HaAttribute.Structured("""["Straight Wind","Natural Wind"]"""),
        "supported_features" to HaAttribute.Number(59.0),
    ),
)

@Preview
@Composable
private fun LightControlsPreview() = PreviewHost(EnglishStrings) { HaControlSheet(previewLight, HaControlActions.None) }

@Preview
@Composable
private fun FanControlsPreview() = PreviewHost(EnglishStrings) { HaControlSheet(previewFan, HaControlActions.None) }

@Preview
@Composable
private fun FanControlsVietnamesePreview() = PreviewHost(VietnameseStrings) { HaControlSheet(previewFan, HaControlActions.None) }

@Preview
@Composable
private fun LightControlsUnavailablePreview() = PreviewHost(EnglishStrings) { HaControlSheet(previewLight.copy(state = "unavailable"), HaControlActions.None) }
