package com.r1control.app.data

import com.r1control.app.data.remote.EntityDelta
import com.r1control.app.data.remote.HaMessage
import com.r1control.app.data.remote.applying
import com.r1control.app.data.remote.authFrame
import com.r1control.app.data.remote.callServiceFrame
import com.r1control.app.data.remote.lovelaceConfigFrame
import com.r1control.app.data.remote.parseHaMessage
import com.r1control.app.data.remote.renderTemplateFrame
import com.r1control.app.data.remote.unsubscribeEventsFrame
import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaDomain
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * Home Assistant's WebSocket frames. `ha_auth_required.json` is a frame read from the
 * household's Home Assistant (2026.10.4); the others are its documented shapes
 * (compressed `subscribe_entities` state, `result`), written out in full.
 */
class HaWireTest {
    private fun initial(): Map<HaEntityId, HaEntity> {
        val message = assertIs<HaMessage.Entities>(parseHaMessage(fixture("ha_subscribe_entities_initial.json")))
        assertEquals(1, message.id)
        return emptyMap<HaEntityId, HaEntity>().applying(message.delta)
    }

    private fun entity(map: Map<HaEntityId, HaEntity>, id: String) = checkNotNull(map[HaEntityId(id)]) { "no $id" }

    @Test fun theAuthHandshakeFramesAreRead() {
        assertEquals(HaMessage.AuthRequired, parseHaMessage(fixture("ha_auth_required.json")))
        assertEquals(HaMessage.AuthOk, parseHaMessage("""{"type":"auth_ok","ha_version":"2026.10.4"}"""))
        assertEquals(HaMessage.AuthInvalid, parseHaMessage("""{"type":"auth_invalid","message":"Invalid access token or password"}"""))
    }

    @Test fun theFirstEventIsEveryEntityWithItsStateAndAttributes() {
        val entities = initial()
        assertEquals(5, entities.size)
        val light = entity(entities, "light.kitchen")
        assertEquals("on", light.state)
        assertEquals(HaDomain.Light, light.domain)
        assertEquals("Kitchen", light.friendlyName)
        assertEquals(HaAttribute.Number(180.0), light.attributes["brightness"])
        assertEquals(HaAttribute.Structured("""["brightness"]"""), light.attributes["supported_color_modes"])
        assertFalse("rgb_color" in light.attributes)

        val sensor = entity(entities, "sensor.hall_temperature")
        assertEquals("21.5", sensor.state)
        assertEquals("°C", sensor.unit)
        assertEquals(HaAttribute.Flag(false), entity(entities, "media_player.living_room").attributes["is_volume_muted"])
    }

    @Test fun anEntityWithNoFriendlyNameIsNamedByItsId() {
        assertEquals("binary_sensor.front_door", entity(initial(), "binary_sensor.front_door").friendlyName)
        assertEquals("unavailable", entity(initial(), "binary_sensor.front_door").state)
    }

    @Test fun aLaterEventAddsRemovesAndChanges() {
        val change = assertIs<HaMessage.Entities>(parseHaMessage(fixture("ha_subscribe_entities_change.json")))
        val after = initial().applying(change.delta)

        assertEquals("off", entity(after, "script.good_night").state)
        assertFalse(HaEntityId("switch.fan") in after)

        val light = entity(after, "light.kitchen")
        assertEquals("off", light.state)
        assertFalse("brightness" in light.attributes, "a null attribute is gone")
        assertFalse("color_mode" in light.attributes)
        assertEquals("Kitchen", light.friendlyName, "attributes not mentioned stay")

        val sensor = entity(after, "sensor.hall_temperature")
        assertEquals("21.7", sensor.state)
        assertEquals("°C", sensor.unit)
        assertFalse(HaEntityId("unknown.entity") in after, "a change to an unknown entity is dropped")
        assertEquals(5, after.size)
    }

    @Test fun anAttributeNamedInTheRemovedListIsRemoved() {
        val before = mapOf(HaEntityId("light.a") to HaEntity(HaEntityId("light.a"), "on", mapOf("x" to HaAttribute.Text("1"), "y" to HaAttribute.Text("2"))))
        val message = assertIs<HaMessage.Entities>(
            parseHaMessage("""{"id":1,"type":"event","event":{"c":{"light.a":{"-":{"a":["x"]}}}}}"""),
        )
        assertEquals(mapOf("y" to HaAttribute.Text("2")), checkNotNull(before.applying(message.delta)[HaEntityId("light.a")]).attributes)
    }

