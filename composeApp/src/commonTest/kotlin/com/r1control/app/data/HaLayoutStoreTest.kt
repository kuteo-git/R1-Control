package com.r1control.app.data

import com.r1control.app.data.local.SettingsDataSource
import com.r1control.app.data.local.SettingsKeys
import com.r1control.app.data.repository.SettingsHaLayoutRepository
import com.r1control.app.data.repository.decodeLayout
import com.r1control.app.data.repository.encodeLayout
import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaCondition
import com.r1control.app.domain.model.ha.HaDashboard
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaSection
import com.r1control.app.domain.model.ha.HaServiceCall
import com.r1control.app.domain.model.ha.HaTile
import com.r1control.app.domain.model.ha.SectionId
import com.r1control.app.domain.model.ha.TileId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class MemorySettings(val map: MutableMap<String, String> = mutableMapOf()) : SettingsDataSource {
    override suspend fun get(key: String) = map[key]
    override suspend fun set(key: String, value: String) {
        map[key] = value
    }
}

private val EVERY_KIND = HaDashboard(
    listOf(
        HaSection(
            SectionId("s0"), "Living “room”",
            listOf(
                HaTile.Toggle(TileId("t0"), "Ceiling", "mdi:lightbulb", HaEntityId("light.kitchen")),
                HaTile.Action(
                    TileId("t1"), "Cool", "", HaEntityId("script.cool"),
                    HaServiceCall(
                        "climate", "set_temperature",
                        mapOf(
                            "text" to HaAttribute.Text("a \"b\""),
                            "number" to HaAttribute.Number(21.5),
                            "whole" to HaAttribute.Number(3.0),
                            "on" to HaAttribute.Flag(true),
                            "off" to HaAttribute.Flag(false),
                            "list" to HaAttribute.Structured("[1,2,{\"a\":null}]"),
                        ),
                    ),
                ),
                HaTile.Action(TileId("t2"), "Off", "", HaEntityId.None, HaServiceCall("script", "off", emptyMap())),
                HaTile.Readout(TileId("t3"), "", "mdi:thermometer", HaEntityId("sensor.hall")),
                HaTile.Cover(TileId("t4"), "Blind", "", HaEntityId("cover.blind")),
                HaTile.Media(TileId("t5"), "", "", HaEntityId("media_player.kitchen")),
                HaTile.Toggle(TileId("t6"), "", "", HaEntityId("light.a"), HaCondition.StateIs(HaEntityId("media_player.kitchen"), listOf("playing", "paused"))),
                HaTile.Readout(TileId("t7"), "", "", HaEntityId("sensor.t"), HaCondition.StateIsNot(HaEntityId("media_player.kitchen"), listOf("off"))),
                HaTile.Cover(
                    TileId("t8"), "", "", HaEntityId("cover.blind"),
                    HaCondition.All(listOf(HaCondition.Above(HaEntityId("sensor.t"), 10.5), HaCondition.Below(HaEntityId("sensor.t"), 20.0))),
                ),
                HaTile.Template(
                    TileId("t10"), "Today", "mdi:calendar", HaEntityId("sensor.lunar"),
                    template = "{% set d = 1 %}\nNgày {{ d }} \"x\"", call = HaServiceCall.None,
                ),
                HaTile.Template(
                    TileId("t11"), "", "", HaEntityId.None, HaCondition.StateIs(HaEntityId("media_player.kitchen"), listOf("playing")),
                    "Tiếp", HaServiceCall("pyscript", "pytube_next_song", mapOf("entity_id" to HaAttribute.Text("media_player.kitchen_audio"))),
                ),
                HaTile.Action(TileId("t9"), "Go", "", HaEntityId.None, HaServiceCall("script", "go", emptyMap()), HaCondition.StateIs(HaEntityId("media_player.kitchen"), listOf("playing"))),
            ),
        ),
        HaSection(SectionId("s1"), "", emptyList()),
    ),
)

