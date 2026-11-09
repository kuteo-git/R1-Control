package com.r1control.app.qa.round3

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.r1control.app.domain.model.NewsCategory
import com.r1control.app.domain.model.NewsSettings
import com.r1control.app.domain.model.SongList
import com.r1control.app.domain.model.Track
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.ui.Loadable
import com.r1control.app.ui.Problem
import com.r1control.app.ui.components.rememberListDrag
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.music.SongMark
import com.r1control.app.ui.music.songItems
import com.r1control.app.ui.settings.NewsAction
import com.r1control.app.ui.settings.NewsActions
import com.r1control.app.ui.settings.NewsContent
import com.r1control.app.ui.settings.NewsDraft
import com.r1control.app.ui.settings.NewsState
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Space
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The drag handle under a real finger (an `ImageComposeScene`), on rows of
 * unequal height and on the News page. The existing gesture test drags a
 * normal row down past a tall one; these take the other directions and the
 * longer travels, where a shared row height would put the drop in the wrong slot.
 */
@OptIn(ExperimentalComposeUiApi::class)
class Round3DragGestureTest {
    private val density = 3f

    /** The most of a finger's travel that touch slop can eat before the drag starts. */
    private val slop = 18f * density

    private inner class Scene(handleDp: Float, content: @Composable (LazyListState) -> Unit) {
        val list = LazyListState()
        private var time = 0L
        val handleX = handleDp * density
        val scene = ImageComposeScene(width = (390 * density).toInt(), height = (700 * density).toInt(), density = Density(density)) {
            CompositionLocalProvider(LocalStrings provides EnglishStrings) {
                R1Theme {
                    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                        content(list)
                    }
                }
            }
        }

        init { frames(5) }

        fun frames(n: Int) = repeat(n) { scene.render(time); time += 16_000_000L }

        private fun send(type: PointerEventType, y: Float) =
            scene.sendPointerEvent(type, Offset(handleX, y), timeMillis = time / 1_000_000, type = PointerType.Touch)

        fun row(key: String) = list.layoutInfo.visibleItemsInfo.first { it.key == key }
        fun centre(key: String) = row(key).let { it.offset + it.size / 2f - list.layoutInfo.viewportStartOffset }
        fun size(key: String) = row(key).size.toFloat()

        /** Press the handle at [fromY], move by [dy] in small steps a frame each, let go. */
        fun drag(fromY: Float, dy: Float, step: Float = 4f) {
            send(PointerEventType.Press, fromY)
            frames(2)
            var at = fromY
            val target = fromY + dy
            while (at != target) {
                at = if (dy > 0) minOf(target, at + step) else maxOf(target, at - step)
                send(PointerEventType.Move, at)
                frames(1)
            }
            send(PointerEventType.Release, at)
            frames(3)
        }

