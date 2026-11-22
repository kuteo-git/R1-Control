package com.r1control.app.ui

import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaCondition
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaServiceCall
import com.r1control.app.domain.model.ha.HaTile
import com.r1control.app.domain.model.ha.TileId
import com.r1control.app.ui.ha.iconNameFor
import com.r1control.app.ui.ha.secondLine
import com.r1control.app.ui.ha.tileSubtitle
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.ha.tileRowsOf
import com.r1control.app.ui.icons.R1Icons
import kotlin.test.Test
import kotlin.test.assertEquals

private fun toggle(n: Int, entity: String = "switch.s$n", icon: String = "") = HaTile.Toggle(TileId("t$n"), "", icon, HaEntityId(entity))
private fun cover(n: Int) = HaTile.Cover(TileId("t$n"), "", "", HaEntityId("cover.c$n"))
private fun media(n: Int) = HaTile.Media(TileId("t$n"), "", "", HaEntityId("media_player.m$n"))
private fun camera(n: Int) = HaTile.Camera(TileId("t$n"), "", "", HaEntityId("camera.c$n"))

class HaTilesTest {
    private fun ids(rows: List<List<HaTile>>) = rows.map { row -> row.map { it.id.value } }

    @Test fun tilesSitTwoToARowInOrder() {
        assertEquals(listOf(listOf("t0", "t1"), listOf("t2", "t3"), listOf("t4")), ids(tileRowsOf((0..4).map(::toggle))))
    }

    @Test fun aCoverAPlayerAndACameraTakeAWholeRowAndEndThePairBeforeThem() {
        assertEquals(
            listOf(listOf("t0"), listOf("t1"), listOf("t2", "t3"), listOf("t4"), listOf("t5"), listOf("t6")),
            ids(tileRowsOf(listOf(toggle(0), cover(1), toggle(2), toggle(3), media(4), camera(5), toggle(6)))),
        )
    }

    @Test fun noTilesAreNoRows() {
        assertEquals(emptyList(), tileRowsOf(emptyList()))
    }

    @Test fun theGlyphFollowsTheSeededIconThenWhatTheTileDoes() {
        // Its own seeded mdi icon wins, drawn exactly, on or off.
        assertEquals("lightbulb", iconNameFor(toggle(0, icon = "mdi:lightbulb"), lit = true))
        assertEquals("lightbulb", iconNameFor(toggle(0, icon = "mdi:lightbulb"), lit = false))
        assertEquals("coolant-temperature", iconNameFor(HaTile.Readout(TileId("t"), "", "mdi:coolant-temperature", HaEntityId("sensor.t")), lit = false))
        assertEquals("fan", iconNameFor(toggle(0, "switch.fan", "mdi:fan"), lit = true))
        // No icon of its own: a light falls to the bulb, outline when off.
        assertEquals("lightbulb", iconNameFor(toggle(0, "light.hall"), lit = true))
        assertEquals("lightbulb-outline", iconNameFor(toggle(0, "light.hall"), lit = false))
        assertEquals("play", iconNameFor(HaTile.Action(TileId("t"), "", "", HaEntityId("script.x"), HaServiceCall("script", "x", emptyMap())), lit = false))
    }

    @Test fun theSecondLineIsTheStateTheSongOrNothing() {
        val temperature = HaEntity(HaEntityId("sensor.t"), "21.5", mapOf(HaEntity.UNIT to HaAttribute.Text("°C")))
        assertEquals("21.5 °C", secondLine(HaTile.Readout(TileId("t"), "", "", temperature.id), temperature))
        val playing = HaEntity(HaEntityId("media_player.m"), "playing", mapOf("media_title" to HaAttribute.Text("Suong Trang")))
        assertEquals("Suong Trang", secondLine(media(1), playing))
        assertEquals("idle", secondLine(media(1), HaEntity(HaEntityId("media_player.m"), "idle", emptyMap())))
        assertEquals("", secondLine(HaTile.Action(TileId("t"), "", "", HaEntityId("script.x"), HaServiceCall("script", "x", emptyMap())), temperature))
        assertEquals("", secondLine(toggle(0), null))
    }

    @Test fun aTemplateTileSaysNothingUnderItsText() {
        val tile = HaTile.Template(TileId("t"), "", "", HaEntityId("sensor.t"), template = "x", call = HaServiceCall.None)
        assertEquals("", secondLine(tile, HaEntity(HaEntityId("sensor.t"), "21", emptyMap())))
    }

    @Test fun aTemplateTileSitsTwoToARowLikeAToggle() {
        val template = { n: Int -> HaTile.Template(TileId("t$n"), "", "", HaEntityId.None, template = "x", call = HaServiceCall.None) }
        assertEquals(listOf(listOf("t0", "t1"), listOf("t2")), ids(tileRowsOf((0..2).map(template))))
    }

    @Test fun theEditRowNamesTheEntityTheKindAndTheConditionInThatOrder() {
        val template = HaTile.Template(TileId("t"), "", "", HaEntityId.None, template = "x", call = HaServiceCall.None)
        assertEquals("Template", tileSubtitle(template, EnglishStrings))
        val shown = template.copy(condition = HaCondition.StateIs(HaEntityId("media_player.k"), listOf("playing")))
        assertEquals("Template · Shown only when a condition holds", tileSubtitle(shown, EnglishStrings))
        assertEquals("light.a", tileSubtitle(toggle(0, "light.a"), EnglishStrings))
        assertEquals("light.a · Chỉ hiện khi có điều kiện", tileSubtitle(toggle(0, "light.a").copy(condition = shown.condition), VietnameseStrings))
    }
}
