package com.r1control.app.ui.ha

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.r1control.app.ui.components.tappable
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

/**
 * The tabs along the top, each a chip of the tab's icon and name. The open tab wears a filled accent pill
 * that slides to it when another is picked: the pill is one node behind the chips, placed at the open
 * chip's measured position, and its offset and width are animated. The row scrolls when the chips overflow.
 */
@Composable
fun HaTabBar(tabs: HaTabs, onSelect: (TabId) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val selectedId = tabs.current?.id?.value
    // Each chip's left offset and width in px, within the scrolled content, for the pill to sit on.
    val bounds = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
    Box(modifier.horizontalScroll(scroll).padding(horizontal = Space.Gutter, vertical = Space.Small)) {
        bounds[selectedId]?.let { (x, w) ->
            val pillX by animateDpAsState(with(density) { x.toDp() })
            val pillW by animateDpAsState(with(density) { w.toDp() })
            Box(Modifier.offset(x = pillX).width(pillW).height(ChipHeight).background(Tokens.Accent, PillShape))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
            tabs.tabs.forEach { tab ->
                TabChip(
                    tab = tab,
                    selected = tab.id.value == selectedId,
                    onClick = { onSelect(tab.id) },
                    onBounds = { x, w -> bounds[tab.id.value] = x to w },
                )
            }
        }
    }
}

@Composable
private fun TabChip(tab: HaTab, selected: Boolean, onClick: () -> Unit, onBounds: (Float, Float) -> Unit) {
    val strings = LocalStrings.current
    // Dark content reads on the pill; muted off it. Both ends animate so the slide and the colour change move together.
    val content by animateColorAsState(if (selected) Tokens.Ground else Tokens.OnGroundMuted)
    Row(
        Modifier
            .height(ChipHeight)
            .onGloballyPositioned { onBounds(it.positionInParent().x, it.size.width.toFloat()) }
            .tappable(role = Role.Tab, selected = selected, onClick = onClick)
            .padding(horizontal = Space.Medium),
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
    R1Theme { HaTabBar(sampleTabs("tab0"), {}) }
}

@Preview
@Composable
private fun TabBarMiddleSelected() {
    R1Theme { HaTabBar(sampleTabs("tab2"), {}) }
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
                {},
            )
        }
    }
}