class HaLayoutStoreTest {
    @Test fun everyKindOfTileAndAttributeSurvivesTheRoundTrip() {
        assertEquals(EVERY_KIND, decodeLayout(encodeLayout(EVERY_KIND)))
    }

    @Test fun anEmptyLayoutSurvivesToo() {
        assertEquals(HaDashboard.Empty, decodeLayout(encodeLayout(HaDashboard.Empty)))
    }

    @Test fun whatIsNotALayoutIsUnreadable() {
        listOf("", "not json", "[]", "{}", """{"version":1}""", """{"version":99,"sections":[]}""").forEach {
            assertNull(decodeLayout(it), it)
        }
    }

    @Test fun aTileOfAKindThisAppDoesNotKnowMakesTheWholeLayoutUnreadable() {
        val text = encodeLayout(EVERY_KIND).replace("\"cover\"", "\"thermostat\"")
        assertNull(decodeLayout(text))
    }

    @Test fun aLayoutWrittenBeforeConditionsReadsAsAlwaysDrawn() {
        val text = """{"version":1,"sections":[{"id":"s0","title":"","tiles":[{"kind":"toggle","id":"t0","name":"","icon":"","entity":"light.a"}]}]}"""
        assertEquals(HaCondition.Always, decodeLayout(text)!!.sections.single().tiles.single().condition)
    }

    @Test fun aConditionOfATypeThisAppDoesNotKnowMakesTheWholeLayoutUnreadable() {
        val text = encodeLayout(EVERY_KIND).replace("\"state_not\"", "\"or\"")
        assertNull(decodeLayout(text))
    }

    @Test fun aTemplateWithoutItsTextIsUnreadable() {
        val text = """{"version":1,"sections":[{"id":"s0","title":"","tiles":[{"kind":"template","id":"t0","name":"","icon":"","entity":""}]}]}"""
        assertNull(decodeLayout(text))
    }

    @Test fun aTemplateWithNoTapComesBackWithNoTap() {
        val tile = decodeLayout(encodeLayout(EVERY_KIND))!!.sections.first().tiles.filterIsInstance<HaTile.Template>().first { it.id.value == "t10" }
        assertEquals(HaServiceCall.None, tile.call)
    }

    @Test fun anActionWithoutItsCallIsUnreadable() {
        val text = """{"version":1,"sections":[{"id":"s0","title":"","tiles":[{"kind":"action","id":"t0","name":"","icon":"","entity":"script.a"}]}]}"""
        assertNull(decodeLayout(text))
    }

    @Test fun aSavedLayoutIsLoadedBackAndNothingSavedIsNull() = runTest {
        val repo = SettingsHaLayoutRepository(MemorySettings())
        assertNull(repo.load())
        repo.save(EVERY_KIND)
        assertEquals(EVERY_KIND, repo.load())
    }

    @Test fun anUnreadableValueLoadsAsNoneAndIsKeptAsideOnceNeverOverwritten() = runTest {
        val settings = MemorySettings(mutableMapOf(SettingsKeys.HA_LAYOUT to "garbage one"))
        val repo = SettingsHaLayoutRepository(settings)
        assertNull(repo.load())
        assertEquals("garbage one", settings.map[SettingsKeys.HA_LAYOUT_BACKUP])
        settings.map[SettingsKeys.HA_LAYOUT] = "garbage two"
        assertNull(repo.load())
        assertEquals("garbage one", settings.map[SettingsKeys.HA_LAYOUT_BACKUP])
    }

    @Test fun anUnreadableValueIsStillNoneWhenItCannotBeKeptAside() = runTest {
        val failing = object : SettingsDataSource {
            override suspend fun get(key: String) = if (key == SettingsKeys.HA_LAYOUT) "garbage" else null
            override suspend fun set(key: String, value: String) = error("disk full")
        }
        assertNull(SettingsHaLayoutRepository(failing).load())
    }
}
