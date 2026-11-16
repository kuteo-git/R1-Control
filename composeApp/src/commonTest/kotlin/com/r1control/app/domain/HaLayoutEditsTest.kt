package com.r1control.app.domain

import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaDashboard
import com.r1control.app.domain.model.ha.HaEditRow
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaSection
import com.r1control.app.domain.model.ha.HaServiceCall
import com.r1control.app.domain.model.ha.HaTile
import com.r1control.app.domain.model.ha.SectionId
import com.r1control.app.domain.model.ha.TileId
import com.r1control.app.domain.model.ha.addSection
import com.r1control.app.domain.model.ha.addTile
import com.r1control.app.domain.model.ha.defaultTile
import com.r1control.app.domain.model.ha.deleteSection
import com.r1control.app.domain.model.ha.deleteTile
import com.r1control.app.domain.model.ha.editRows
import com.r1control.app.domain.model.ha.isUnavailableWith
import com.r1control.app.domain.model.ha.moveRow
import com.r1control.app.domain.model.ha.moveSection
import com.r1control.app.domain.model.ha.moveTile
import com.r1control.app.domain.model.ha.nextSectionId
import com.r1control.app.domain.model.ha.nextTileId
import com.r1control.app.domain.model.ha.pickableEntities
import com.r1control.app.domain.model.ha.renameSection
import com.r1control.app.domain.model.ha.renameTile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

private fun tile(n: Int, entity: String = "light.l$n") = HaTile.Toggle(TileId("t$n"), "", "", HaEntityId(entity))

private fun section(n: Int, vararg tiles: Int) = HaSection(SectionId("s$n"), "S$n", tiles.map { tile(it) })

/** s0: t0 t1 t2 | s1: t3 | s2: (empty) */
private val LAYOUT = HaDashboard(listOf(section(0, 0, 1, 2), section(1, 3), section(2)))

private fun HaDashboard.shape(): List<Pair<String, List<String>>> = sections.map { s -> s.id.value to s.tiles.map { it.id.value } }

class HaLayoutEditsTest {
    @Test fun aSectionIsAddedLastAndTwiceIsOnce() {
        val added = LAYOUT.addSection(SectionId("s9"), "New")
        assertEquals(listOf("s0", "s1", "s2", "s9"), added.sections.map { it.id.value })
        assertEquals(emptyList(), added.sections.last().tiles)
        assertEquals(added, added.addSection(SectionId("s9"), "Again"))
    }

    @Test fun aSectionIsRenamedAndAnUnknownOneIsNotTouched() {
        assertEquals("Hall", LAYOUT.renameSection(SectionId("s1"), "Hall").sections[1].title)
        assertSame(LAYOUT, LAYOUT.renameSection(SectionId("nope"), "Hall"))
    }

    @Test fun deletingASectionTakesItsTilesAndAMissingOneIsANoOp() {
        assertEquals(listOf("s1" to listOf("t3"), "s2" to emptyList()), LAYOUT.deleteSection(SectionId("s0")).shape())
        assertSame(LAYOUT, LAYOUT.deleteSection(SectionId("nope")))
    }

    @Test fun aSectionMovesToTheIndexItEndsAt() {
        assertEquals(listOf("s1", "s2", "s0"), LAYOUT.moveSection(0, 2).sections.map { it.id.value })
        assertEquals(listOf("s2", "s0", "s1"), LAYOUT.moveSection(2, 0).sections.map { it.id.value })
    }

    @Test fun moveSectionClampsTheTargetAndIgnoresASourceOutOfRange() {
        assertEquals(listOf("s1", "s2", "s0"), LAYOUT.moveSection(0, 99).sections.map { it.id.value })
        assertEquals(listOf("s2", "s0", "s1"), LAYOUT.moveSection(2, -5).sections.map { it.id.value })
        assertSame(LAYOUT, LAYOUT.moveSection(7, 0))
        assertSame(LAYOUT, LAYOUT.moveSection(-1, 0))
        assertSame(HaDashboard.Empty, HaDashboard.Empty.moveSection(0, 1))
    }

