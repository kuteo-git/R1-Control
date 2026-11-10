package com.r1control.app.domain.usecase

import com.r1control.app.domain.model.SpeakerSnapshot
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.repository.SpeakerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * What the screens know about the speaker right now. A failed poll after a good
 * one keeps the last snapshot ([Stale]) so a dropped packet does not blank the card.
 */
sealed interface SpeakerView {
    data object Connecting : SpeakerView
    data class Online(val snapshot: SpeakerSnapshot) : SpeakerView
    data class Stale(val snapshot: SpeakerSnapshot, val error: SpeakerException) : SpeakerView
    data class Offline(val error: SpeakerException) : SpeakerView
}

/** The last snapshot the speaker answered with, or none while it has not answered. */
fun SpeakerView.snapshotOrNull(): SpeakerSnapshot? = when (this) {
    is SpeakerView.Online -> snapshot
    is SpeakerView.Stale -> snapshot
    SpeakerView.Connecting, is SpeakerView.Offline -> null
}

fun SpeakerView.after(result: PollResult): SpeakerView = when (result) {
    is PollResult.Fresh -> SpeakerView.Online(result.snapshot)
    is PollResult.Failed -> when (this) {
        is SpeakerView.Online -> SpeakerView.Stale(snapshot, result.error)
        is SpeakerView.Stale -> SpeakerView.Stale(snapshot, result.error)
        is SpeakerView.Connecting, is SpeakerView.Offline -> SpeakerView.Offline(result.error)
    }
}

/**
 * The one shared reading of the speaker. Polls while somebody collects [view]
 * and for [STOP_DELAY_MS] after the last one leaves, so switching tabs does not
 * restart the loop. [refresh] reads once now, for after a command: the poll may
 * be seconds away.
 *
 * Readings are applied in the order they were started, not the order they
 * land: a slow poll that left before a command must not put the old card back
 * over the refresh that followed the command.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SpeakerFeed(
    private val speaker: SpeakerRepository,
    private val scope: CoroutineScope,
    /** Told of every fresh snapshot as it is applied: what the app warms from the speaker's own answer. */
    private val onFresh: (SpeakerSnapshot) -> Unit = {},
) {
    private val state = MutableStateFlow<SpeakerView>(SpeakerView.Connecting)
    private val answerCount = MutableStateFlow(0)

    // Only touched from the feed's scope, which runs on one thread (the main one in the app).
    private var started = 0L
    private var applied = 0L

    val view: StateFlow<SpeakerView> = state.asStateFlow()

    /** How many readings the speaker has answered; a failure can wait for the next answer. */
    val answers: StateFlow<Int> = answerCount.asStateFlow()

    init {
        scope.launch {
            state.subscriptionCount
                .map { it > 0 }
                .distinctUntilChanged()
                .debounce { watched -> if (watched) 0L else STOP_DELAY_MS }
                // A tab switch is false then true inside the delay; debounce keeps the true, which is no change.
                .distinctUntilChanged()
                .collectLatest { watched ->
                    if (watched) SpeakerPoller { read(applyFailure = true) }.snapshots().collect {}
                }
        }
    }

    /** Reads now. A failure is left to the poll, so one lost refresh does not flag a working speaker. */
    fun refresh() {
        scope.launch { read(applyFailure = false) }
    }

    /**
     * Forgets the reading, for a new address: readings already on their way
     * belong to the old speaker and are dropped, and the new one is read now.
     */
    fun restart() {
        applied = started
        state.value = SpeakerView.Connecting
        scope.launch { read(applyFailure = true) }
    }

    private suspend fun read(applyFailure: Boolean): PollResult {
        val ticket = ++started
        val result = speaker.read()
        val applies = ticket > applied && (applyFailure || result is PollResult.Fresh)
        if (applies) {
            applied = ticket
            state.value = state.value.after(result)
            if (result is PollResult.Fresh) {
                answerCount.value++
                onFresh(result.snapshot)
            }
        }
        return result
    }

    private companion object {
        const val STOP_DELAY_MS = 3_000L
    }
}
