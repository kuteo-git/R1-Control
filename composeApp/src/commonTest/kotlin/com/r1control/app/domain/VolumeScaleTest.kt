package com.r1control.app.domain

import com.r1control.app.domain.model.VolumeScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VolumeScaleTest {
    private val r1 = VolumeScale(15)

    @Test fun edgesStayAtTheEdges() {
        assertEquals(0, r1.snap(0))
        assertEquals(100, r1.snap(100))
    }

    @Test fun outOfRangeIsClamped() {
        assertEquals(0, r1.snap(-20))
        assertEquals(100, r1.snap(400))
    }

    /** 39% is 5.85 steps: the panel rounds to step 6, which reads back as 40%. */
    @Test fun thirtyNineSnapsToFortyLikeThePanel() {
        assertEquals(40, r1.snap(39))
    }

    /** A step is 6.67%: 3% is under half of the first step, 4% is over it. */
    @Test fun halfAStepRoundsToTheNearest() {
        assertEquals(0, r1.snap(3))
        assertEquals(7, r1.snap(4))
    }

    @Test fun everyStepIsAFixedPoint() {
        for (idx in 0..15) {
            val pct = (idx * 200 + 15) / 30
            assertEquals(pct, r1.snap(pct), "step $idx")
        }
    }

    @Test fun zeroStepsMeansNoHardwareScale() {
        val none = VolumeScale(0)
        assertFalse(none.hasHardwareSteps)
        assertEquals(39, none.snap(39))
        assertEquals(100, none.snap(130))
        assertTrue(r1.hasHardwareSteps)
    }

    @Test fun aScaleOfOneHundredStepsIsPercent() {
        for (p in 0..100) assertEquals(p, VolumeScale(100).snap(p))
    }
}
