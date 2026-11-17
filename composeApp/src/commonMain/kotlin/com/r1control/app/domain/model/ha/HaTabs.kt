package com.r1control.app.domain.model.ha

import com.r1control.app.domain.model.moved

/**
 * The person's dashboards as tabs along the top: each a named, iconed [HaDashboard] of its own, with one
 * selected. A tab is the app's own grouping of devices, kept phone-local; it names Home Assistant entities
 * but never changes them, so deleting a tab drops the app's layout for it and leaves Home Assistant untouched.
 *
 * Every per-tab layout edit (sections, tiles, drags) stays the pure [HaDashboard] edits in HaLayoutEdits.kt,
 * reached through [onCurrent]. This holds only what tabs add: which dashboards there are, and which is open.
 */
data class HaTabs(val tabs: List<HaTab>, val selected: TabId) {
    /** The open tab, or the first when [selected] names none (a deleted tab, a fresh store). Null only when there are none. */
    val current: HaTab? get() = tabs.firstOrNull { it.id == selected } ?: tabs.firstOrNull()

    val currentIndex: Int get() = tabs.indexOfFirst { it.id == current?.id }

    companion object {
        val Empty = HaTabs(emptyList(), TabId(""))

        /** One tab holding [dashboard], the shape a store seeded before tabs migrates to. */
        fun of(dashboard: HaDashboard, id: TabId = TabId("tab0"), name: String = "", icon: String = ""): HaTabs =
            HaTabs(listOf(HaTab(id, name, icon, dashboard)), id)
    }
}

/** A dashboard with a tab's name and icon. An empty [name] shows a default; [icon] is an `mdi:` name or empty. */
data class HaTab(val id: TabId, val name: String, val icon: String, val dashboard: HaDashboard)

/** App-assigned and stable, so a tab can be renamed, re-iconed or reselected by an edit later. */
@kotlin.jvm.JvmInline
value class TabId(val value: String)

// The tab edits a person can make. Each is pure: it takes the tabs and returns the next, never throws, and
// treats an unknown id or an index out of range as the smallest sensible thing. Ids are the caller's, so the
// same edit applied twice converges.

/** Appended last, empty, and selected. Ignored when a tab with [id] is already there. */
fun HaTabs.addTab(id: TabId, name: String, icon: String): HaTabs =
    if (tabs.any { it.id == id }) this
    else copy(tabs = tabs + HaTab(id, name, icon, HaDashboard.Empty), selected = id)

fun HaTabs.renameTab(id: TabId, name: String): HaTabs = changeTab(id) { it.copy(name = name) }

fun HaTabs.setTabIcon(id: TabId, icon: String): HaTabs = changeTab(id) { it.copy(icon = icon) }

/**
 * The tab and its whole dashboard. When the open tab goes, the one in its place is selected, else the new
 * last. Deleting the only tab leaves none, and [HaTabs.current] is then null.
 */
fun HaTabs.deleteTab(id: TabId): HaTabs {
    val at = tabs.indexOfFirst { it.id == id }
    if (at < 0) return this
    val rest = tabs.filterNot { it.id == id }
    val nextSelected = when {
        rest.isEmpty() -> TabId("")
        id != selected -> selected
        else -> rest[at.coerceIn(rest.indices)].id
    }
    return HaTabs(rest, nextSelected)
}

/** The tab at [from] ends up at index [to] of the result; [to] is clamped. Selection follows the tab, not the slot. */
fun HaTabs.moveTab(from: Int, to: Int): HaTabs {
    if (tabs.isEmpty()) return this
    val next = tabs.moved(from, to.coerceIn(tabs.indices))
    return if (next === tabs) this else copy(tabs = next)
}

/** Opens [id]; an unknown id changes nothing. */
fun HaTabs.selectTab(id: TabId): HaTabs = if (tabs.any { it.id == id }) copy(selected = id) else this

/** Applies a [HaDashboard] edit to the open tab's dashboard. The bridge to every edit in HaLayoutEdits.kt. */
fun HaTabs.onCurrent(edit: (HaDashboard) -> HaDashboard): HaTabs {
    val open = current ?: return this
    return changeTab(open.id) { it.copy(dashboard = edit(it.dashboard)) }
}

private fun HaTabs.changeTab(id: TabId, change: (HaTab) -> HaTab): HaTabs =
    if (tabs.none { it.id == id }) this else copy(tabs = tabs.map { if (it.id == id) change(it) else it })

/** A tab id no tab here has: one past the highest `tab<number>` in use. */
fun HaTabs.nextTabId(): TabId =
    TabId("tab" + (1 + (tabs.maxOfOrNull { numberAfterTab(it.id.value) } ?: -1)))

private fun numberAfterTab(id: String): Int =
    if (id.startsWith("tab")) id.drop(3).toIntOrNull()?.takeIf { it >= 0 } ?: -1 else -1
