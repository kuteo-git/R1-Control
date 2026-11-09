package com.r1control.app.domain

import com.r1control.app.domain.model.mixInterleaved
import com.r1control.app.testing.track
import kotlin.test.Test
import kotlin.test.assertEquals

class MixInterleavedTest {
    private fun ids(lists: List<List<String>>) =
        mixInterleaved(lists.map { l -> l.map { track(it) } }).map { it.id }

    @Test fun roundRobin() {
        assertEquals(listOf("a1", "b1", "a2", "b2"), ids(listOf(listOf("a1", "a2"), listOf("b1", "b2"))))
    }

    @Test fun unevenListsRunOutIndependently() {
        assertEquals(
            listOf("a1", "b1", "c1", "a2", "b2", "a3"),
            ids(listOf(listOf("a1", "a2", "a3"), listOf("b1", "b2"), listOf("c1"))),
        )
    }

    @Test fun emptyListsContributeNothing() {
        assertEquals(listOf("a1", "a2"), ids(listOf(emptyList(), listOf("a1", "a2"), emptyList())))
        assertEquals(emptyList(), ids(emptyList()))
        assertEquals(emptyList(), ids(listOf(emptyList(), emptyList())))
    }

    @Test fun aSongInTwoListsKeepsItsFirstPlace() {
        assertEquals(
            listOf("x", "b1", "a2", "b2"),
            ids(listOf(listOf("x", "a2"), listOf("b1", "x", "b2"))),
        )
    }

    @Test fun aRepeatWithinOneListIsDropped() {
        assertEquals(listOf("a", "b", "c"), ids(listOf(listOf("a", "a", "c"), listOf("b"))))
    }

    @Test fun theKeptDuplicateIsTheFirstTrackObject() {
        val first = track("x", title = "first")
        val second = track("x", title = "second")
        assertEquals("first", mixInterleaved(listOf(listOf(first), listOf(second))).single().title)
    }
}
