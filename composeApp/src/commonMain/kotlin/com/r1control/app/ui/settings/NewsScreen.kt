package com.r1control.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.r1control.app.ui.components.Haptic
import com.r1control.app.ui.components.LocalHaptics
import com.r1control.app.ui.components.tappable
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.NewsCategory
import com.r1control.app.domain.model.NewsSettings
import com.r1control.app.domain.model.NewsTime
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.ui.Loadable
import com.r1control.app.ui.Problem
import com.r1control.app.ui.components.FailedBox
import com.r1control.app.ui.components.ListDrag
import com.r1control.app.ui.components.LoadingBox
import com.r1control.app.ui.components.editableItems
import com.r1control.app.ui.components.rememberListDrag
import com.r1control.app.ui.components.ProblemBanner
import com.r1control.app.ui.components.R1Icon
import com.r1control.app.ui.components.R1Switch
import com.r1control.app.ui.components.SectionLabel
import com.r1control.app.ui.components.SwitchRow
import com.r1control.app.ui.components.TonalButton
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.i18n.describe
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Radius
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NewsActions(
    val onEnabled: (Boolean) -> Unit = {},
    val onHour: (Int) -> Unit = {},
    val onMinute: (Int) -> Unit = {},
    val onVoice: (String) -> Unit = {},
    val onCategory: (String, Boolean) -> Unit = { _, _ -> },
    val onMoved: (Int, Int) -> Unit = { _, _ -> },
    val onTest: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

@Composable
fun NewsScreen(viewModel: NewsViewModel) {
    val state by viewModel.state.collectAsState()
    NewsContent(
        state,
        NewsActions(
            viewModel::onEnabled, viewModel::onHour, viewModel::onMinute, viewModel::onVoice,
            viewModel::onCategory, viewModel::onMoved, viewModel::test, viewModel::load,
        ),
    )
}

/**
 * One lazy list, so the categories use the same drag as the songs. Off, the
 * bulletin's settings are dimmed and out of reach, as a disabled control is.
 */
@Composable
fun NewsContent(state: NewsState, actions: NewsActions) {
    val strings = LocalStrings.current
    val list = rememberLazyListState()
    val loaded = (state.draft as? Loadable.Loaded)?.value
    val drag = rememberListDrag(loaded?.news?.categories.orEmpty())
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = Space.XLarge)) {
        val problem = state.problem
        if (problem is Problem.Failed) {
            item(key = "problem") {
                ProblemBanner(strings.describe(problem.error), Modifier.padding(horizontal = Space.Gutter, vertical = Space.Small))
            }
        }
        when (val draft = state.draft) {
            Loadable.Loading -> item(key = "loading") { LoadingBox() }
            is Loadable.Failed -> item(key = "failed") { FailedBox(strings.describe(draft.error), strings.tryAgain, actions.onRetry) }
            is Loadable.Loaded -> loadedItems(draft.value, state.action, drag, list, strings, actions)
        }
    }
}

private fun LazyListScope.loadedItems(
    draft: NewsDraft,
    action: NewsAction,
    drag: ListDrag<NewsCategory>,
    list: LazyListState,
    strings: Strings,
    actions: NewsActions,
) {
    val news = draft.news
    val on = news.enabled
    val dim = Modifier.alpha(if (on) 1f else DIMMED)
    item(key = "enabled") { SwitchRow(strings.newsEnabled, news.enabled, actions.onEnabled) }
    item(key = "time") {
        Column(dim.padding(horizontal = Space.Gutter)) {
            SectionLabel(strings.newsTime)
            TimeSteppers(NewsTime.parse(news.time), actions.onHour, actions.onMinute, enabled = on)
        }
    }
    item(key = "voice") {
        Column(dim.padding(horizontal = Space.Gutter)) {
            SectionLabel(strings.newsVoice)
            VoicePicker(news.voice, draft.voices, actions.onVoice, enabled = on)
        }
    }
    item(key = "categories") { Column(dim.padding(horizontal = Space.Gutter)) { SectionLabel(strings.newsCategories) } }
    editableItems(
        items = news.categories,
        key = { "news:" + it.key },
        drag = drag,
        list = list,
        enabled = on,
        onMove = actions.onMoved,
        onRemove = null,
        canRemove = false,
        handleLabel = { strings.reorderCategory(strings.newsCategory(it.key)) },
        removeLabel = { "" },
        rowModifier = Modifier.padding(horizontal = Space.Small),
    ) { category -> CategoryRow(category, on, actions) }
    item(key = "test") {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.Gutter).padding(top = Space.XLarge),
            verticalArrangement = Arrangement.spacedBy(Space.Small),
        ) {
            TonalButton(strings.newsTest, actions.onTest, Modifier.fillMaxWidth(), enabled = action != NewsAction.Testing)
            Text(
                if (action == NewsAction.Tested) strings.newsTestStarted else strings.newsTestHint,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = if (action == NewsAction.Tested) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimeSteppers(time: NewsTime, onHour: (Int) -> Unit, onMinute: (Int) -> Unit, enabled: Boolean) {
    val strings = LocalStrings.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Row)).background(Tokens.Card).padding(vertical = Space.Small),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stepper(time.hour.toString().padStart(2, '0'), strings.hours, { onHour(1) }, { onHour(-1) }, enabled)
        Text(":", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(horizontal = Space.Medium))
        Stepper(time.minute.toString().padStart(2, '0'), strings.minutes, { onMinute(5) }, { onMinute(-5) }, enabled)
    }
}

