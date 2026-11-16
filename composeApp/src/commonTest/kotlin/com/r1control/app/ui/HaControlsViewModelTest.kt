package com.r1control.app.ui

import com.r1control.app.data.remote.importDashboard
import com.r1control.app.domain.bedroomFan
import com.r1control.app.domain.model.ha.HaAddress
import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaTile
import com.r1control.app.domain.model.ha.SavedHaAddress
import com.r1control.app.domain.model.ha.TileId
import com.r1control.app.domain.model.ha.tapAction
import com.r1control.app.domain.model.ha.HaTap
import com.r1control.app.domain.momToiletLight
import com.r1control.app.domain.repository.HaException
import com.r1control.app.domain.usecase.Foreground
import com.r1control.app.testing.FakeHa
import com.r1control.app.testing.MemoryHaLayout
import com.r1control.app.testing.MemoryHaSettings
import com.r1control.app.ui.ha.HaViewModel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HaControlsViewModelTest {
    private val ha = FakeHa()
    private val light = momToiletLight()
    private val fan = bedroomFan()

    private fun TestScope.model(): HaViewModel {
        ha.map.value = listOf(light, fan).associateBy { it.id }
        val vm = HaViewModel(
            MemoryHaSettings(SavedHaAddress.Set(HaAddress("ha.local", 8123, "tok"))), ha, Foreground(), backgroundScope,
            MemoryHaLayout(), ::importDashboard,
        )
        runCurrent()
        return vm
    }

    private fun on(id: HaEntityId): Map<String, HaAttribute> = mapOf("entity_id" to HaAttribute.Text(id.value))

    @Test fun openingControlsNamesTheTargetAndClosingClearsIt() = runTest {
        val vm = model()
        assertTrue(vm.controlTarget.value.isNone)
        vm.openControls(light.id)
        assertEquals(light.id, vm.controlTarget.value)
        vm.closeControls()
        assertTrue(vm.controlTarget.value.isNone)
    }

    @Test fun aTargetThatLeavesTheEntitiesClosesTheControls() = runTest {
        val vm = model()
        vm.openControls(fan.id)
        runCurrent()
        assertEquals(fan.id, vm.controlTarget.value)
        ha.map.value = listOf(light).associateBy { it.id }
        runCurrent()
        assertTrue(vm.controlTarget.value.isNone)
    }

    @Test fun aTargetStaysWhileItsStateChanges() = runTest {
        val vm = model()
        vm.openControls(light.id)
        ha.map.value = mapOf(light.id to momToiletLight(128.0, "on"), fan.id to fan)
        runCurrent()
        assertEquals(light.id, vm.controlTarget.value)
    }

    @Test fun aControllableToggleOpensControlsAndAPlainOneCallsTheService() = runTest {
        val vm = model()
        val dimmable = HaTile.Toggle(TileId("t0"), "", "", light.id)
        val plain = HaEntity(HaEntityId("switch.hall"), "off", emptyMap())
        val plainTile = HaTile.Toggle(TileId("t1"), "", "", plain.id)
        assertEquals(HaTap.OpenControls, tapAction(light, dimmable))
        assertEquals(HaTap.RunTile, tapAction(plain, plainTile))
        vm.onTile(plainTile)
        runCurrent()
        assertEquals(listOf(Triple("switch", "toggle", on(plain.id))), ha.calls)
    }

    @Test fun eachSetterCallsItsService() = runTest {
        val vm = model()
        vm.togglePower(light.id)
        vm.setBrightness(light.id, 50)
        vm.setFanSpeed(fan.id, 70)
        vm.setOscillate(fan.id, false)
        vm.setPreset(fan.id, "Natural Wind")
        runCurrent()
        assertEquals(
            listOf(
                Triple("light", "toggle", on(light.id)),
                Triple("light", "turn_on", on(light.id) + ("brightness_pct" to HaAttribute.Number(50.0))),
                Triple("fan", "set_percentage", on(fan.id) + ("percentage" to HaAttribute.Number(70.0))),
                Triple("fan", "oscillate", on(fan.id) + ("oscillating" to HaAttribute.Flag(false))),
                Triple("fan", "set_preset_mode", on(fan.id) + ("preset_mode" to HaAttribute.Text("Natural Wind"))),
            ),
            ha.calls,
        )
    }

    @Test fun aFailedCallIsDroppedAndTheNextOneStillGoesOut() = runTest {
        val vm = model()
        ha.callFailure = HaException.TimedOut()
        vm.setBrightness(light.id, 30)
        runCurrent()
        ha.callFailure = null
        vm.setBrightness(light.id, 40)
        runCurrent()
        assertEquals(2, ha.calls.size)
    }
}