    @Test fun aTileIsAddedLastToItsSection() {
        val added = LAYOUT.addTile(SectionId("s2"), tile(9))
        assertEquals(listOf("t9"), added.sections[2].tiles.map { it.id.value })
        assertEquals(listOf("t0", "t1", "t2", "t9"), LAYOUT.addTile(SectionId("s0"), tile(9)).sections[0].tiles.map { it.id.value })
    }

    @Test fun aTileForAnUnknownSectionOrWithATakenIdIsIgnored() {
        assertSame(LAYOUT, LAYOUT.addTile(SectionId("nope"), tile(9)))
        assertSame(LAYOUT, LAYOUT.addTile(SectionId("s2"), tile(1)))
    }

    @Test fun aTileIsRenamedWhateverItsKindAndAMissingOneIsANoOp() {
        val kinds = HaDashboard(
            listOf(
                HaSection(
                    SectionId("s0"), "",
                    listOf(
                        HaTile.Toggle(TileId("a"), "", "", HaEntityId("light.a")),
                        HaTile.Action(TileId("b"), "", "", HaEntityId("script.b"), HaServiceCall("script", "b", emptyMap())),
                        HaTile.Readout(TileId("c"), "", "", HaEntityId("sensor.c")),
                        HaTile.Cover(TileId("d"), "", "", HaEntityId("cover.d")),
                        HaTile.Media(TileId("e"), "", "", HaEntityId("media_player.e")),
                    ),
                ),
            ),
        )
        val renamed = listOf("a", "b", "c", "d", "e").fold(kinds) { d, id -> d.renameTile(TileId(id), "X$id") }
        assertEquals(listOf("Xa", "Xb", "Xc", "Xd", "Xe"), renamed.sections.single().tiles.map { it.name })
        assertSame(LAYOUT, LAYOUT.renameTile(TileId("nope"), "x"))
    }

    @Test fun aTileIsDeletedAndAMissingOneIsANoOp() {
        assertEquals(listOf("t0", "t2"), LAYOUT.deleteTile(TileId("t1")).sections[0].tiles.map { it.id.value })
        assertSame(LAYOUT, LAYOUT.deleteTile(TileId("nope")))
    }

    @Test fun aTileMovesWithinItsSectionToTheIndexItEndsAt() {
        assertEquals(listOf("t1", "t2", "t0"), LAYOUT.moveTile(TileId("t0"), SectionId("s0"), 2).sections[0].tiles.map { it.id.value })
        assertEquals(listOf("t2", "t0", "t1"), LAYOUT.moveTile(TileId("t2"), SectionId("s0"), 0).sections[0].tiles.map { it.id.value })
    }

    @Test fun aTileMovesAcrossSectionsIntoTheMiddleTheStartAndAnEmptySection() {
        assertEquals(
            listOf("s0" to listOf("t0", "t2"), "s1" to listOf("t3", "t1"), "s2" to emptyList()),
            LAYOUT.moveTile(TileId("t1"), SectionId("s1"), 1).shape(),
        )
        assertEquals(listOf("t1", "t3"), LAYOUT.moveTile(TileId("t1"), SectionId("s1"), 0).sections[1].tiles.map { it.id.value })
        assertEquals(listOf("t3"), LAYOUT.moveTile(TileId("t3"), SectionId("s2"), 0).sections[2].tiles.map { it.id.value })
        assertEquals(emptyList(), LAYOUT.moveTile(TileId("t3"), SectionId("s2"), 0).sections[1].tiles)
    }

    @Test fun moveTileClampsTheIndexIntoTheTargetSection() {
        assertEquals(listOf("t3", "t0"), LAYOUT.moveTile(TileId("t0"), SectionId("s1"), 99).sections[1].tiles.map { it.id.value })
        assertEquals(listOf("t0", "t3"), LAYOUT.moveTile(TileId("t0"), SectionId("s1"), -4).sections[1].tiles.map { it.id.value })
    }

    @Test fun moveTileForAnUnknownTileOrSectionChangesNothing() {
        assertSame(LAYOUT, LAYOUT.moveTile(TileId("nope"), SectionId("s1"), 0))
        assertSame(LAYOUT, LAYOUT.moveTile(TileId("t0"), SectionId("nope"), 0))
    }

