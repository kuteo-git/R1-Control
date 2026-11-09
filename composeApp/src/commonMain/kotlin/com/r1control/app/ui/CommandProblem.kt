package com.r1control.app.ui

import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.usecase.CommandOutcome
import com.r1control.app.domain.usecase.SpeakerFeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The failure of the last command a screen sent, as its banner. It is shown for
 * at least [minMillis], so a refusal can be read, and then until the speaker
 * has answered a reading after it: "could not reach the speaker" must not stay
 * up while the card underneath updates. A later command replaces it.
 */
class CommandProblem(
    private val feed: SpeakerFeed,
    private val scope: CoroutineScope,
    private val minMillis: Long = MIN_SHOW_MILLIS,
) {
    private val state = MutableStateFlow<Problem>(Problem.None)
    private var clearing: Job? = null

    val flow: StateFlow<Problem> = state.asStateFlow()

    fun report(outcome: CommandOutcome) = when (outcome) {
        CommandOutcome.Done -> succeeded()
        is CommandOutcome.Failed -> failed(outcome.error)
    }

    fun succeeded() {
        clearing?.cancel()
        state.value = Problem.None
    }

    fun failed(error: SpeakerException) {
        clearing?.cancel()
        state.value = Problem.Failed(error)
        val answersAtFailure = feed.answers.value
        clearing = scope.launch {
            delay(minMillis)
            feed.answers.first { it > answersAtFailure }
            state.value = Problem.None
        }
    }

    private companion object {
        const val MIN_SHOW_MILLIS = 4_000L
    }
}
