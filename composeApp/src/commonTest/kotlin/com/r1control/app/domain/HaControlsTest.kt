package com.r1control.app.domain

import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HA_COLORS
import com.r1control.app.domain.model.ha.HA_WHITES
import com.r1control.app.domain.model.ha.HaChip
import com.r1control.app.domain.model.ha.HaLevel
import com.r1control.app.domain.model.ha.HaLevelKind
import com.r1control.app.domain.model.ha.HaRgb
import com.r1control.app.domain.model.ha.HaServiceCall
import com.r1control.app.domain.model.ha.HaTap
import com.r1control.app.domain.model.ha.HaTile
import com.r1control.app.domain.model.ha.TileId
import com.r1control.app.domain.model.ha.brightnessCall
import com.r1control.app.domain.model.ha.brightnessPercent
import com.r1control.app.domain.model.ha.colorCall
import com.r1control.app.domain.model.ha.colorSwatches
import com.r1control.app.domain.model.ha.fanPercent
import com.r1control.app.domain.model.ha.fanPresetMode
import com.r1control.app.domain.model.ha.fanPresetModes
import com.r1control.app.domain.model.ha.fanSpeedCall
import com.r1control.app.domain.model.ha.hasControls
import com.r1control.app.domain.model.ha.isOn
import com.r1control.app.domain.model.ha.isOscillating
import com.r1control.app.domain.model.ha.jsonIntArray
import com.r1control.app.domain.model.ha.jsonStringArray
import com.r1control.app.domain.model.ha.whiteCall
import com.r1control.app.domain.model.ha.whiteSwatches
import com.r1control.app.domain.model.ha.oscillateCall
import com.r1control.app.domain.model.ha.powerToggleCall
import com.r1control.app.domain.model.ha.presetCall
import com.r1control.app.domain.model.ha.sheetChips
import com.r1control.app.domain.model.ha.sheetLevel
import com.r1control.app.domain.model.ha.supportsBrightness
import com.r1control.app.domain.model.ha.supportsFanPreset
import com.r1control.app.domain.model.ha.supportsFanSpeed
import com.r1control.app.domain.model.ha.supportsOscillate
import com.r1control.app.domain.model.ha.tapAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The two entities as the household's Home Assistant reports them (both off). A JSON null attribute never reaches the model. */
internal fun momToiletLight(brightness: Double? = null, state: String = "off") = HaEntity(
    HaEntityId("light.mom_toilet_light"),
    state,
    buildMap {
        put("friendly_name", HaAttribute.Text("Mom toilet light"))
        put("supported_color_modes", HaAttribute.Structured("""["color_temp","xy"]"""))
        put("supported_features", HaAttribute.Number(44.0))
        if (brightness != null) put("brightness", HaAttribute.Number(brightness))
    },
)

internal fun bedroomFan(features: Double = 59.0, state: String = "off") = HaEntity(
    HaEntityId("fan.bam_vao_day_de_chinh_toc_do"),
    state,
    mapOf(
        "friendly_name" to HaAttribute.Text("Fan"),
        "percentage" to HaAttribute.Number(55.0),
        "oscillating" to HaAttribute.Flag(true),
        "preset_mode" to HaAttribute.Text("Straight Wind"),
        "preset_modes" to HaAttribute.Structured("""["Straight Wind","Natural Wind"]"""),
        "supported_features" to HaAttribute.Number(features),
    ),
)

private fun lightWith(modes: String?) = HaEntity(
    HaEntityId("light.x"),
    "on",
    if (modes == null) emptyMap() else mapOf("supported_color_modes" to HaAttribute.Structured(modes)),
)

class HaControlsTest {
    @Test fun aLightWithColourModesIsDimmableEvenWhileOff() {
        val light = momToiletLight()
        assertFalse(light.isOn)
        assertTrue(light.supportsBrightness)
        assertTrue(light.hasControls)
    }

    @Test fun anOnOffOnlyLightIsNotDimmable() {
        assertFalse(lightWith("""["onoff"]""").supportsBrightness)
        assertFalse(lightWith("""["onoff"]""").hasControls)
        assertFalse(lightWith(null).supportsBrightness)
        assertTrue(lightWith("""["brightness"]""").supportsBrightness)
    }