    @Test fun everyEditKeepsEveryTileExactlyOnce() {
        val moved = LAYOUT.moveTile(TileId("t0"), SectionId("s2"), 0).moveTile(TileId("t3"), SectionId("s0"), 1).moveSection(0, 2)
        assertEquals(listOf("t0", "t1", "t2", "t3"), moved.sections.flatMap { s -> s.tiles.map { it.id.value } }.sorted())
    }
}

class HaLayoutIdsTest {
    @Test fun aNewIdIsOnePastTheHighestInUse() {
        assertEquals(SectionId("s3"), LAYOUT.nextSectionId())
        assertEquals(TileId("t4"), LAYOUT.nextTileId())
    }

    @Test fun anEmptyLayoutStartsAtZero() {
        assertEquals(SectionId("s0"), HaDashboard.Empty.nextSectionId())
        assertEquals(TileId("t0"), HaDashboard.Empty.nextTileId())
    }

    @Test fun anIdOfAnotherShapeTakesNoPartInTheCount() {
        val odd = HaDashboard(listOf(HaSection(SectionId("hall"), "", listOf(tile(7).copy(id = TileId("lamp")), tile(2)))))
        assertEquals(SectionId("s0"), odd.nextSectionId())
        assertEquals(TileId("t3"), odd.nextTileId())
    }

    @Test fun aNewIdNeverCollidesNoMatterHowManyAreMade() {
        var layout = HaDashboard.Empty
        repeat(5) { layout = layout.addSection(layout.nextSectionId(), "x") }
        repeat(5) { layout = layout.addTile(layout.sections[it].id, tile(0).copy(id = layout.nextTileId())) }
        assertEquals(5, layout.sections.map { it.id }.distinct().size)
        assertEquals(5, layout.sections.flatMap { it.tiles }.map { it.id }.distinct().size)
    }
}

class HaDefaultTileTest {
    private fun kind(entity: String) = defaultTile(TileId("t0"), HaEntityId(entity))

    @Test fun eachDomainBecomesItsKind() {
        listOf("light.a", "switch.a", "fan.a", "input_boolean.a").forEach { assertIs<HaTile.Toggle>(kind(it), it) }
        assertIs<HaTile.Cover>(kind("cover.a"))
        assertIs<HaTile.Media>(kind("media_player.a"))
        listOf("sensor.a", "select.a", "climate.a", "binary_sensor.a").forEach { assertIs<HaTile.Readout>(kind(it), it) }
    }

    @Test fun scriptsButtonsAndScenesCarryTheirOwnService() {
        assertEquals(HaServiceCall("script", "cool", emptyMap()), assertIs<HaTile.Action>(kind("script.cool")).call)
        assertEquals(HaServiceCall.on(HaEntityId("button.b"), "button", "press"), assertIs<HaTile.Action>(kind("button.b")).call)
        assertEquals(HaServiceCall.on(HaEntityId("scene.s"), "scene", "turn_on"), assertIs<HaTile.Action>(kind("scene.s")).call)
    }
}

private fun live(id: String, name: String? = null, state: String = "on") =
    HaEntity(HaEntityId(id), state, if (name == null) emptyMap() else mapOf(HaEntity.FRIENDLY_NAME to HaAttribute.Text(name)))

class HaPickableEntitiesTest {
    private val entities = listOf(live("light.l0", "Kitchen"), live("light.l9", "Hall lamp"), live("sensor.temp", "Hall temperature"))

    @Test fun whatIsAlreadyOnTheDashboardIsNotOffered() {
        assertEquals(listOf("light.l9", "sensor.temp"), pickableEntities(entities, LAYOUT, "").map { it.id.value })
    }

    @Test fun theQueryMatchesNameOrIdIgnoringCaseAndEdges() {
        assertEquals(listOf("light.l9", "sensor.temp"), pickableEntities(entities, LAYOUT, "  HALL ").map { it.id.value })
        assertEquals(listOf("sensor.temp"), pickableEntities(entities, LAYOUT, "sensor.").map { it.id.value })
        assertEquals(emptyList(), pickableEntities(entities, LAYOUT, "garage"))
    }
}

