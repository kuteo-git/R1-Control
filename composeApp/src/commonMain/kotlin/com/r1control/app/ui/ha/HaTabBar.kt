package com.r1control.app.ui.ha

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import com.r1control.app.ui.components.ConfirmDialog
import com.r1control.app.ui.components.Haptic
import com.r1control.app.ui.components.LocalHaptics
import com.r1control.app.ui.components.R1TextField
import com.r1control.app.ui.components.TonalButton
import com.r1control.app.ui.components.rememberListDrag
import com.r1control.app.ui.components.tappable
import com.r1control.app.ui.theme.Radius
import com.r1control.app.domain.model.ha.HaDashboard
import com.r1control.app.domain.model.ha.HaTab
import com.r1control.app.domain.model.ha.HaTabs
import com.r1control.app.domain.model.ha.TabId
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.icons.MdiIcons
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens
import androidx.compose.runtime.CompositionLocalProvider
import org.jetbrains.compose.ui.tooling.preview.Preview

/** What the tabs along the top can do: select one now, and in the edit mode add, rename, re-icon, delete and move. */
class HaTabActions(
    val onSelect: (TabId) -> Unit,
    val onAdd: (name: String, icon: String) -> Unit,
    val onRename: (TabId, String) -> Unit,
    val onSetIcon: (TabId, String) -> Unit,
    val onDelete: (TabId) -> Unit,
    val onMove: (from: Int, to: Int) -> Unit,
) {
    companion object {
        val None = HaTabActions({}, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, { _, _ -> })
    }
}

private val ChipHeight = 36.dp
private val PillShape = RoundedCornerShape(ChipHeight / 2)
private const val DEFAULT_TAB_ICON = "view-grid"

/** The question the tab bar has open in the edit mode. */
private sealed interface TabAsk {
    data object None : TabAsk
    data class Edit(val id: TabId, val name: String, val icon: String) : TabAsk
    data class Delete(val id: TabId) : TabAsk
}

/**
 * The tabs along the top, each a chip of the tab's icon and name. The open tab wears a filled accent pill
 * that slides to it when another is picked: the pill is one node behind the chips, placed at the open
 * chip's measured position, and its offset and width are animated. The row scrolls when the chips overflow.
 *
 * In the edit mode a chip carries an X to delete (never the last one), tapping a chip opens its name and
 * icon editor, and a trailing plus adds a tab. A delete is confirmed first.
 */