    @Test fun brightnessIsReadAsAPercentAndAbsentIsZero() {
        assertEquals(0, momToiletLight().brightnessPercent)
        assertEquals(0, momToiletLight(0.0, "on").brightnessPercent)
        assertEquals(50, momToiletLight(128.0, "on").brightnessPercent)
        assertEquals(100, momToiletLight(255.0, "on").brightnessPercent)
    }

    @Test fun theFanFeaturesAreABitmask() {
        val fan = bedroomFan()
        assertTrue(fan.supportsFanSpeed)
        assertTrue(fan.supportsOscillate)
        assertTrue(fan.supportsFanPreset)
        assertTrue(fan.hasControls)
        assertFalse(fan.supportsBrightness)
    }

    @Test fun aFanWithNoFeaturesHasNoControls() {
        val fan = bedroomFan(features = 0.0)
        assertFalse(fan.supportsFanSpeed)
        assertFalse(fan.supportsOscillate)
        assertFalse(fan.supportsFanPreset)
        assertFalse(fan.hasControls)
    }

    @Test fun eachFanFeatureBitStandsAlone() {
        assertEquals(listOf(true, false, false), bedroomFan(1.0).let { listOf(it.supportsFanSpeed, it.supportsOscillate, it.supportsFanPreset) })
        assertEquals(listOf(false, true, false), bedroomFan(2.0).let { listOf(it.supportsFanSpeed, it.supportsOscillate, it.supportsFanPreset) })
        assertEquals(listOf(false, false, true), bedroomFan(8.0).let { listOf(it.supportsFanSpeed, it.supportsOscillate, it.supportsFanPreset) })
    }

    @Test fun aCoverWhoseFeaturesHaveTheSameBitsIsNotAFan() {
        val cover = HaEntity(HaEntityId("cover.blind"), "open", mapOf("supported_features" to HaAttribute.Number(59.0)))
        assertFalse(cover.supportsFanSpeed)
        assertFalse(cover.supportsOscillate)
        assertFalse(cover.hasControls)
    }

    @Test fun theFanReadsItsOwnValues() {
        val fan = bedroomFan()
        assertEquals(55, fan.fanPercent)
        assertTrue(fan.isOscillating)
        assertEquals("Straight Wind", fan.fanPresetMode)
        assertEquals(listOf("Straight Wind", "Natural Wind"), fan.fanPresetModes)
    }

    @Test fun aFanThatSaysNothingReadsAsZeroAndEmpty() {
        val fan = HaEntity(HaEntityId("fan.bare"), "off", mapOf("supported_features" to HaAttribute.Number(11.0)))
        assertEquals(0, fan.fanPercent)
        assertFalse(fan.isOscillating)
        assertEquals("", fan.fanPresetMode)
        assertEquals(emptyList(), fan.fanPresetModes)
    }

    @Test fun aStringArrayIsReadInOrder() {
        assertEquals(listOf("a", "b"), jsonStringArray("""["a","b"]"""))
        assertEquals(emptyList(), jsonStringArray("[]"))
        assertEquals(listOf("x"), jsonStringArray("""["x"]"""))
        assertEquals(listOf("Straight Wind", "Natural Wind"), jsonStringArray("""["Straight Wind","Natural Wind"]"""))
        assertEquals(listOf("say \"hi\""), jsonStringArray("""["say \"hi\""]"""))
        assertEquals(emptyList(), jsonStringArray("not an array"))
    }
}

class HaSheetTest {
    @Test fun aLightsLevelIsItsBrightnessEvenWhileOff() {
        assertEquals(HaLevel.Present(HaLevelKind.Brightness, 0), momToiletLight().sheetLevel())
        assertEquals(HaLevel.Present(HaLevelKind.Brightness, 50), momToiletLight(128.0, "on").sheetLevel())
    }

    @Test fun aFansLevelIsItsSpeed() {
        assertEquals(HaLevel.Present(HaLevelKind.FanSpeed, 55), bedroomFan().sheetLevel())
    }

    @Test fun aFanWithPresetsButNoSpeedHasNoLevel() {
        assertEquals(HaLevel.None, bedroomFan(features = 8.0).sheetLevel())
    }

