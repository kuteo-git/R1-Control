package com.r1control.app.ui

import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.usecase.CommandOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** A thing that is loading, loaded or failed. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Loaded<T>(val value: T) : Loadable<T>
    data class Failed(val error: SpeakerException) : Loadable<Nothing>
}

/** A quiet problem shown as a banner while the rest of the screen stays usable. */
sealed interface Problem {
    data object None : Problem
    data class Failed(val error: SpeakerException) : Problem
}

fun CommandOutcome.asProblem(): Problem = when (this) {
    CommandOutcome.Done -> Problem.None
    is CommandOutcome.Failed -> Problem.Failed(error)
}

/** A value the finger owns, or the polled one when it does not. */
sealed interface Held<out T> {
    data object Free : Held<Nothing>
    data class Holding<T>(val value: T) : Held<T>
}

/**
 * A value that follows a finger and then keeps overriding the polled one for
 * [holdMillis] after release, so the R1's older answer does not snap the control
 * back under the finger. A new [hold] cancels a pending release.
 */
class HeldValue<T>(private val scope: CoroutineScope, private val holdMillis: Long) {
    private val state = MutableStateFlow<Held<T>>(Held.Free)
    private var release: Job? = null

    val flow: StateFlow<Held<T>> get() = state

    fun hold(value: T) {
        release?.cancel()
        state.value = Held.Holding(value)
    }

    fun releaseSoon() {
        release?.cancel()
        release = scope.launch {
            delay(holdMillis)
            state.value = Held.Free
        }
    }

    /**
     * The speaker answered a write of [value]. Taken, it is let go after the
     * hold, for the reading already on its way; not taken, at once, so the
     * control shows the speaker's own value again. A newer value is left alone:
     * its own write answers for it.
     */
    fun answered(value: T, taken: Boolean) {
        if (state.value != Held.Holding(value)) return
        if (taken) {
            releaseSoon()
        } else {
            release?.cancel()
            state.value = Held.Free
        }
    }
}

fun <T> Held<T>.or(polled: T): T = when (this) {
    Held.Free -> polled
    is Held.Holding -> value
}

/** What a command's failure becomes once the speaker answers a reading: a refusal still stands, the rest is past. */
fun Problem.afterGoodRead(): Problem =
    if (this is Problem.Failed && error is SpeakerException.Refused) this else Problem.None
