package com.r1control.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.r1control.app.domain.model.SongList
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.music.SongMark
import com.r1control.app.ui.music.songItems
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Space
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The handle, driven by a finger: pressed, dragged, held at an edge. Only real
 * pointer events prove that the list scrolls under a held row (mytube's first
 * drag shipped "not seen working"), so these run the gesture through a scene.
 */
@OptIn(ExperimentalComposeUiApi::class)
class EditableRowsGestureTest {
    private val density = 3f

    private fun songs(n: Int) = SongList.of(List(n) { Track("s$it", "Song $it", "Artist", "3:00", "") })

    /** One scene, its clock, and what the list reported. */
    private inner class Rig(
        songs: SongList,
        mark: SongMark = SongMark.None,
        enabled: Boolean = true,
        heightDp: Int = 600,
    ) {
        val list = LazyListState()
        val moves = mutableListOf<Pair<Int, Int>>()
        val removed = mutableListOf<String>()
        private var time = 0L
        val scene = ImageComposeScene(width = (390 * density).toInt(), height = (heightDp * density).toInt(), density = Density(density)) {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                R1Theme {
                    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                        val drag = rememberListDrag(songs.tracks)
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            state = list,
                            contentPadding = PaddingValues(horizontal = Space.Gutter, vertical = Space.Small),
                        ) {
                            songItems(
                                EnglishStrings, songs, mark, drag, list,
                                onPlay = {}, onMove = { f, t -> moves += f to t }, onRemove = { removed += it.id }, onAdd = null,
                            )
                        }
                    }
                }
            }
        }

        init { frames(5) }

        fun frames(n: Int) = repeat(n) { scene.render(time); time += 16_000_000L }

        fun press(x: Float, y: Float) = scene.sendPointerEvent(PointerEventType.Press, Offset(x, y), timeMillis = time / 1_000_000, type = PointerType.Touch)
        fun move(x: Float, y: Float) = scene.sendPointerEvent(PointerEventType.Move, Offset(x, y), timeMillis = time / 1_000_000, type = PointerType.Touch)
        fun release(x: Float, y: Float) = scene.sendPointerEvent(PointerEventType.Release, Offset(x, y), timeMillis = time / 1_000_000, type = PointerType.Touch)

        /** Moves in small steps, a frame each, as a finger does. */
        fun dragTo(x: Float, fromY: Float, toY: Float, step: Float = 12f): Float {
            var at = fromY
            val dir = if (toY > fromY) 1 else -1
            while (at != toY) {
                at = if (dir > 0) minOf(toY, at + step) else maxOf(toY, at - step)
                move(x, at)
                frames(1)
            }
            return at
        }

        /** The handle's centre: inside the list's side margin, at the row's middle. */
        fun handleX() = (Space.Gutter.value + 20f) * density
        fun rowCentre(key: String): Float {
            val info = list.layoutInfo.visibleItemsInfo.first { it.key == key }
            return info.offset + info.size / 2f + (-list.layoutInfo.viewportStartOffset)
        }
        fun rowSize(key: String) = list.layoutInfo.visibleItemsInfo.first { it.key == key }.size.toFloat()

        fun close() = scene.close()
    }

    @Test
    fun aDragOfTwoRowsMovesTheSongTwoPlaces() {
        val rig = Rig(songs(10))
        try {
            val size = rig.rowSize("song:s0")
            val y = rig.rowCentre("song:s0")
            rig.press(rig.handleX(), y)
            rig.frames(2)
            val end = rig.dragTo(rig.handleX(), y, y + 2 * size + 4f)
            rig.release(rig.handleX(), end)
            rig.frames(3)
            assertEquals(listOf(0 to 2), rig.moves)
        } finally {
            rig.close()
        }
    }

    @Test
    fun aDragLessThanHalfARowMovesNothing() {
        val rig = Rig(songs(10))
        try {
            val y = rig.rowCentre("song:s3")
            rig.press(rig.handleX(), y)
            rig.frames(2)
            val end = rig.dragTo(rig.handleX(), y, y + 18f * density + rig.rowSize("song:s4") / 2f - 40f)
            rig.release(rig.handleX(), end)
            rig.frames(3)
            assertTrue(rig.moves.isEmpty(), "${rig.moves}")
        } finally {
            rig.close()
        }
    }

    /** A row with the failed-play line is taller than the others; its half height is the step, not a shared row height. */
    @Test
    fun aTallerRowIsPassedAtHalfItsOwnHeight() {
        val error = SpeakerException.Refused("queue busy")
        val tall = SongMark.Failed("s1", error)
        // The first part of a finger's travel is touch slop, not drag; 18 dp is the most it can be, so the margins keep both sides honest.
        val slop = 18f * density
        for ((extra, expected) in listOf(-40f to null, 14f to (0 to 1))) {
            val rig = Rig(songs(10), mark = tall)
            try {
                val normal = rig.rowSize("song:s0")
                val taller = rig.rowSize("song:s1")
                assertTrue(taller > normal + 10f, "the failed row is taller: $taller vs $normal")
                val y = rig.rowCentre("song:s0")
                rig.press(rig.handleX(), y)
                rig.frames(2)
                val end = rig.dragTo(rig.handleX(), y, y + slop + taller / 2f + extra, step = 4f)
                rig.release(rig.handleX(), end)
                rig.frames(3)
                assertEquals(listOfNotNull(expected), rig.moves, "dragged ${taller / 2f + extra}px past slop, a row of $taller px")
            } finally {
                rig.close()
            }
        }
    }

    /** "khi drag item lên top or bottom nó ko scroll": held at the bottom edge, the list scrolls and the row lands past what was on screen. */
    @Test
    fun holdingAtTheBottomEdgeScrollsAndKeepsTheRowUnderTheFinger() {
        val rig = Rig(songs(40))
        try {
            val lastVisible = rig.list.layoutInfo.visibleItemsInfo.last().index
            val y = rig.rowCentre("song:s2")
            rig.press(rig.handleX(), y)
            rig.frames(2)
            val bottom = rig.list.layoutInfo.viewportEndOffset - 10f
            val end = rig.dragTo(rig.handleX(), y, bottom, step = 40f)
            rig.frames(60)
            assertTrue(rig.list.firstVisibleItemIndex > 0, "the list did not scroll under the held row")
            rig.release(rig.handleX(), end)
            rig.frames(3)
            val (from, to) = assertNotNull(rig.moves.singleOrNull(), "letting go moved nothing: ${rig.moves}")
            assertEquals(2, from, "the row picked up is the one that moved")
            assertTrue(to > lastVisible, "landed at $to, no further than the screen first showed ($lastVisible)")
        } finally {
            rig.close()
        }
    }

    @Test
    fun holdingAtTheTopEdgeScrollsBackUp() {
        val rig = Rig(songs(40))
        try {
            runCatching { rig.list.dispatchRawDelta(4000f) }
            rig.frames(3)
            val first = rig.list.firstVisibleItemIndex
            assertTrue(first > 5, "set up: the list starts scrolled down ($first)")
            val key = "song:s${first + 2}"
            val y = rig.rowCentre(key)
            rig.press(rig.handleX(), y)
            rig.frames(2)
            val end = rig.dragTo(rig.handleX(), y, 10f, step = 40f)
            rig.frames(60)
            assertTrue(rig.list.firstVisibleItemIndex < first, "the list did not scroll up under the held row")
            rig.release(rig.handleX(), end)
            rig.frames(3)
            val (from, to) = assertNotNull(rig.moves.singleOrNull(), "${rig.moves}")
            assertEquals(first + 2, from)
            assertTrue(to < first, "landed at $to, no higher than the screen first showed ($first)")
        } finally {
            rig.close()
        }
    }

    @Test
    fun aDragOutsideTheHandleScrollsTheListAndMovesNothing() {
        val rig = Rig(songs(40))
        try {
            val y = rig.rowCentre("song:s3")
            val x = 200f * density
            rig.press(x, y)
            rig.frames(2)
            val end = rig.dragTo(x, y, y - 300f, step = 20f)
            rig.release(x, end)
            rig.frames(3)
            assertTrue(rig.moves.isEmpty(), "${rig.moves}")
            assertTrue(rig.list.firstVisibleItemIndex > 0 || rig.list.firstVisibleItemScrollOffset > 0, "the page did not scroll")
        } finally {
            rig.close()
        }
    }

    @Test
    fun theXRemovesASongAndIsDisabledAtOneSongLeft() {
        val many = Rig(songs(3))
        try {
            val y = many.rowCentre("song:s1")
            val x = (390f - Space.Gutter.value - 24f) * density
            many.press(x, y); many.frames(1); many.release(x, y); many.frames(3)
            assertEquals(listOf("s1"), many.removed)
        } finally {
            many.close()
        }
        val one = Rig(songs(1))
        try {
            val y = one.rowCentre("song:s0")
            val x = (390f - Space.Gutter.value - 24f) * density
            one.press(x, y); one.frames(1); one.release(x, y); one.frames(3)
            assertTrue(one.removed.isEmpty(), "a list never loses its last song to an X")
        } finally {
            one.close()
        }
    }

    @Test
    fun aDisabledListHasNoGesture() {
        val moves = mutableListOf<Pair<Int, Int>>()
        val list = LazyListState()
        val scene = ImageComposeScene(width = (390 * density).toInt(), height = (600 * density).toInt(), density = Density(density)) {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                R1Theme {
                    Surface {
                        val items = songs(5).tracks
                        val drag = rememberListDrag(items)
                        LazyColumn(Modifier.fillMaxSize(), state = list) {
                            editableItems(
                                items, { "k:" + it.id }, drag, list, enabled = false, onMove = { f, t -> moves += f to t },
                                onRemove = null, canRemove = false, handleLabel = { it.title }, removeLabel = { "" },
                            ) { androidx.compose.material3.Text(it.title) }
                        }
                    }
                }
            }
        }
        var time = 0L
        fun frames(n: Int) = repeat(n) { scene.render(time); time += 16_000_000L }
        try {
            frames(5)
            val row = list.layoutInfo.visibleItemsInfo.first { it.key == "k:s0" }
            val x = 20f * density
            val y = row.offset + row.size / 2f
            scene.sendPointerEvent(PointerEventType.Press, Offset(x, y), timeMillis = 0, type = PointerType.Touch)
            frames(2)
            var at = y
            repeat(10) { at += 30f; scene.sendPointerEvent(PointerEventType.Move, Offset(x, at), timeMillis = time / 1_000_000, type = PointerType.Touch); frames(1) }
            scene.sendPointerEvent(PointerEventType.Release, Offset(x, at), timeMillis = time / 1_000_000, type = PointerType.Touch)
            frames(3)
            assertTrue(moves.isEmpty(), "$moves")
            assertNull(moves.firstOrNull())
        } finally {
            scene.close()
        }
    }

    /** UX-07. A screen reader cannot drag: each handle carries Move up and Move down, through the same onMove. */
    @Test
    fun eachHandleMovesItsRowUpOrDownForAScreenReader() {
        val rig = Rig(songs(3))
        try {
            fun steps(title: String): Map<String, () -> Boolean> {
                val node = rig.scene.semanticsOwners
                    .flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
                    .single { it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf(EnglishStrings.reorderSong(title)) }
                return node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().associate { it.label to it.action }
            }
            assertEquals(setOf(EnglishStrings.moveDown), steps("Song 0").keys, "the first row cannot go up")
            assertEquals(setOf(EnglishStrings.moveUp, EnglishStrings.moveDown), steps("Song 1").keys)
            assertEquals(setOf(EnglishStrings.moveUp), steps("Song 2").keys, "the last row cannot go down")

            steps("Song 1").getValue(EnglishStrings.moveDown)()
            steps("Song 1").getValue(EnglishStrings.moveUp)()
            assertEquals(listOf(1 to 2, 1 to 0), rig.moves)
        } finally {
            rig.close()
        }
    }
}