    @Test fun aPlainSwitchHasNoLevel() {
        assertEquals(HaLevel.None, HaEntity(HaEntityId("switch.hall"), "on", emptyMap()).sheetLevel())
    }

    @Test fun aLightsOnlyChipIsItsPower() {
        assertEquals(listOf(HaChip.Power(false)), momToiletLight().sheetChips())
        assertEquals(listOf(HaChip.Power(true)), momToiletLight(128.0, "on").sheetChips())
    }

    @Test fun aFanChipsArePowerThenOscillationThenEveryPreset() {
        assertEquals(
            listOf(
                HaChip.Power(false),
                HaChip.Oscillate(true),
                HaChip.Preset("Straight Wind", true),
                HaChip.Preset("Natural Wind", false),
            ),
            bedroomFan().sheetChips(),
        )
    }

    @Test fun aFanThatOnlyOscillatesHasPowerAndOscillation() {
        assertEquals(listOf(HaChip.Power(false), HaChip.Oscillate(true)), bedroomFan(features = 2.0).sheetChips())
    }
}

class HaLightColourTest {
    private fun colourLight(
        modes: String = """["color_temp","xy"]""",
        state: String = "on",
        rgb: String? = null,
        kelvin: Double? = null,
    ) = HaEntity(
        HaEntityId("light.mom_toilet_light"),
        state,
        buildMap {
            put("supported_color_modes", HaAttribute.Structured(modes))
            if (rgb != null) put("rgb_color", HaAttribute.Structured(rgb))
            if (kelvin != null) put("color_temp_kelvin", HaAttribute.Number(kelvin))
        },
    )

    @Test fun aColourLightOffersColourAndWhiteSwatches() {
        val light = colourLight()
        assertEquals(HA_COLORS.size, light.colorSwatches().size)
        assertEquals(HA_WHITES.size, light.whiteSwatches().size)
    }

    @Test fun anOnOffLightOffersNoSwatches() {
        val plain = colourLight(modes = """["onoff"]""")
        assertTrue(plain.colorSwatches().isEmpty())
        assertTrue(plain.whiteSwatches().isEmpty())
    }

    @Test fun aWhiteOnlyLightOffersWhitesButNoColours() {
        val white = colourLight(modes = """["color_temp"]""")
        assertTrue(white.colorSwatches().isEmpty())
        assertEquals(HA_WHITES.size, white.whiteSwatches().size)
    }

    @Test fun aColourOnlyLightStillOffersWhites() {
        val colour = colourLight(modes = """["xy"]""")
        assertEquals(HA_COLORS.size, colour.colorSwatches().size)
        assertEquals(HA_WHITES.size, colour.whiteSwatches().size)
    }

    @Test fun theLiveColourIsTheMarkedSwatch() {
        val light = colourLight(rgb = "[255, 152, 0]")
        assertEquals(listOf(true), light.colorSwatches().filter { it.active }.map { it.active })
        assertEquals(HaRgb(255, 152, 0), light.colorSwatches().first { it.active }.rgb)
    }

    @Test fun nothingIsMarkedWhileTheLightIsOff() {
        val light = colourLight(state = "off", rgb = "[255, 152, 0]", kelvin = 2700.0)
        assertTrue(light.colorSwatches().none { it.active })
        assertTrue(light.whiteSwatches().none { it.active })
    }

    @Test fun theLiveWhiteIsTheMarkedSwatch() {
        val light = colourLight(kelvin = 2700.0)
        assertEquals(2700, light.whiteSwatches().first { it.active }.white.kelvin)
    }

    @Test fun aColourIsSentAsAnRgbArray() {
        assertEquals(
            HaServiceCall(
                "light",
                "turn_on",
                mapOf("entity_id" to HaAttribute.Text("light.x"), "rgb_color" to HaAttribute.Structured("[255, 152, 0]")),
            ),
            colorCall(HaEntityId("light.x"), HaRgb(255, 152, 0)),
        )
    }

    @Test fun aWhiteIsSentAsKelvin() {
        assertEquals(
            HaServiceCall(
                "light",
                "turn_on",
                mapOf("entity_id" to HaAttribute.Text("light.x"), "color_temp_kelvin" to HaAttribute.Number(2700.0)),
            ),
            whiteCall(HaEntityId("light.x"), 2700),
        )
    }

