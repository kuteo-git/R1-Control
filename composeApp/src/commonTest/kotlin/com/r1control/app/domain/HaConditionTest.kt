package com.r1control.app.domain

import com.r1control.app.domain.model.ha.HaCondition
import com.r1control.app.domain.model.ha.HaDashboard
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaSection
import com.r1control.app.domain.model.ha.HaTile
import com.r1control.app.domain.model.ha.SectionId
import com.r1control.app.domain.model.ha.TileId
import com.r1control.app.domain.model.ha.allOf
import com.r1control.app.domain.model.ha.holds
import com.r1control.app.domain.model.ha.isVisibleWith
import com.r1control.app.domain.model.ha.visibleSections
import com.r1control.app.domain.model.ha.withCondition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun id(value: String) = HaEntityId(value)

private fun world(vararg states: Pair<String, String>): Map<HaEntityId, HaEntity> =
    states.associate { (entity, state) -> id(entity) to HaEntity(id(entity), state, emptyMap()) }

class HaConditionTest {
    private val player = id("media_player.kitchen")

    @Test fun alwaysHoldsEvenWithNoEntities() {
        assertTrue(HaCondition.Always.holds(emptyMap()))
    }

    @Test fun stateIsHoldsForAnyOfItsStates() {
        val playingOrPaused = HaCondition.StateIs(player, listOf("playing", "paused"))
        assertTrue(playingOrPaused.holds(world(player.value to "paused")))
        assertFalse(playingOrPaused.holds(world(player.value to "idle")))
    }

    @Test fun stateIsNotHoldsWhenTheStateIsNoneOfThem() {
        val notOff = HaCondition.StateIsNot(player, listOf("off", "unavailable"))
        assertTrue(notOff.holds(world(player.value to "playing")))
        assertFalse(notOff.holds(world(player.value to "off")))
    }

    @Test fun anAbsentEntityFailsEveryTestEvenANegativeOne() {
        val nothing = emptyMap<HaEntityId, HaEntity>()
        assertFalse(HaCondition.StateIs(player, listOf("playing")).holds(nothing))
        assertFalse(HaCondition.StateIsNot(player, listOf("playing")).holds(nothing))
        assertFalse(HaCondition.Above(player, 1.0).holds(nothing))
        assertFalse(HaCondition.Below(player, 1.0).holds(nothing))
    }

    @Test fun aboveAndBelowAreStrictAndReadTheStateAsANumber() {
        val sensor = id("sensor.t")
        assertTrue(HaCondition.Above(sensor, 20.0).holds(world(sensor.value to "20.5")))
        assertFalse(HaCondition.Above(sensor, 20.0).holds(world(sensor.value to "20")))
        assertTrue(HaCondition.Below(sensor, 20.0).holds(world(sensor.value to "19.9")))
        assertFalse(HaCondition.Below(sensor, 20.0).holds(world(sensor.value to "20")))
    }

    @Test fun aStateThatIsNotANumberFailsAboveAndBelow() {
        val sensor = id("sensor.t")
        assertFalse(HaCondition.Above(sensor, 0.0).holds(world(sensor.value to "unavailable")))
        assertFalse(HaCondition.Below(sensor, 100.0).holds(world(sensor.value to "unknown")))
    }

    @Test fun allNeedsEveryPart() {
        val sensor = id("sensor.t")
        val between = HaCondition.All(listOf(HaCondition.Above(sensor, 10.0), HaCondition.Below(sensor, 20.0)))
        assertTrue(between.holds(world(sensor.value to "15")))
        assertFalse(between.holds(world(sensor.value to "25")))
        assertFalse(between.holds(world(sensor.value to "5")))
    }

    @Test fun anEmptyAllHolds() {
        assertTrue(HaCondition.All(emptyList()).holds(emptyMap()))
    }

    @Test fun allOfIsTheSimplestEquivalent() {
        val a = HaCondition.StateIs(player, listOf("playing"))
        val b = HaCondition.Above(id("sensor.t"), 1.0)
        assertEquals(HaCondition.Always, allOf(emptyList()))
        assertEquals(a, allOf(listOf(a)))
        assertEquals(a, allOf(listOf(HaCondition.Always, a)))
        assertEquals(HaCondition.All(listOf(a, b)), allOf(listOf(a, b)))
        assertEquals(HaCondition.All(listOf(a, b)), allOf(listOf(HaCondition.All(listOf(a)), b)))
    }

    // ---- what the view mode draws ----

    private val whenPlaying = HaCondition.StateIs(player, listOf("playing"))
    private val always = HaTile.Toggle(TileId("t0"), "", "", id("light.a"))
    private val shown = HaTile.Toggle(TileId("t1"), "", "", id("light.b"), whenPlaying)
    private val hidden = HaTile.Toggle(TileId("t2"), "", "", id("light.c"), HaCondition.StateIs(player, listOf("paused")))
    private val dashboard = HaDashboard(
        listOf(
            HaSection(SectionId("s0"), "Mixed", listOf(always, shown, hidden)),
            HaSection(SectionId("s1"), "Only hidden", listOf(hidden.withCondition(HaCondition.StateIs(player, listOf("buffering"))))),
            HaSection(SectionId("s2"), "Empty from the start", emptyList()),
        ),
    )

    @Test fun aTileWithNoConditionIsAlwaysDrawn() {
        assertTrue(always.isVisibleWith(emptyMap()))
    }

    @Test fun viewModeKeepsTheTilesWhoseConditionHoldsInOrder() {
        val sections = dashboard.visibleSections(world(player.value to "playing"))
        assertEquals(listOf("t0", "t1"), sections.first().tiles.map { it.id.value })
    }

    @Test fun aSectionWhoseTilesAreAllHiddenIsNotDrawnButAnEmptyOneStays() {
        val sections = dashboard.visibleSections(world(player.value to "playing"))
        assertEquals(listOf("Mixed", "Empty from the start"), sections.map { it.title })
    }

    @Test fun whenTheConditionChangesTheTileComesAndGoes() {
        assertEquals(listOf("t0", "t2"), dashboard.visibleSections(world(player.value to "paused")).first().tiles.map { it.id.value })
        assertEquals(listOf("Mixed", "Only hidden", "Empty from the start"), dashboard.visibleSections(world(player.value to "buffering")).map { it.title })
    }

    @Test fun withNoEntitiesOnlyUnconditionalTilesAreDrawn() {
        assertEquals(listOf("t0"), dashboard.visibleSections(emptyMap()).single { it.title == "Mixed" }.tiles.map { it.id.value })
    }
}
