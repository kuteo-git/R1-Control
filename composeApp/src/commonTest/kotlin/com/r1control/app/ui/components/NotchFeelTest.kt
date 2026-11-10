package com.r1control.app.ui.components

import com.r1control.app.domain.model.VolumeScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotchFeelTest {

    /** A finger sweeping [path] one percent at a time, felt the way the volume bar feels it. */
    private fun sweep(path: IntProgression, steps: Int): List<Haptic> {
        val last = volumeNotches(steps)
        var felt = notchOf(path.first.toFloat(), 0f..100f, last)
        return path.mapNotNull { pct ->
            val notch = notchOf(pct.toFloat(), 0f..100f, last)
            notchFeel(felt, notch, last).also { felt = notch }
        }
    }

    @Test
    fun theVolumeTicksOncePerHardwareStepAndKnocksAtTheTop() {
        // 40% on a 15-step speaker is step 6; 7 to 14 tick, 15 is the top.
        assertEquals(List(8) { Haptic.Tick } + Haptic.Knock, sweep(40..100, steps = 15))
    }

    @Test
    fun theVolumeKnocksAtZeroToo() {
        assertEquals(List(5) { Haptic.Tick } + Haptic.Knock, sweep(40 downTo 0, steps = 15))
    }

    @Test
    fun aVolumeWithoutHardwareStepsTicksEveryFivePercent() {
        assertEquals(List(9) { Haptic.Tick } + Haptic.Knock, sweep(50..100, steps = 0))
    }

    @Test
    fun theNotchesAreTheSpeakersOwnSteps() {
        val scale = VolumeScale(15)
        for (pct in 0..100) {
            val snapped = scale.snap(pct)
            assertEquals(notchOf(snapped.toFloat(), 0f..100f, 15), notchOf(pct.toFloat(), 0f..100f, 15), "$pct snaps to $snapped")
        }
    }

    @Test
    fun aFingerRestingInsideANotchFeelsNothing() {
        assertEquals(emptyList(), sweep(41..43, steps = 15), "step 7 starts at 43.3%")
        assertNull(notchFeel(3, 3, 10))
    }

    @Test
    fun aJumpPastSeveralNotchesIsOneTickNotABurst() {
        assertEquals(Haptic.Tick, notchFeel(2, 7, 10))
        assertEquals(Haptic.Knock, notchFeel(2, 10, 10))
        assertEquals(Haptic.Knock, notchFeel(9, 0, 10))
    }

    @Test
    fun leavingAnEndTicks() {
        assertEquals(Haptic.Tick, notchFeel(10, 9, 10))
        assertEquals(Haptic.Tick, notchFeel(0, 1, 10))
    }

    @Test
    fun aValueOutsideTheRangeIsAtItsEnd() {
        assertEquals(0, notchOf(-50f, 0f..100f, 10))
        assertEquals(10, notchOf(150f, 0f..100f, 10))
        assertEquals(0, notchOf(5f, 5f..5f, 10), "an empty range has one notch")
        assertEquals(0, notchOf(50f, 0f..100f, 0), "no notches")
    }

    @Test
    fun anEqualiserRangeIsCutInDecibels() {
        // -15 dB .. +15 dB at one notch per dB: 0 dB is notch 15, +1 dB is 16.
        assertEquals(15, notchOf(0f, -1500f..1500f, 30))
        assertEquals(16, notchOf(100f, -1500f..1500f, 30))
        assertEquals(15, notchOf(40f, -1500f..1500f, 30))
    }
}