@Composable
fun HaTabBar(tabs: HaTabs, editing: Boolean, actions: HaTabActions, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val density = LocalDensity.current
    val haptics = LocalHaptics.current
    val scroll = rememberScrollState()
    val selectedId = tabs.current?.id?.value
    // Each chip's left offset and width in px, within the scrolled content, for the pill to sit on.
    val bounds = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
    var ask by remember { mutableStateOf<TabAsk>(TabAsk.None) }
    // A just-added tab is the open one; open its editor so a name and icon can be given at once.
    var editAdded by remember { mutableStateOf(false) }
    LaunchedEffect(tabs.tabs.size) {
        if (editAdded) {
            tabs.current?.let { ask = TabAsk.Edit(it.id, it.name, it.icon) }
            editAdded = false
        }
    }
    // A long press lifts a chip; dragging it past a neighbour's half reorders the tabs. The spacing between
    // chips is part of the step a chip crosses to swap, so the pitch is the neighbour's width plus the gap.
    val drag = rememberListDrag(tabs.tabs)
    val spacingPx = with(density) { Space.Small.toPx() }
    val shown = drag.order ?: tabs.tabs
    Box(modifier.horizontalScroll(scroll, enabled = !drag.holding).padding(horizontal = Space.Gutter, vertical = Space.Small)) {
        bounds[selectedId]?.let { (x, w) ->
            val pillX by animateDpAsState(with(density) { x.toDp() })
            val pillW by animateDpAsState(with(density) { w.toDp() })
            Box(Modifier.offset(x = pillX).width(pillW).height(ChipHeight).background(Tokens.Accent, PillShape))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
            shown.forEachIndexed { i, tab ->
                key(tab.id.value) {
                    val held = drag.holding && drag.heldIndex == i
                    val index by rememberUpdatedState(i)
                    val moves = if (editing) listOfNotNull(
                        if (i > 0) CustomAccessibilityAction(strings.haTabMoveLeft) { actions.onMove(i, i - 1); true } else null,
                        if (i < shown.size - 1) CustomAccessibilityAction(strings.haTabMoveRight) { actions.onMove(i, i + 1); true } else null,
                    ) else emptyList()
                    val dragMod = if (editing) {
                        Modifier.pointerInput(tab.id.value) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { haptics.play(Haptic.Lift); drag.start(index, tabs.tabs) },
                                onDragEnd = { haptics.play(Haptic.Knock); drag.end()?.let { (from, to) -> actions.onMove(from, to) } },
                                onDragCancel = { drag.cancel() },
                            ) { change, amount ->
                                change.consume()
                                val moved = drag.dragBy(amount.x) { at ->
                                    val id = (drag.order ?: tabs.tabs).getOrNull(at)?.id?.value
                                    (id?.let { bounds[it]?.second } ?: 0f) + spacingPx
                                }
                                if (moved) haptics.play(Haptic.Tick)
                            }
                        }
                    } else {
                        Modifier
                    }
                    TabChip(
                        tab = tab,
                        selected = tab.id.value == selectedId,
                        deletable = editing && tabs.tabs.size > 1,
                        onClick = { if (editing) ask = TabAsk.Edit(tab.id, tab.name, tab.icon) else actions.onSelect(tab.id) },
                        onDelete = { ask = TabAsk.Delete(tab.id) },
                        onBounds = { x, w -> bounds[tab.id.value] = x to w },
                        moveActions = moves,
                        modifier = Modifier
                            .zIndex(if (held) 1f else 0f)
                            .graphicsLayer { translationX = if (drag.holding && drag.heldIndex == index) drag.offsetPx else 0f }
                            .then(dragMod),
                    )
                }
            }
            if (editing) AddTabChip { actions.onAdd("", DEFAULT_TAB_ICON); editAdded = true }
        }
    }
    when (val a = ask) {
        TabAsk.None -> Unit
        is TabAsk.Edit -> TabEditorDialog(
            initialName = a.name,
            initialIcon = a.icon,
            onDone = { name, icon -> actions.onRename(a.id, name); actions.onSetIcon(a.id, icon); ask = TabAsk.None },
            onCancel = { ask = TabAsk.None },
        )
        is TabAsk.Delete -> ConfirmDialog(
            strings.haTabDeleteBody, strings.delete, strings.cancel,
            onConfirm = { actions.onDelete(a.id); ask = TabAsk.None }, onCancel = { ask = TabAsk.None },
        )
    }
}

@Composable
private fun TabChip(
    tab: HaTab,
    selected: Boolean,
    deletable: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onBounds: (Float, Float) -> Unit,
    moveActions: List<CustomAccessibilityAction> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    // Dark content reads on the pill; muted off it. Both ends animate so the slide and the colour change move together.
    val content by animateColorAsState(if (selected) Tokens.Ground else Tokens.OnGroundMuted)
    Row(
        modifier
            .height(ChipHeight)
            .onGloballyPositioned { onBounds(it.positionInParent().x, it.size.width.toFloat()) }
            .clip(PillShape)
            .tappable(role = Role.Tab, selected = selected, onClick = onClick)
            .then(if (moveActions.isNotEmpty()) Modifier.semantics { customActions = moveActions } else Modifier)
            .padding(start = Space.Medium, end = if (deletable) Space.Small else Space.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Small),
    ) {
        MdiIcons.byName(tab.icon)?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp), tint = content) }
        Text(
            tab.name.ifBlank { strings.haTabUntitled },
            color = content,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
        if (deletable) {
            Icon(
                MdiIcons.byName("close") ?: return@Row,
                contentDescription = strings.delete,
                tint = content,
                modifier = Modifier
                    .clip(RoundedCornerShape(ChipHeight / 2))
                    .tappable(role = Role.Button, onClick = onDelete)
                    .size(18.dp),
            )
        }
    }
}