    @Test fun aResultKeepsItsResultObjectAsCompactText() {
        val message = assertIs<HaMessage.Result>(parseHaMessage("""{"id":3,"type":"result","success":true,"result":{"views": [ {"title":"Home"} ]}}"""))
        assertEquals("""{"views":[{"title":"Home"}]}""", message.payload)
        assertEquals("", assertIs<HaMessage.Result>(parseHaMessage("""{"id":3,"type":"result","success":true,"result":null}""")).payload)
    }

    @Test fun theLovelaceConfigFrameAsksForTheDefaultDashboard() {
        assertEquals("""{"id":4,"type":"lovelace/config"}""", lovelaceConfigFrame(4))
    }

    @Test fun aResultCarriesItsErrorCodeAndNeverItsMessage() {
        assertEquals(HaMessage.Result(2, false, "not_found"), parseHaMessage(fixture("ha_result_error.json")))
        val ok = assertIs<HaMessage.Result>(parseHaMessage(fixture("ha_result_ok.json")))
        assertEquals(true, ok.success)
        assertEquals("", ok.errorCode)
        assertEquals("", assertIs<HaMessage.Result>(parseHaMessage(fixture("ha_result_error.json"))).payload, "a refusal has no payload")
    }

    @Test fun framesThatAreNotReadableAreIgnoredNotThrown() {
        assertEquals(HaMessage.Ignored, parseHaMessage("not json"))
        assertEquals(HaMessage.Ignored, parseHaMessage("[1,2]"))
        assertEquals(HaMessage.Ignored, parseHaMessage("""{"id":3,"type":"pong"}"""))
        assertEquals(HaMessage.Ignored, parseHaMessage("""{"type":"event"}"""))
    }

    @Test fun aSubscriptionEventForAnEmptyHouseIsEmpty() {
        val message = assertIs<HaMessage.Entities>(parseHaMessage("""{"id":1,"type":"event","event":{"a":{}}}"""))
        assertEquals(EntityDelta(emptyMap(), emptyMap(), emptySet()), message.delta)
    }

    @Test fun theAuthFrameCarriesTheToken() {
        assertEquals("""{"type":"auth","access_token":"abc"}""", authFrame("abc"))
    }

    @Test fun aServiceCallFrameNamesTheServiceAndItsData() {
        val frame = callServiceFrame(
            7, "light", "turn_on",
            mapOf("entity_id" to HaAttribute.Text("light.kitchen"), "brightness" to HaAttribute.Number(128.0), "transition" to HaAttribute.Number(1.5), "flash" to HaAttribute.Flag(true)),
        )
        assertEquals(
            """{"id":7,"type":"call_service","domain":"light","service":"turn_on","service_data":{"entity_id":"light.kitchen","brightness":128,"transition":1.5,"flash":true}}""",
            frame,
        )
    }

    @Test fun anEventWithAResultIsATemplateRenderingAndOneWithEntitiesIsNot() {
        val template = parseHaMessage("""{"id":5,"type":"event","event":{"result":"Ngày 16","listeners":{"all":false}}}""")
        assertEquals(HaMessage.TemplateEvent(5, "Ngày 16"), template)
        assertIs<HaMessage.Entities>(parseHaMessage("""{"id":1,"type":"event","event":{"c":{"light.a":{"+":{"s":"off"}}}}}"""))
        assertIs<HaMessage.Entities>(parseHaMessage(fixture("ha_subscribe_entities_initial.json")))
    }

    @Test fun aRenderedNumberOrListIsTheTextOfIt() {
        assertEquals(HaMessage.TemplateEvent(5, "21.5"), parseHaMessage("""{"id":5,"type":"event","event":{"result":21.5}}"""))
        assertEquals(HaMessage.TemplateEvent(5, "true"), parseHaMessage("""{"id":5,"type":"event","event":{"result":true}}"""))
        assertEquals(HaMessage.TemplateEvent(5, """["a","b"]"""), parseHaMessage("""{"id":5,"type":"event","event":{"result":["a","b"]}}"""))
        assertEquals(HaMessage.TemplateEvent(5, ""), parseHaMessage("""{"id":5,"type":"event","event":{"result":null}}"""))
    }

    @Test fun theTemplateFramesNameTheTemplateAndTheSubscriptionToStop() {
        assertEquals("""{"id":7,"type":"render_template","template":"{{ now().day }}\nx"}""", renderTemplateFrame(7, "{{ now().day }}\nx"))
        assertEquals("""{"id":8,"type":"unsubscribe_events","subscription":7}""", unsubscribeEventsFrame(8, 7))
    }
}
