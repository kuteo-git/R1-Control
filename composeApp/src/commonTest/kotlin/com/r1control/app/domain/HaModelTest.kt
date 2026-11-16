package com.r1control.app.domain

import com.r1control.app.domain.model.ha.HaAddress
import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaDomain
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaInput
import com.r1control.app.domain.model.ha.sortedForList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class HaModelTest {
    @Test fun theDomainIsReadFromTheIdPrefix() {
        val expected = mapOf(
            "light.kitchen" to HaDomain.Light,
            "switch.fan" to HaDomain.Switch,
            "script.good_night" to HaDomain.Script,
            "fan.bedroom" to HaDomain.Fan,
            "sensor.hall_temperature" to HaDomain.Sensor,
            "binary_sensor.front_door" to HaDomain.BinarySensor,
            "media_player.living_room" to HaDomain.MediaPlayer,
            "cover.garage" to HaDomain.Cover,
            "climate.hall" to HaDomain.Climate,
            "automation.sunrise" to HaDomain.Other,
            "lights.kitchen" to HaDomain.Other,
            "light" to HaDomain.Light,
        )
        expected.forEach { (id, domain) -> assertEquals(domain, HaDomain.of(HaEntityId(id)), id) }
    }

    @Test fun aSensorIsNotABinarySensor() {
        assertEquals(HaDomain.BinarySensor, HaDomain.of(HaEntityId("binary_sensor.x")))
        assertEquals("binary_sensor", HaEntityId("binary_sensor.x").domainName)
        assertEquals("x", HaEntityId("binary_sensor.x").objectName)
    }

    @Test fun theNameFallsBackToTheIdWhenThereIsNoFriendlyName() {
        val named = HaEntity(HaEntityId("light.a"), "on", mapOf("friendly_name" to HaAttribute.Text("Kitchen")))
        val bare = HaEntity(HaEntityId("light.b"), "on", emptyMap())
        val blank = HaEntity(HaEntityId("light.c"), "on", mapOf("friendly_name" to HaAttribute.Text(" ")))
        assertEquals("Kitchen", named.friendlyName)
        assertEquals("light.b", bare.friendlyName)
        assertEquals("light.c", blank.friendlyName)
    }

    @Test fun theListIsSortedByDomainThenNameIgnoringCase() {
        fun e(id: String, name: String) = HaEntity(HaEntityId(id), "on", mapOf("friendly_name" to HaAttribute.Text(name)))
        val sorted = listOf(e("switch.a", "Zed"), e("light.b", "beta"), e("light.a", "Alpha"), e("light.c", "Beta")).sortedForList()
        assertEquals(listOf("light.a", "light.b", "light.c", "switch.a"), sorted.map { it.id.value })
    }

    @Test fun addressTakesHomeAssistantsPortWhenNoneIsTyped() {
        val parsed = assertIs<HaInput.Valid>(HaAddress.parse("homeassistant.local", "tok"))
        assertEquals(HaAddress("homeassistant.local", 8123, "tok"), parsed.address)
        assertEquals("homeassistant.local:8123", parsed.address.hostAndPort)
    }

    @Test fun addressKeepsATypedPortAndAcceptsTheShapesTheR1FieldDoes() {
        assertEquals(
            HaAddress("192.168.1.10", 9000, "tok"),
            assertIs<HaInput.Valid>(HaAddress.parse("http://192.168.1.10:9000/", " tok\n")).address,
        )
    }

    @Test fun badInputNamesItsReason() {
        fun reason(address: String, token: String) = assertIs<HaInput.Invalid>(HaAddress.parse(address, token)).reason
        assertEquals(HaInput.Reason.EmptyAddress, reason("", "tok"))
        assertEquals(HaInput.Reason.BadHost, reason("ha box", "tok"))
        assertEquals(HaInput.Reason.BadHost, reason("https://ha.local", "tok"))
        assertEquals(HaInput.Reason.BadPort, reason("ha.local:99999", "tok"))
        assertEquals(HaInput.Reason.EmptyToken, reason("ha.local", "  "))
        assertEquals(HaInput.Reason.BadToken, reason("ha.local", "two words"))
    }

    @Test fun theTokenNeverReachesAMessage() {
        val address = HaAddress("ha.local", 8123, "SECRET-TOKEN")
        assertFalse("SECRET" in address.toString())
        assertFalse("SECRET" in "$address")
    }
}
