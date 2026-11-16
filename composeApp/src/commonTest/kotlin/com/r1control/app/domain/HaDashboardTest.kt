package com.r1control.app.domain

import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaCoverCommand
import com.r1control.app.domain.model.ha.HaDashboard
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaServiceCall
import com.r1control.app.domain.model.ha.HaTile
import com.r1control.app.domain.model.ha.TileId
import com.r1control.app.domain.model.ha.call
import com.r1control.app.domain.model.ha.isLitBy
import com.r1control.app.domain.model.ha.isPressable
import com.r1control.app.domain.model.ha.playPauseCall
import com.r1control.app.domain.model.ha.tapCall
import com.r1control.app.domain.model.ha.titleFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val ID = TileId("t0")

private fun entity(id: String, state: String, name: String? = null) =
    HaEntity(HaEntityId(id), state, if (name == null) emptyMap() else mapOf(HaEntity.FRIENDLY_NAME to HaAttribute.Text(name)))

private fun on(id: String) = mapOf("entity_id" to HaAttribute.Text(id))

class HaDashboardTest {
    private val light = HaTile.Toggle(ID, "Ceiling", "mdi:lightbulb", HaEntityId("light.hall"))
    private val cover = HaTile.Cover(ID, "", "", HaEntityId("cover.blind"))
    private val media = HaTile.Media(ID, "", "", HaEntityId("media_player.kitchen"))
    private val readout = HaTile.Readout(ID, "", "", HaEntityId("sensor.temp"))

    @Test fun aToggleTapTogglesTheEntitysOwnDomain() {
        assertEquals(HaServiceCall("light", "toggle", on("light.hall")), light.tapCall())
        assertEquals(HaServiceCall("switch", "toggle", on("switch.fan")), HaTile.Toggle(ID, "", "", HaEntityId("switch.fan")).tapCall())
    }

    @Test fun anActionTapIsItsCall() {
        val call = HaServiceCall("pyscript", "pytube_next_song", on("media_player.r1"))
        assertEquals(call, HaTile.Action(ID, "", "", HaEntityId.None, call).tapCall())
    }

    @Test fun aReadoutACoverAndAPlayerHaveNoTap() {
        listOf(readout, cover, media).forEach {
            assertNull(it.tapCall())
            assertFalse(it.isPressable)
        }
        assertTrue(light.isPressable)
    }

    @Test fun coverControlsNameTheirServices() {
        assertEquals(HaServiceCall("cover", "open_cover", on("cover.blind")), cover.call(HaCoverCommand.Open))
        assertEquals(HaServiceCall("cover", "close_cover", on("cover.blind")), cover.call(HaCoverCommand.Close))
        assertEquals(HaServiceCall("cover", "stop_cover", on("cover.blind")), cover.call(HaCoverCommand.Stop))
    }

    @Test fun aPlayerPlaysOrPausesWithOneService() {
        assertEquals(HaServiceCall("media_player", "media_play_pause", on("media_player.kitchen")), media.playPauseCall())
    }

    @Test fun aTileIsLitByItsLiveEntity() {
        assertTrue(light.isLitBy(entity("light.hall", "on")))
        assertFalse(light.isLitBy(entity("light.hall", "off")))
        assertFalse(light.isLitBy(null), "no entity yet is not lit")
        assertTrue(cover.isLitBy(entity("cover.blind", "open")))
        assertFalse(cover.isLitBy(entity("cover.blind", "closed")))
        assertTrue(media.isLitBy(entity("media_player.kitchen", "playing")))
        assertFalse(media.isLitBy(entity("media_player.kitchen", "paused")))
        assertFalse(readout.isLitBy(entity("sensor.temp", "on")), "a readout is never lit")
    }

    @Test fun aTilesTitleIsItsSeededNameThenTheLiveNameThenTheId() {
        assertEquals("Ceiling", light.titleFor(entity("light.hall", "on", "Hall light")))
        assertEquals("Hall light", HaTile.Toggle(ID, "", "", HaEntityId("light.hall")).titleFor(entity("light.hall", "on", "Hall light")))
        assertEquals("light.hall", HaTile.Toggle(ID, "", "", HaEntityId("light.hall")).titleFor(null))
    }

    @Test fun anEmptyDashboardHasNoTiles() {
        assertEquals(0, HaDashboard.Empty.tileCount)
    }

    @Test fun noEntityIsNamedAndIsNone() {
        assertTrue(HaEntityId.None.isNone)
        assertFalse(HaEntityId("script.x").isNone)
    }
}
