package com.r1control.app.domain

import com.r1control.app.domain.model.ListEdit
import com.r1control.app.domain.model.SongList
import com.r1control.app.domain.model.cleanPlaylistName
import com.r1control.app.domain.model.moved
import com.r1control.app.domain.model.shelfOf
import com.r1control.app.domain.model.songKey
import com.r1control.app.domain.model.without
import com.r1control.app.testing.track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import com.r1control.app.domain.model.Playlist
import com.r1control.app.domain.model.PlaylistId

class SongListTest {
    private fun list(vararg ids: String) = SongList.of(ids.map { track(it) })
    private fun SongList.ids() = tracks.map { it.id }

    @Test fun ofDropsRepeatsKeepingTheFirst() {
        val songs = SongList.of(listOf(track("a", "first"), track("b"), track("a", "second")))
        assertEquals(listOf("a", "b"), songs.ids())
        assertEquals("first", songs.tracks.first().title)
    }

    @Test fun movedTakesOneItemAndPutsItBack() {
        assertEquals(listOf("b", "c", "a"), listOf("a", "b", "c").moved(0, 2))
        assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").moved(2, 0))
    }

    @Test fun moveOutOfRangeOrInPlaceReturnsTheSameInstance() {
        val songs = list("a", "b", "c")
        assertSame(songs, songs.edited(ListEdit.Move(1, 1)))
        assertSame(songs, songs.edited(ListEdit.Move(-1, 1)))
        assertSame(songs, songs.edited(ListEdit.Move(0, 3)))
        val plain = listOf("a", "b")
        assertSame(plain, plain.moved(1, 1))
        assertSame(plain, plain.moved(0, 2))
    }

    @Test fun aMoveReordersAndDoesNotTouchTheOriginal() {
        val songs = list("a", "b", "c", "d")
        assertEquals(listOf("a", "c", "d", "b"), songs.edited(ListEdit.Move(1, 3)).ids())
        assertEquals(listOf("a", "b", "c", "d"), songs.ids())
    }

    @Test fun withoutDropsOneIndexAndIgnoresAnyOtherIndex() {
        assertEquals(listOf("a", "c"), listOf("a", "b", "c").without(1))
        val plain = listOf("a")
        assertSame(plain, plain.without(5))
        assertSame(plain, plain.without(-1))
    }

    @Test fun removeIsByIdAndAbsentIsUnchanged() {
        val songs = list("a", "b", "c")
        assertEquals(listOf("a", "c"), songs.edited(ListEdit.Remove("b")).ids())
        assertSame(songs, songs.edited(ListEdit.Remove("zzz")))
    }

    @Test fun theLastSongCannotBeRemoved() {
        val one = list("a")
        assertFalse(one.canRemove)
        assertSame(one, one.edited(ListEdit.Remove("a")))
        assertTrue(list("a", "b").canRemove)
        assertFalse(SongList.Empty.canRemove)
    }

    @Test fun addSkipsSongsAlreadyThereAndRepeatsWithinTheAdd() {
        val songs = list("a", "b")
        assertEquals(listOf("a", "b", "c"), songs.edited(ListEdit.Add(listOf(track("b"), track("c"), track("c")))).ids())
        assertSame(songs, songs.edited(ListEdit.Add(listOf(track("a"), track("b")))))
        assertSame(songs, songs.edited(ListEdit.Add(emptyList())))
        assertEquals(listOf("x"), SongList.Empty.edited(ListEdit.Add(listOf(track("x")))).ids())
    }

    @Test fun lookupsFindTheOneRowAnIdNames() {
        val songs = list("a", "b")
        assertTrue(songs.contains("b"))
        assertFalse(songs.contains("z"))
        assertEquals(1, songs.indexOf("b"))
        assertEquals(-1, songs.indexOf("z"))
    }

    @Test fun songKeysAreUniqueAndNeverMeetTheTabsOtherKeys() {
        val keys = list("a", "b", "c").tracks.map(::songKey)
        assertEquals(keys.size, keys.toSet().size)
        val others = setOf("query", "hint", "recents-title", "shelf", "shelf-title", "shelf-new", "header", "empty")
        assertTrue(keys.none { it in others })
        assertTrue(keys.all { it.startsWith("song:") })
    }

    @Test fun movedAndWithoutAreTheOnlyEditsSoAnyListReordersThroughThem() {
        assertEquals(listOf("two", "one", "three"), listOf("one", "two", "three").moved(1, 0))
    }

    @Test fun aPlaylistNameIsTrimmedAndItsInnerWhitespaceCollapsed() {
        assertEquals("Nhạc xưa hay", cleanPlaylistName("  Nhạc   xưa\thay \n"))
        assertEquals("", cleanPlaylistName("   \n "))
    }

    @Test fun theShelfShowsFiveAndSaysWhenThereAreMore() {
        fun playlists(n: Int) = List(n) { Playlist(PlaylistId("p$it"), "n$it", SongList.Empty, it.toLong()) }
        assertEquals(0, shelfOf(playlists(0)).cards.size)
        assertFalse(shelfOf(playlists(5)).hasMore)
        assertEquals(5, shelfOf(playlists(6)).cards.size)
        assertTrue(shelfOf(playlists(6)).hasMore)
    }
}
