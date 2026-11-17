package com.r1control.app.domain

import com.r1control.app.domain.model.ha.HaDashboard
import com.r1control.app.domain.model.ha.HaTabs
import com.r1control.app.domain.model.ha.TabId
import com.r1control.app.domain.model.ha.addSection
import com.r1control.app.domain.model.ha.addTab
import com.r1control.app.domain.model.ha.deleteTab
import com.r1control.app.domain.model.ha.moveTab
import com.r1control.app.domain.model.ha.nextTabId
import com.r1control.app.domain.model.ha.onCurrent
import com.r1control.app.domain.model.ha.renameTab
import com.r1control.app.domain.model.ha.selectTab
import com.r1control.app.domain.model.ha.setTabIcon
import com.r1control.app.domain.model.ha.SectionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HaTabsTest {
    private val base = HaTabs.of(HaDashboard.Empty, TabId("tab0"), name = "Home", icon = "mdi:home")

    @Test fun currentIsTheSelectedTabElseTheFirst() {
        assertEquals("tab0", base.current?.id?.value)
        // A selection naming nobody falls back to the first, never crashes.
        assertEquals("tab0", base.copy(selected = TabId("gone")).current?.id?.value)
        assertNull(HaTabs.Empty.current)
    }

    @Test fun addAppendsEmptyAndSelectsIt() {
        val two = base.addTab(TabId("tab1"), "Lights", "mdi:lightbulb")
        assertEquals(listOf("tab0", "tab1"), two.tabs.map { it.id.value })
        assertEquals("tab1", two.selected.value)
        assertTrue(two.tabs[1].dashboard.sections.isEmpty())
    }

    @Test fun addIsIdempotentOnItsId() {
        val once = base.addTab(TabId("tab1"), "Lights", "mdi:lightbulb")
        assertEquals(once, once.addTab(TabId("tab1"), "Other", "mdi:fan"))
    }

    @Test fun renameAndIconChangeOnlyThatTab() {
        val edited = base.renameTab(TabId("tab0"), "House").setTabIcon(TabId("tab0"), "mdi:sofa")
        assertEquals("House", edited.current?.name)
        assertEquals("mdi:sofa", edited.current?.icon)
    }

    @Test fun deleteOfTheOpenTabSelectsTheOneInItsPlace() {
        val three = base
            .addTab(TabId("tab1"), "B", "")
            .addTab(TabId("tab2"), "C", "")
            .selectTab(TabId("tab1"))
        val after = three.deleteTab(TabId("tab1"))
        assertEquals(listOf("tab0", "tab2"), after.tabs.map { it.id.value })
        assertEquals("tab2", after.selected.value) // the tab that slid into index 1
    }

    @Test fun deleteOfAnotherTabKeepsTheSelection() {
        val three = base.addTab(TabId("tab1"), "B", "").addTab(TabId("tab2"), "C", "").selectTab(TabId("tab0"))
        assertEquals("tab0", three.deleteTab(TabId("tab2")).selected.value)
    }

    @Test fun deleteOfTheLastTabLeavesNone() {
        val empty = base.deleteTab(TabId("tab0"))
        assertTrue(empty.tabs.isEmpty())
        assertNull(empty.current)
    }

    @Test fun moveReordersAndSelectionFollowsTheTab() {
        val three = base.addTab(TabId("tab1"), "B", "").addTab(TabId("tab2"), "C", "").selectTab(TabId("tab0"))
        val moved = three.moveTab(0, 2)
        assertEquals(listOf("tab1", "tab2", "tab0"), moved.tabs.map { it.id.value })
        assertEquals("tab0", moved.selected.value)
    }

    @Test fun onCurrentEditsOnlyTheOpenTabsDashboard() {
        val two = base.addTab(TabId("tab1"), "B", "").selectTab(TabId("tab0"))
        val edited = two.onCurrent { it.addSection(SectionId("s0"), "Group") }
        assertEquals(1, edited.tabs[0].dashboard.sections.size)
        assertTrue(edited.tabs[1].dashboard.sections.isEmpty())
    }

    @Test fun nextTabIdIsOnePastTheHighest() {
        val two = base.addTab(TabId("tab1"), "B", "")
        assertEquals("tab2", two.nextTabId().value)
        assertEquals("tab0", HaTabs.Empty.nextTabId().value)
    }
}