    @Test fun anIntArrayIsReadInOrder() {
        assertEquals(listOf(255, 152, 0), jsonIntArray("[255, 152, 0]"))
        assertEquals(listOf(255, 152, 0), jsonIntArray("[255,152,0]"))
        assertEquals(emptyList(), jsonIntArray("not an array"))
        assertEquals(emptyList(), jsonIntArray("[]"))
    }
}

class HaControlCallsTest {
    private val light = HaEntityId("light.mom_toilet_light")
    private val fan = HaEntityId("fan.bam_vao_day_de_chinh_toc_do")

    private fun entity(id: HaEntityId) = mapOf("entity_id" to HaAttribute.Text(id.value))

    @Test fun brightnessTurnsTheLightOnAtAPercent() {
        assertEquals(
            HaServiceCall("light", "turn_on", entity(light) + ("brightness_pct" to HaAttribute.Number(50.0))),
            brightnessCall(light, 50),
        )
    }

    @Test fun aPercentOutsideTheRangeIsClamped() {
        assertEquals(HaAttribute.Number(100.0), brightnessCall(light, 140).data["brightness_pct"])
        assertEquals(HaAttribute.Number(0.0), fanSpeedCall(fan, -3).data["percentage"])
    }

    @Test fun fanSpeedSetsAPercentage() {
        assertEquals(
            HaServiceCall("fan", "set_percentage", entity(fan) + ("percentage" to HaAttribute.Number(70.0))),
            fanSpeedCall(fan, 70),
        )
    }

    @Test fun oscillateSetsAFlag() {
        assertEquals(HaServiceCall("fan", "oscillate", entity(fan) + ("oscillating" to HaAttribute.Flag(true))), oscillateCall(fan, true))
        assertEquals(HaAttribute.Flag(false), oscillateCall(fan, false).data["oscillating"])
    }

    @Test fun aPresetIsSetByName() {
        assertEquals(
            HaServiceCall("fan", "set_preset_mode", entity(fan) + ("preset_mode" to HaAttribute.Text("Natural Wind"))),
            presetCall(fan, "Natural Wind"),
        )
    }

    @Test fun powerTogglesInTheEntitysOwnDomain() {
        assertEquals(HaServiceCall("light", "toggle", entity(light)), powerToggleCall(light))
        assertEquals(HaServiceCall("fan", "toggle", entity(fan)), powerToggleCall(fan))
    }
}

class HaTapActionTest {
    private fun toggle(entity: HaEntity) = HaTile.Toggle(TileId("t0"), "", "", entity.id)

    @Test fun aDimmableLightOpensItsControls() {
        assertEquals(HaTap.OpenControls, tapAction(momToiletLight(), toggle(momToiletLight())))
    }

    @Test fun aFanOpensItsControls() {
        assertEquals(HaTap.OpenControls, tapAction(bedroomFan(), toggle(bedroomFan())))
    }

    @Test fun aPlainSwitchStillToggles() {
        val plain = HaEntity(HaEntityId("switch.hall"), "on", emptyMap())
        assertEquals(HaTap.RunTile, tapAction(plain, toggle(plain)))
    }

    @Test fun anOnOffOnlyLightStillToggles() {
        val onoff = HaEntity(HaEntityId("light.x"), "on", mapOf("supported_color_modes" to HaAttribute.Structured("""["onoff"]""")))
        assertEquals(HaTap.RunTile, tapAction(onoff, toggle(onoff)))
    }

    @Test fun aTileWhoseEntityIsNotKnownRunsItsOwnTap() {
        assertEquals(HaTap.RunTile, tapAction(null, toggle(momToiletLight())))
    }

    @Test fun anUnreachableDeviceRunsItsOwnTap() {
        assertEquals(HaTap.RunTile, tapAction(momToiletLight(state = "unavailable"), toggle(momToiletLight())))
    }

    @Test fun onlyAToggleTileOpensControls() {
        val light = momToiletLight()
        val readout = HaTile.Readout(TileId("t1"), "", "", light.id)
        val action = HaTile.Action(TileId("t2"), "", "", light.id, HaServiceCall("script", "go", emptyMap()))
        assertEquals(HaTap.RunTile, tapAction(light, readout))
        assertEquals(HaTap.RunTile, tapAction(light, action))
    }
}
