package com.r1control.app.ui.ha

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Density
import com.r1control.app.domain.model.ha.HaDashboard
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaSection
import com.r1control.app.domain.model.ha.HaTile
import com.r1control.app.domain.model.ha.SectionId
import com.r1control.app.domain.model.ha.TileId
import com.r1control.app.domain.model.ha.moveRow
import com.r1control.app.ui.components.Haptic
import com.r1control.app.ui.components.Haptics
import com.r1control.app.ui.components.LocalHaptics
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Space
import kotlin.test.Test
import kotlin.test.assertEquals

private fun tile(n: Int) = HaTile.Toggle(TileId("t$n"), "Tile $n", "", HaEntityId("light.l$n"))

/** s0: t0 t1 | s1: t2. Rows: s0, t0, t1, s1, t2. */
private val LAYOUT = HaDashboard(
    listOf(
        HaSection(SectionId("s0"), "First", listOf(tile(0), tile(1))),
        HaSection(SectionId("s1"), "Second", listOf(tile(2))),
    ),
)

/** The edit list's drag, by a finger: only real pointer events show that a tile's handle reorders across a header. */
@OptIn(ExperimentalComposeUiApi::class)
class HaEditGestureTest {
    private val density = 3f

    @Test
    fun draggingATileDownPastTheNextHeaderMovesItIntoThatSection() {
        val moves = mutableListOf<Pair<Int, Int>>()
        val felt = mutableListOf<Haptic>()
        var time = 0L
        val scene = ImageComposeScene(width = (390 * density).toInt(), height = (700 * density).toInt(), density = Density(density)) {
            CompositionLocalProvider(LocalStrings provides EnglishStrings, LocalHaptics provides Haptics { felt += it }) {
                R1Theme {
                    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                        HaEditList(LAYOUT, emptyList(), HaEditActions({}, {}, { _, _ -> }, {}, { _, _ -> }, { _, _ -> }, {}, { f, t -> moves += f to t }))
                    }
                }
            }
        }
        fun frames(n: Int) = repeat(n) { scene.render(time); time += 16_000_000L }
        fun send(type: PointerEventType, x: Float, y: Float) =
            scene.sendPointerEvent(type, Offset(x, y), timeMillis = time / 1_000_000, type = PointerType.Touch)
        try {
            frames(5)
            // The handle of row 2 (t1): inside the row's side margin. The list starts at the top of the scene.
            val x = (Space.Small.value + 20f) * density
            val row = 56f * density
            // Row 0 is the header, so row 2 starts two rows down; its middle is a little over two rows from the top.
            val y = 2.5f * row
            send(PointerEventType.Press, x, y); frames(2)
            var at = y
            val target = y + 1.5f * row
            while (at < target) { at = minOf(target, at + 12f); send(PointerEventType.Move, x, at); frames(1) }
            send(PointerEventType.Release, x, at); frames(3)
            val (from, to) = moves.single()
            assertEquals(2, from)
            assertEquals(listOf("t0"), LAYOUT.moveRow(from, to).sections[0].tiles.map { it.id.value }, "t1 left the first section")
            assertEquals(listOf("t1", "t2").sorted(), LAYOUT.moveRow(from, to).sections[1].tiles.map { it.id.value }.sorted())
        } finally {
            scene.close()
        }
    }
}