@Composable
private fun Stepper(text: String, label: String, onUp: () -> Unit, onDown: () -> Unit, enabled: Boolean) {
    val strings = LocalStrings.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RepeatingTap(R1Icons.ExpandLess, "${strings.increase} ${label.lowercase()}", onUp, enabled)
        Text(text, style = MaterialTheme.typography.displaySmall)
        RepeatingTap(R1Icons.ExpandMore, "${strings.decrease} ${label.lowercase()}", onDown, enabled)
    }
}

/**
 * A stepper arrow that repeats while held, like iOS's own stepper: 06:30 to
 * 21:45 was eighteen separate taps. A tap is still one step.
 */
@Composable
private fun RepeatingTap(icon: ImageVector, description: String, onStep: () -> Unit, enabled: Boolean) {
    val haptics = LocalHaptics.current
    val step by rememberUpdatedState({
        haptics.play(Haptic.Tick)
        onStep()
    })
    val tint = if (enabled) Tokens.OnGround else Tokens.OnGround.copy(alpha = 0.38f)
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
                if (!enabled) disabled()
                onClick { step(); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                coroutineScope {
                    detectTapGestures(
                        onPress = {
                            step()
                            val repeating = launch {
                                delay(REPEAT_AFTER_MILLIS)
                                while (true) {
                                    step()
                                    delay(REPEAT_EVERY_MILLIS)
                                }
                            }
                            tryAwaitRelease()
                            repeating.cancel()
                        },
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        R1Icon(icon, null, tint = tint)
    }
}

private const val REPEAT_AFTER_MILLIS = 450L
private const val REPEAT_EVERY_MILLIS = 120L
private const val DIMMED = 0.45f

@Composable
private fun VoicePicker(current: String, voices: List<String>, onVoice: (String) -> Unit, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Row)).background(Tokens.Card)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).tappable(enabled = enabled, role = Role.DropdownList) { open = !open }.padding(horizontal = Space.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            R1Icon(if (open) R1Icons.ExpandLess else R1Icons.ExpandMore, null, tint = Tokens.OnGroundMuted)
        }
        if (open && enabled) {
            voices.forEach { name ->
                VoiceRow(name, selected = name == current, onClick = { onVoice(name); open = false })
            }
        }
    }
}

@Composable
private fun RowScope.CategoryRow(category: NewsCategory, enabled: Boolean, actions: NewsActions) {
    val strings = LocalStrings.current
    Row(
        Modifier.weight(1f).heightIn(min = 56.dp).alpha(if (enabled) 1f else DIMMED),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            strings.newsCategory(category.key),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (category.enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        R1Switch(category.enabled, { actions.onCategory(category.key, it) }, enabled = enabled)
        Spacer(Modifier.width(Space.Small))
    }
}

@Composable
private fun PreviewHost(strings: Strings, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStrings provides strings) {
        R1Theme { Box(Modifier.background(Tokens.Ground)) { content() } }
    }
}

private val news = NewsSettings(
    enabled = true, time = "09:15", voice = "Thiền Tâm Đức",
    categories = listOf(
        NewsCategory("tech", true), NewsCategory("world", true), NewsCategory("society", false),
        NewsCategory("weather", false), NewsCategory("power", true),
    ),
)
private val draft = NewsDraft(news, listOf("Thiền Tâm Đức", "Lê Yến", "Quốc Tuấn"))

@Preview
@Composable
private fun NewsLoadingPreview() = PreviewHost(EnglishStrings) { NewsContent(NewsState(Loadable.Loading, NewsAction.Idle, Problem.None), NewsActions()) }

@Preview
@Composable
private fun NewsLoadedPreview() =
    PreviewHost(EnglishStrings) { NewsContent(NewsState(Loadable.Loaded(draft), NewsAction.Tested, Problem.None), NewsActions()) }

@Preview
@Composable
private fun NewsOffPreview() = PreviewHost(EnglishStrings) {
    NewsContent(NewsState(Loadable.Loaded(draft.copy(news = news.copy(enabled = false))), NewsAction.Idle, Problem.None), NewsActions())
}

@Preview
@Composable
private fun NewsFailedPreview() = PreviewHost(EnglishStrings) {
    NewsContent(NewsState(Loadable.Failed(SpeakerException.Unreachable(RuntimeException("x"))), NewsAction.Idle, Problem.None), NewsActions())
}

@Preview
@Composable
private fun NewsVietnamesePreview() =
    PreviewHost(VietnameseStrings) { NewsContent(NewsState(Loadable.Loaded(draft), NewsAction.Idle, Problem.None), NewsActions()) }
