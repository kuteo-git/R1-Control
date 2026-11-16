package com.r1control.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
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
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.R1Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The level slider commits every drag, not only the first. Home Assistant pushes a new value back after each
 * commit, which once recreated the slider's state and orphaned the running drag gesture, so a second drag
 * wrote to a dead state and sent nothing. This drives two drags through a scene and proves both land.
 */
@OptIn(ExperimentalComposeUiApi::class)
class VerticalLevelSliderGestureTest {
    private val density = 3f

    private inner class Rig(content: @Composable () -> Unit) {
        private var time = 0L
        val scene = ImageComposeScene(width = (120 * density).toInt(), height = (220 * density).toInt(), density = Density(density)) {
            CompositionLocalProvider(LocalStrings provides EnglishStrings, LocalHaptics provides Haptics {}) {
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

        /** Presses at [fromY], moves a few pixels a frame to [toY], lets go. */
        fun vdrag(fromY: Float, toY: Float, x: Float, step: Float = 6f) {
            send(PointerEventType.Press, x, fromY); frames(1)
            var at = fromY
            while (at != toY) {
                at = if (toY > at) minOf(toY, at + step) else maxOf(toY, at - step)
                send(PointerEventType.Move, x, at); frames(1)
            }
            send(PointerEventType.Release, x, at); frames(3)
        }

        fun use(block: Rig.() -> Unit) = try { block() } finally { scene.close() }
    }

    @Test
    fun eachDragCommitsNotOnlyTheFirst() {
        var percent by mutableStateOf(0)
        val commits = mutableListOf<Int>()
        Rig {
            VerticalLevelSlider(
                percent = percent,
                enabled = true,
                icon = R1Icons.Fan,
                description = "Speed",
                valueText = { "$it%" },
                onCommit = { commits += it; percent = it },
            )
        }.use {
            val x = 40 * density
            // Up from the bottom: a big rise.
            vdrag(fromY = 150 * density, toY = 30 * density, x = x)
            val first = percent
            assertTrue(first > 0, "the first drag set it: $first")
            frames(2)
            // Up again, a smaller rise, starting from the committed value.
            vdrag(fromY = 150 * density, toY = 90 * density, x = x)
            assertEquals(2, commits.size, "both drags committed: $commits")
            assertTrue(commits[1] != commits[0] && percent == commits[1], "the second drag changed the value too: $commits")
        }
    }
}
