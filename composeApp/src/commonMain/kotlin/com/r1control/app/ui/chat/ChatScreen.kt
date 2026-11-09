package com.r1control.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.r1control.app.domain.model.ChatMessage
import com.r1control.app.domain.model.Sender
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.ui.Problem
import com.r1control.app.ui.components.FailedBox
import com.r1control.app.ui.components.IconTap
import com.r1control.app.ui.components.LoadingBox
import com.r1control.app.ui.components.ProblemBanner
import com.r1control.app.ui.components.R1Icon
import com.r1control.app.ui.components.R1TextField
import com.r1control.app.ui.i18n.EnglishStrings
import com.r1control.app.ui.i18n.LocalStrings
import com.r1control.app.ui.i18n.Strings
import com.r1control.app.ui.i18n.VietnameseStrings
import com.r1control.app.ui.i18n.dayOf
import com.r1control.app.ui.i18n.describe
import com.r1control.app.ui.i18n.formatLocalTime
import com.r1control.app.ui.i18n.localDay
import com.r1control.app.ui.icons.R1Icons
import com.r1control.app.ui.theme.R1Theme
import com.r1control.app.ui.theme.Space
import com.r1control.app.ui.theme.Tokens
import kotlin.time.Clock

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.state.collectAsState(initial = viewModel.current())
    val draft by viewModel.draft.collectAsState()
    ChatContent(state, draft, viewModel::onDraft, viewModel::send, viewModel::retry)
}

@Composable
fun ChatContent(
    state: ChatState,
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val focus = LocalFocusManager.current
    val field = remember { FocusRequester() }
    var typing by remember { mutableStateOf(false) }
    // The tab is for writing, so the keyboard is up as it opens.
    LaunchedEffect(Unit) { field.requestFocus() }
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (val log = state.log) {
                ChatLog.Asking -> LoadingBox()
                is ChatLog.Unreachable -> FailedBox(strings.describe(log.error), strings.tryAgain, onRetry)
                is ChatLog.Lines -> if (log.messages.isEmpty()) EmptyChat() else Messages(log.messages)
            }
        }
        // At the field, because with the keyboard up the field is all that is in view.
        val send = state.sendProblem
        val reading = state.problem
        val note = when {
            send is Problem.Failed -> strings.chatNotSent(strings.describe(send.error))
            reading is Problem.Failed -> strings.describe(reading.error)
            else -> ""
        }
        if (note.isNotEmpty()) ProblemBanner(note, Modifier.padding(horizontal = Space.Gutter).padding(top = Space.Small))
        val sendable = state.canSend && draft.isNotBlank()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.Gutter, vertical = Space.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.Small),
        ) {
            R1TextField(
                value = draft,
                onValueChange = onDraft,
                hint = strings.chatHint,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                onDone = if (sendable) onSend else null,
                focusRequester = field,
                onFocusChanged = { typing = it },
            )
            if (typing) {
                IconTap(R1Icons.KeyboardHide, strings.hideKeyboard, { focus.clearFocus() }, tint = Tokens.OnGroundMuted)
            }
            IconTap(
                icon = R1Icons.Send,
                description = strings.send,
                onClick = onSend,
                enabled = sendable,
                tint = MaterialTheme.colorScheme.onPrimary,
                disabledTint = Tokens.OnGroundMuted,
                background = if (sendable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                iconSize = 22.dp,
                box = 52.dp,
            )
        }
    }
}