@Composable
private fun AddTabChip(onClick: () -> Unit) {
    val strings = LocalStrings.current
    Row(
        Modifier
            .height(ChipHeight)
            .clip(PillShape)
            .background(Tokens.Card, PillShape)
            .tappable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.Small),
    ) {
        MdiIcons.byName("plus")?.let { Icon(it, contentDescription = strings.haTabAdd, modifier = Modifier.size(18.dp), tint = Tokens.OnGroundMuted) }
        Text(strings.haTabAdd, color = Tokens.OnGroundMuted, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** A tab's name and icon, chosen together: a field, then a grid of the icon set, done writes both. */
@Composable
private fun TabEditorDialog(initialName: String, initialIcon: String, onDone: (String, String) -> Unit, onCancel: () -> Unit) {
    val strings = LocalStrings.current
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon.removePrefix("mdi:")) }
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).clip(RoundedCornerShape(Radius.Card)).background(Tokens.CardRaised).padding(Space.XLarge),
            verticalArrangement = Arrangement.spacedBy(Space.Large),
        ) {
            Text(strings.haTabRename, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            R1TextField(name, { name = it }, strings.haTabUntitled)
            Text(strings.haTabChooseIcon, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyVerticalGrid(columns = GridCells.Adaptive(48.dp), modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(Space.Small), horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                items(MdiIcons.names, key = { it }) { name ->
                    val chosen = name == icon
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(Radius.Row))
                            .background(if (chosen) Tokens.Accent else Tokens.Card)
                            .tappable(role = Role.Button, selected = chosen) { icon = name },
                        contentAlignment = Alignment.Center,
                    ) {
                        MdiIcons.byName(name)?.let { Icon(it, contentDescription = name, modifier = Modifier.size(22.dp), tint = if (chosen) Tokens.Ground else Tokens.OnGround) }
                    }
                }
            }
            TonalButton(strings.save, { onDone(name.trim(), "mdi:$icon") }, Modifier.fillMaxWidth())
        }
    }
}

// ---- previews ----

private fun sampleTabs(selected: String) = HaTabs(
    listOf(
        HaTab(TabId("tab0"), "Favorites", "mdi:heart", HaDashboard.Empty),
        HaTab(TabId("tab1"), "All devices", "mdi:view-grid", HaDashboard.Empty),
        HaTab(TabId("tab2"), "Lights", "mdi:lightbulb", HaDashboard.Empty),
        HaTab(TabId("tab3"), "Climate", "mdi:thermostat", HaDashboard.Empty),
    ),
    TabId(selected),
)

@Preview
@Composable
private fun TabBarFirstSelected() {
    R1Theme { HaTabBar(sampleTabs("tab0"), editing = false, HaTabActions.None) }
}

@Preview
@Composable
private fun TabBarMiddleSelected() {
    R1Theme { HaTabBar(sampleTabs("tab2"), editing = false, HaTabActions.None) }
}

@Preview
@Composable
private fun TabBarEditing() {
    R1Theme { HaTabBar(sampleTabs("tab1"), editing = true, HaTabActions.None) }
}

@Preview
@Composable
private fun TabBarVietnamese() {
    R1Theme {
        CompositionLocalProvider(LocalStrings provides VietnameseStrings) {
            HaTabBar(
                HaTabs(
                    listOf(
                        HaTab(TabId("tab0"), "Yêu thích", "mdi:heart", HaDashboard.Empty),
                        HaTab(TabId("tab1"), "", "mdi:view-grid", HaDashboard.Empty),
                    ),
                    TabId("tab0"),
                ),
                editing = false,
                HaTabActions.None,
            )
        }
    }
}
