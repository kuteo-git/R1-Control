package com.r1control.app.ui.chat

import com.r1control.app.domain.model.ChatMessage
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.usecase.SendChat
import com.r1control.app.domain.usecase.SendOutcome
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.domain.usecase.SpeakerView
import com.r1control.app.ui.CommandProblem
import com.r1control.app.ui.Problem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** What the chat area shows: a spinner, the failure in its place, or the bubbles. */
sealed interface ChatLog {
    data object Asking : ChatLog
    /** No reading has come back yet. */
    data class Unreachable(val error: SpeakerException) : ChatLog
    /** Oldest first. */
    data class Lines(val messages: List<ChatMessage>) : ChatLog
}

/**
 * [problem] is the reading's, [sendProblem] the last send's: a send that failed
 * is said at the field it came back to, whatever the readings are doing.
 */
data class ChatState(val log: ChatLog, val problem: Problem, val sendProblem: Problem) {
    val messages: List<ChatMessage> get() = (log as? ChatLog.Lines)?.messages ?: emptyList()

    /** A speaker that has never answered has nobody to send to. */
    val canSend: Boolean get() = log !is ChatLog.Unreachable
}

class ChatViewModel(
    private val feed: SpeakerFeed,
    private val sendChat: SendChat,
    private val scope: CoroutineScope,
) {
    private val draftFlow = MutableStateFlow("")
    private val sendProblem = CommandProblem(feed, scope)

    /** The text field's own flow, so typing never waits for the combined state. */
    val draft: StateFlow<String> = draftFlow.asStateFlow()

    /**
     * Cold, so a screen that opens starts from [current], not from a value kept
     * since the last time somebody looked: a shared state would show its first
     * "asking" for a frame on the first visit even though the feed already knows.
     */
    val state: Flow<ChatState> = combine(feed.view, sendProblem.flow, ::stateOf)

    fun current(): ChatState = stateOf(feed.view.value, sendProblem.flow.value)

    fun onDraft(text: String) {
        draftFlow.value = text
    }

    /** The field is cleared at once; a refused message puts the text back. */
    fun send() {
        val text = draftFlow.value
        if (text.isBlank()) return
        draftFlow.value = ""
        scope.launch {
            when (val outcome = sendChat(text)) {
                SendOutcome.Blank -> Unit
                is SendOutcome.Sent -> {
                    sendProblem.succeeded()
                    feed.refresh()
                }
                is SendOutcome.Failed -> {
                    sendProblem.failed(outcome.error)
                    if (draftFlow.value.isEmpty()) draftFlow.value = text
                }
            }
        }
    }

    /** Reads the speaker now instead of waiting for the next poll. */
    fun retry() = feed.refresh()
}

private fun stateOf(view: SpeakerView, send: Problem): ChatState =
    when (view) {
        SpeakerView.Connecting -> ChatState(ChatLog.Asking, Problem.None, send)
        is SpeakerView.Offline -> ChatState(ChatLog.Unreachable(view.error), Problem.None, Problem.None)
        is SpeakerView.Online -> ChatState(ChatLog.Lines(view.snapshot.chat), Problem.None, send)
        is SpeakerView.Stale -> ChatState(ChatLog.Lines(view.snapshot.chat), Problem.Failed(view.error), send)
    }