        fun close() = scene.close()
    }

    private fun songs(n: Int) = SongList.of(List(n) { Track("s$it", "Song $it", "Artist", "3:00", "") })

    /** A song list where [tallId]'s row carries the failed-play line. */
    private fun songScene(tallId: String, moves: MutableList<Pair<Int, Int>>) = Scene(Space.Gutter.value + 20f) { list ->
        val songs = songs(10)
        val drag = rememberListDrag(songs.tracks)
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(horizontal = Space.Gutter, vertical = Space.Small)) {
            songItems(
                EnglishStrings, songs, SongMark.Failed(tallId, SpeakerException.Refused("queue busy")), drag, list,
                onPlay = {}, onMove = { f, t -> moves += f to t }, onRemove = {}, onAdd = null,
            )
        }
    }

    /** R3-03. Going up past a taller row: the step is half of that row's own height. */
    @Test fun aRowDraggedUpPassesATallerRowAtHalfItsHeight() {
        for ((extra, expected) in listOf(-40f to null, 14f to (2 to 1))) {
            val moves = mutableListOf<Pair<Int, Int>>()
            val s = songScene("s1", moves)
            try {
                val tall = s.size("song:s1")
                assertTrue(tall > s.size("song:s2") + 10f, "set up: s1 is the taller row")
                s.drag(s.centre("song:s2"), -(slop + tall / 2f + extra))
                assertEquals(listOfNotNull(expected), moves, "dragged ${tall / 2f + extra}px past slop up, over a row of $tall px")
            } finally {
                s.close()
            }
        }
    }

    /** R3-03. The tall row itself, dragged down past a normal row: the step is half of the normal row. */
    @Test fun theTallRowMovesDownPastANormalRowAtHalfTheNormalRowsHeight() {
        for ((extra, expected) in listOf(-40f to null, 14f to (1 to 2))) {
            val moves = mutableListOf<Pair<Int, Int>>()
            val s = songScene("s1", moves)
            try {
                val normal = s.size("song:s2")
                s.drag(s.centre("song:s1"), slop + normal / 2f + extra)
                assertEquals(listOfNotNull(expected), moves, "dragged ${normal / 2f + extra}px past slop down, over a row of $normal px")
            } finally {
                s.close()
            }
        }
    }

    /** R3-03. One long travel across a tall and a normal row lands two places down, not where a shared height would put it. */
    @Test fun aLongDragAcrossATallAndANormalRowLandsTwoPlacesDown() {
        val moves = mutableListOf<Pair<Int, Int>>()
        val s = songScene("s1", moves)
        try {
            val tall = s.size("song:s1")
            val normal = s.size("song:s2")
            s.drag(s.centre("song:s0"), slop + tall + normal / 2f + 14f)
            assertEquals(listOf(0 to 2), moves, "tall $tall, normal $normal")
        } finally {
            s.close()
        }
    }

    private val categories = listOf("tech", "world", "society", "weather", "power").map { NewsCategory(it, true) }

    private fun newsScene(enabled: Boolean, moves: MutableList<Pair<Int, Int>>) = Scene(Space.Small.value + 20f) { _ ->
        NewsContent(
            NewsState(Loadable.Loaded(NewsDraft(NewsSettings(enabled, "07:00", "b", categories), listOf("b"))), NewsAction.Idle, Problem.None),
            NewsActions(onMoved = { f, t -> moves += f to t }),
        )
    }

    /** The y of a news category's handle: found by its accessibility label, "Move <name>". */
    private fun Scene.newsHandleY(name: String): Float {
        val nodes = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
        val node = nodes.first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Move $name") == true }
        return node.boundsInRoot.center.y
    }

    private fun Scene.newsRowHeight(): Float {
        val nodes = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
        val handles = nodes.filter { it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.startsWith("Move ") } == true }
            .map { it.boundsInRoot.center.y }.sorted()
        return handles[1] - handles[0]
    }

    /** R3-41. With the bulletin on, a category dragged by its handle moves, once, on release. */
    @Test fun aNewsCategoryMovesByItsHandleWhileTheBulletinIsOn() {
        val moves = mutableListOf<Pair<Int, Int>>()
        val s = newsScene(enabled = true, moves)
        try {
            val first = EnglishStrings.newsCategory("tech")
            s.drag(s.newsHandleY(first), slop + s.newsRowHeight() + 14f)
            assertEquals(listOf(0 to 1), moves)
        } finally {
            s.close()
        }
    }

    /** R3-42. With the bulletin off, the rows are dimmed and the handle takes no drag. */
    @Test fun aNewsCategoryCannotBeDraggedWhileTheBulletinIsOff() {
        val moves = mutableListOf<Pair<Int, Int>>()
        val s = newsScene(enabled = false, moves)
        try {
            val first = EnglishStrings.newsCategory("tech")
            s.drag(s.newsHandleY(first), slop + 2 * s.newsRowHeight() + 14f)
            assertTrue(moves.isEmpty(), "$moves")
        } finally {
            s.close()
        }
    }
}