class HaAvailabilityTest {
    @Test fun aTileIsUnavailableWhenItsEntityIsMissingOrHomeAssistantSaysSo() {
        val t = tile(0)
        assertTrue(t.isUnavailableWith(null))
        assertTrue(t.isUnavailableWith(live("light.l0", state = "unavailable")))
        assertFalse(t.isUnavailableWith(live("light.l0", state = "on")))
    }

    @Test fun anActionNamingNoEntityIsNeverUnavailable() {
        val chip = HaTile.Action(TileId("t0"), "Off", "", HaEntityId.None, HaServiceCall("script", "off", emptyMap()))
        assertFalse(chip.isUnavailableWith(null))
    }
}

class HaEditRowsTest {
    private fun HaDashboard.rows() = editRows().map { (it as? HaEditRow.Header)?.id?.value ?: (it as HaEditRow.TileRow).tile.id.value }

    @Test fun theColumnIsEachHeaderThenItsTiles() {
        assertEquals(listOf("s0", "t0", "t1", "t2", "s1", "t3", "s2"), LAYOUT.rows())
    }

    @Test fun aTileDroppedBelowAnotherSectionsHeaderLandsFirstInIt() {
        // t2 (index 3) dropped onto the header s1 (index 4).
        assertEquals(listOf("s0" to listOf("t0", "t1"), "s1" to listOf("t2", "t3"), "s2" to emptyList()), LAYOUT.moveRow(3, 4).shape())
    }

    @Test fun aTileDroppedAmongAnotherSectionsTilesLandsWhereItWasDropped() {
        // t0 (1) dropped after t3 (5).
        assertEquals(listOf("s0" to listOf("t1", "t2"), "s1" to listOf("t3", "t0"), "s2" to emptyList()), LAYOUT.moveRow(1, 5).shape())
    }

    @Test fun aTileDroppedAtTheEndGoesToTheLastSectionEvenWhenItIsEmpty() {
        assertEquals(listOf("t3"), LAYOUT.moveRow(5, 6).sections[2].tiles.map { it.id.value })
    }

    @Test fun aTileDroppedWithinItsSectionIsAReorder() {
        assertEquals(listOf("t1", "t2", "t0"), LAYOUT.moveRow(1, 3).sections[0].tiles.map { it.id.value })
    }

    @Test fun aTileDroppedAboveTheFirstHeaderIsTheFirstTileOfTheFirstSection() {
        assertEquals(listOf("t3", "t0", "t1", "t2"), HaDashboard(listOf(section(0, 0, 1, 2), section(1, 3))).moveRow(5, 0).sections[0].tiles.map { it.id.value })
    }

    @Test fun aHeaderCarriesItsWholeSection() {
        // s0 (0) dropped after s1 (4): the sections swap and every tile stays where its section is.
        assertEquals(listOf("s1" to listOf("t3"), "s0" to listOf("t0", "t1", "t2"), "s2" to emptyList()), LAYOUT.moveRow(0, 4).shape())
        assertEquals(listOf("s2" to emptyList(), "s0" to listOf("t0", "t1", "t2"), "s1" to listOf("t3")), LAYOUT.moveRow(6, 0).shape())
    }

    @Test fun aMoveOutOfRangeOrToItsOwnPlaceChangesNothing() {
        assertSame(LAYOUT, LAYOUT.moveRow(2, 2))
        assertSame(LAYOUT, LAYOUT.moveRow(-1, 2))
        assertSame(LAYOUT, LAYOUT.moveRow(2, 99))
        assertSame(HaDashboard.Empty, HaDashboard.Empty.moveRow(0, 1))
    }

    @Test fun afterAnyDragEveryTileIsStillThereOnce() {
        val rows = LAYOUT.editRows().size
        for (from in 0 until rows) for (to in 0 until rows) {
            val moved = LAYOUT.moveRow(from, to)
            assertEquals(listOf("t0", "t1", "t2", "t3"), moved.sections.flatMap { s -> s.tiles.map { it.id.value } }.sorted(), "$from -> $to")
            assertEquals(listOf("s0", "s1", "s2"), moved.sections.map { it.id.value }.sorted(), "$from -> $to")
        }
    }
}