@Composable
private fun EmptyChat() {
    val strings = LocalStrings.current
    Column(
        Modifier.fillMaxSize().padding(Space.XLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        R1Icon(R1Icons.Chat, null, tint = Tokens.OnGroundMuted, size = 40.dp)
        Text(
            strings.chatEmpty,
            modifier = Modifier.padding(top = Space.Large),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Newest at the bottom, laid out from the bottom up, so a viewport that shrinks under
 * the keyboard keeps the newest lines in sight. Pinned to the newest while the reader
 * is at the bottom; a reader who scrolled up is left alone. A drag closes the keyboard.
 */
@Composable
private fun Messages(messages: List<ChatMessage>) {
    val strings = LocalStrings.current
    val rows = remember(messages) { chatRows(messages, ::localDay).asReversed() }
    val nowMillis = remember(messages) { Clock.System.now().toEpochMilliseconds() }
    val listState = rememberLazyListState()
    var pinned by remember { mutableStateOf(true) }
    val newest = rows.firstOrNull()?.key
    LaunchedEffect(newest) {
        if (pinned) listState.scrollToItem(0)
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to !listState.canScrollBackward }
            .collect { (scrolling, atBottom) -> if (scrolling || atBottom) pinned = atBottom }
    }
    val focus = LocalFocusManager.current
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) focus.clearFocus() }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bubbleMax = maxWidth * 0.8f
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = Space.Gutter, vertical = Space.Small),
            verticalArrangement = Arrangement.spacedBy(Space.Medium, Alignment.Bottom),
        ) {
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is ChatRow.Day -> DayHeading(strings.dayOf(row.atMillis, nowMillis))
                    is ChatRow.Line -> Bubble(row.message, row.showsTime, bubbleMax)
                }
            }
        }
    }
}

@Composable
private fun DayHeading(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(top = Space.Small),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Bubble(message: ChatMessage, showsTime: Boolean, maxWidth: androidx.compose.ui.unit.Dp) {
    val mine = message.sender == Sender.User
    val shape = RoundedCornerShape(
        topStart = 20.dp, topEnd = 20.dp,
        bottomStart = if (mine) 20.dp else 6.dp,
        bottomEnd = if (mine) 6.dp else 20.dp,
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Box(
            Modifier
                .widthIn(max = maxWidth)
                .background(if (mine) Tokens.Accent else Tokens.Card, shape)
                .padding(horizontal = Space.Large, vertical = Space.Medium),
        ) {
            Text(
                message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (mine) Tokens.Ground else Tokens.OnGround,
            )
        }
        if (showsTime) {
            Text(
                formatLocalTime(message.timeMillis),
                modifier = Modifier.padding(horizontal = Space.Small, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- previews ----

@Composable
private fun PreviewHost(strings: Strings, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStrings provides strings) {
        R1Theme { Box(Modifier.background(Tokens.Ground)) { content() } }
    }
}

private val conversation = listOf(
    ChatMessage(Sender.User, "Bật nhạc Thanh Ngân", 1_762_500_000_000),
    ChatMessage(Sender.Assistant, "Được rồi, mình phát bài Thành Tâm Sám Hối của NSƯT Thanh Ngân nhé.", 1_762_500_010_000),
    ChatMessage(Sender.User, "Cảm ơn", 1_762_500_100_000),
)

@Preview
@Composable
private fun ChatLoadingPreview() = PreviewHost(EnglishStrings) {
    ChatContent(ChatState(ChatLog.Asking, Problem.None, Problem.None), "", {}, {}, {})
}

@Preview
@Composable
private fun ChatEmptyPreview() = PreviewHost(EnglishStrings) {
    ChatContent(ChatState(ChatLog.Lines(emptyList()), Problem.None, Problem.None), "", {}, {}, {})
}

@Preview
@Composable
private fun ChatConversationPreview() = PreviewHost(EnglishStrings) {
    ChatContent(ChatState(ChatLog.Lines(conversation), Problem.None, Problem.None), "Hello", {}, {}, {})
}

@Preview
@Composable
private fun ChatFailedPreview() = PreviewHost(EnglishStrings) {
    ChatContent(
        ChatState(ChatLog.Lines(conversation), Problem.Failed(SpeakerException.Unreachable(RuntimeException("x"))), Problem.None),
        "", {}, {}, {},
    )
}

@Preview
@Composable
private fun ChatNotSentPreview() = PreviewHost(VietnameseStrings) {
    ChatContent(
        ChatState(ChatLog.Lines(conversation), Problem.None, Problem.Failed(SpeakerException.Unreachable(RuntimeException("x")))),
        "offline test two", {}, {}, {},
    )
}

@Preview
@Composable
private fun ChatVietnamesePreview() = PreviewHost(VietnameseStrings) {
    ChatContent(ChatState(ChatLog.Lines(conversation), Problem.None, Problem.None), "", {}, {}, {})
}

@Preview
@Composable
private fun ChatUnreachablePreview() = PreviewHost(VietnameseStrings) {
    ChatContent(ChatState(ChatLog.Unreachable(SpeakerException.Unreachable(RuntimeException("x"))), Problem.None, Problem.None), "", {}, {}, {})
}
