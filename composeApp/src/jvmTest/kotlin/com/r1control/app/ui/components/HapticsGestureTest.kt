package com.r1control.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.VolumeScale
import com.r1control.app.ui.control.VolumePill
import com.r1control.app.ui.control.VolumeState
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.R1Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the phone plays when a finger works each control, driven by pointer
 * events through a scene with a recording [LocalHaptics]. The platform calls
 * themselves cannot run here; this proves which sensation each touch asks for.
 */
@OptIn(ExperimentalComposeUiApi::class)
class HapticsGestureTest {
    private val density = 3f
    private val widthPx = 390 * density

    private inner class Rig(content: @Composable () -> Unit) {
        val felt = mutableListOf<Haptic>()
        private var time = 0L
        val scene = ImageComposeScene(width = widthPx.toInt(), height = (200 * density).toInt(), density = Density(density)) {
            CompositionLocalProvider(LocalStrings provides EnglishStrings, LocalHaptics provides Haptics { felt += it }) {
                R1Theme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) { content() }
                    }
                }
            }
        }

        init { frames(3) }

        fun frames(n: Int) = repeat(n) { scene.render(time); time += 16_000_000L }
        private fun send(type: PointerEventType, x: Float, y: Float) =
            scene.sendPointerEvent(type, Offset(x, y), timeMillis = time / 1_000_000, type = PointerType.Touch)

        fun tap(x: Float, y: Float) {
            send(PointerEventType.Press, x, y); frames(1)
            send(PointerEventType.Release, x, y); frames(2)
        }

        /** Presses at [fromX], moves a few pixels a frame to [toX], lets go. */
        fun drag(fromX: Float, toX: Float, y: Float, step: Float = 6f) {
            send(PointerEventType.Press, fromX, y); frames(1)
            var at = fromX
            while (at != toX) {
                at = if (toX > at) minOf(toX, at + step) else maxOf(toX, at - step)
                send(PointerEventType.Move, at, y); frames(1)
            }
            send(PointerEventType.Release, at, y); frames(3)
        }

        fun use(block: Rig.() -> Unit) = try { block() } finally { scene.close() }
    }

    @Test
    fun aButtonPressIsATapAndADisabledOneIsSilent() {
        var presses = 0
        Rig { IconTap(R1Icons.Next, "Next", { presses++ }) }.use {
            tap(24 * density, 24 * density)
            assertEquals(listOf(Haptic.Tap), felt)
            assertEquals(1, presses)
        }
        Rig { IconTap(R1Icons.Next, "Next", { presses++ }, enabled = false) }.use {
            tap(24 * density, 24 * density)
            assertEquals(emptyList(), felt)
        }
    }

    @Test
    fun aSwitchFeelsOnAndOffAndTheRowAndTheSwitchFeelOnceEach() {
        var on by mutableStateOf(false)
        Rig { SwitchRow("Wake word", on, { on = it }) }.use {
            tap(40 * density, 28 * density)
            assertEquals(listOf(Haptic.On), felt, "the row's text, off to on")
            assertTrue(on)
            frames(2)
            // The switch sits at the row's end, inside the gutter.
            tap(widthPx - 46 * density, 28 * density)
            assertEquals(listOf(Haptic.On, Haptic.Off), felt, "the switch itself, on to off, felt once")
            assertTrue(!on)
        }
    }

    @Test
    fun theVolumeTicksPerSpeakerStepAndKnocksOnceAtTheTop() {
        var pct by mutableStateOf(40)
        Rig {
            VolumePill(VolumeState(pct, VolumeScale(15), enabled = true), onDrag = { pct = it }, onDone = {})
        }.use {
            val y = 36 * density
            // Well past the right end: the level stops at 100 and the knock is not repeated while the finger goes on.
            drag(widthPx * 0.3f, widthPx * 0.3f + widthPx * 0.8f, y)
            assertEquals(100, pct)
            assertEquals(List(8) { Haptic.Tick } + Haptic.Knock, felt, "40% is step 6 of 15: steps 7 to 14 tick, 15 knocks")
        }
    }

    @Test
    fun aTapOnTheVolumeWritesNothingAndFeelsNothing() {
        var pct by mutableStateOf(40)
        Rig { VolumePill(VolumeState(pct, VolumeScale(15), enabled = true), onDrag = { pct = it }, onDone = {}) }.use {
            tap(widthPx * 0.9f, 36 * density)
            assertEquals(40, pct)
            assertEquals(emptyList(), felt)
        }
    }

    @Test
    fun theVolumeDraggedDownKnocksAtSilence() {
        var pct by mutableStateOf(20)
        Rig { VolumePill(VolumeState(pct, VolumeScale(15), enabled = true), onDrag = { pct = it }, onDone = {}) }.use {
            drag(widthPx * 0.9f, widthPx * 0.9f - widthPx * 0.5f, 36 * density)
            assertEquals(0, pct)
            assertEquals(listOf(Haptic.Tick, Haptic.Tick, Haptic.Knock), felt, "20% is step 3: 2 and 1 tick, 0 knocks")
        }
    }

    @Test
    fun aSteppedSliderTicksAndKnocksAtItsEnd() {
        var value by mutableFloatStateOf(1000f)
        Rig {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                R1Slider(value = value, onValueChange = { value = it }, valueRange = 0f..2000f, steps = 19)
            }
        }.use {
            drag(widthPx / 2, widthPx - 4 * density, 24 * density)
            assertEquals(2000f, value)
            assertEquals(Haptic.Knock, felt.last(), "$felt")
            assertEquals(listOf(Haptic.Knock), felt.filter { it != Haptic.Tick }, "$felt")
            assertEquals(9, felt.count { it == Haptic.Tick }, "1000 to 2000 by 100: notches 11 to 19 tick, $felt")
        }
    }

    @Test
    fun theSeekBarIsNotFelt() {
        var value by mutableFloatStateOf(100f)
        Rig {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                R1Slider(value = value, onValueChange = { value = it }, valueRange = 0f..600f)
            }
        }.use {
            drag(widthPx / 4, widthPx - 4 * density, 24 * density)
            assertTrue(value > 500f, "the drag moved it: $value")
            assertEquals(emptyList(), felt)
        }
    }

    @Test
    fun aSecondDragIsMeasuredFromWhereTheFirstLeftOff() {
        var value by mutableFloatStateOf(1000f)
        Rig {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                R1Slider(value = value, onValueChange = { value = it }, valueRange = 0f..2000f, steps = 19)
            }
        }.use {
            drag(widthPx / 2, widthPx - 4 * density, 24 * density)
            felt.clear()
            // Back from the top: leaving an end ticks, it does not knock again.
            drag(widthPx - 20 * density, widthPx / 2, 24 * density)
            assertTrue(felt.isNotEmpty() && felt.all { it == Haptic.Tick }, "$felt")
        }
    }

    @Test
    fun aRowTapInAListIsATap() {
        var opened = 0
        Rig { SettingsRow(icon = R1Icons.Volume, title = "Audio", onClick = { opened++ }) }.use {
            tap(widthPx / 2, 28 * density)
            assertEquals(1, opened)
            assertEquals(listOf(Haptic.Tap), felt)
        }
    }
}
