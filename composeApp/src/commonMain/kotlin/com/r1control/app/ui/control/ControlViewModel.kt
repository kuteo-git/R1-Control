package com.r1control.app.ui.control

import com.r1control.app.domain.model.NowPlaying
import com.r1control.app.domain.model.SpeakerSnapshot
import com.r1control.app.domain.model.VoiceControls
import com.r1control.app.domain.model.canEndSession
import com.r1control.app.domain.model.VolumeScale
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.repository.SpeakerRepository
import com.r1control.app.domain.usecase.CommandOutcome
import com.r1control.app.domain.usecase.MusicStore
import com.r1control.app.domain.usecase.SpeakerFeed
import com.r1control.app.domain.usecase.SpeakerView
import com.r1control.app.domain.usecase.Transport
import com.r1control.app.domain.usecase.VolumeSetter
import com.r1control.app.domain.usecase.command
import com.r1control.app.ui.CommandProblem
import com.r1control.app.ui.Held
import com.r1control.app.ui.HeldValue
import com.r1control.app.ui.Problem
import com.r1control.app.ui.or
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed interface CardState {
    /** The first reading is on its way. */
    data object Connecting : CardState
    /** No reading has ever come back: the card says so and offers a retry, not an empty player. */
    data class Unreachable(val error: SpeakerException) : CardState
    data class Showing(val nowPlaying: NowPlaying) : CardState
}

/** [enabled] is false while the level is not known; the pill then shows no number. */
data class VolumeState(val pct: Int, val scale: VolumeScale, val enabled: Boolean)

/** "End the conversation" under the switch. */
enum class EndControl {
    /** No session to end, music that a sleep would stop, or a reading too old to tell: not drawn. */
    Hidden,

    Offered,

    /** Pressed, and the R1 has not yet shown the session closed: drawn as "Ending…", not pressable. */
    Ending,
}

/** The listening controls under the volume pill. */
sealed interface VoiceRow {
    /** An R1 build without the listening routes (`wake_enabled` absent), or no reading yet: nothing is drawn. */
    data object Hidden : VoiceRow

    data class Shown(
        /** The switch: the finger's value while held, else the R1's. */
        val wakeWordOn: Boolean,
        val end: EndControl,
    ) : VoiceRow {
        /** A press would send `/api/voice/sleep`. */
        val canEndSession: Boolean get() = end == EndControl.Offered
    }
}

data class ControlState(
    val card: CardState,
    val volume: VolumeState,
    val problem: Problem,
    /** `MusicStore.canNext`: the same value the mini player draws Next from. */
    val canNext: Boolean,
    val voice: VoiceRow,
) {
    companion object {
        val Initial = ControlState(
            CardState.Connecting, VolumeState(0, VolumeScale(0), enabled = false), Problem.None,
            canNext = false, voice = VoiceRow.Hidden,
        )
    }
}

/**
 * The listening controls over a reading and the finger's holds. Pure, so the
 * rules are one tested function:
 * - controls the R1 does not have draw nothing;
 * - the switch shows the held value until the R1 has caught up;
 * - End is offered only when [canEndSession] over a [fresh] reading: a sleep
 *   stops music, and a stale reading cannot say that none has started since;
 * - a pressed End reads "Ending…" while its hold lasts and the reading still
 *   shows the session open (the R1 answers before it has ended it), and goes
 *   as soon as a reading shows it closed.
 */
fun voiceRowOf(snapshot: SpeakerSnapshot, fresh: Boolean, wake: Held<Boolean>, ending: Held<Boolean>): VoiceRow =
    when (val controls = snapshot.voiceControls) {
        VoiceControls.Absent -> VoiceRow.Hidden
        is VoiceControls.Present -> VoiceRow.Shown(
            wakeWordOn = wake.or(controls.wakeWordOn),
            end = when {
                !canEndSession(snapshot) -> EndControl.Hidden
                ending is Held.Holding -> EndControl.Ending
                !fresh -> EndControl.Hidden
                else -> EndControl.Offered
            },
        )
    }

private data class Holds(val volume: Held<Int>, val seek: Held<Int>, val wake: Held<Boolean>, val ending: Held<Boolean>)

/**
 * The Control tab. The card is [nowPlayingOf] over the shared reading; the
 * volume and the seek bar are the finger's for [holdMillis] after release,
 * because the R1's next answer is older than the finger.
 */
class ControlViewModel(
    private val feed: SpeakerFeed,
    private val speaker: SpeakerRepository,
    private val store: MusicStore,
    private val scope: CoroutineScope,
    holdMillis: Long = HOLD_MILLIS,
    /** How long a pressed "end the conversation" stays away waiting for the R1 to go idle; its stop chime takes a second or two. */
    endHoldMillis: Long = END_HOLD_MILLIS,
) {
    private val transport = Transport(speaker)
    private val commandProblem = CommandProblem(feed, scope)
    private val volumeHold = HeldValue<Int>(scope, holdMillis)
    private val seekHold = HeldValue<Int>(scope, holdMillis)
    private val wakeHold = HeldValue<Boolean>(scope, holdMillis)
    private val endHold = HeldValue<Boolean>(scope, endHoldMillis)
    private val volumeSetter = VolumeSetter(scope, speaker) { commandProblem.failed(it) }

    private val holds = combine(volumeHold.flow, seekHold.flow, wakeHold.flow, endHold.flow, ::Holds)

    val state: StateFlow<ControlState> = combine(
        feed.view, store.canNext, holds, commandProblem.flow,
    ) { view, canNext, holds, command ->
        when (view) {
            SpeakerView.Connecting -> ControlState.Initial.copy(problem = command)
            is SpeakerView.Offline -> ControlState.Initial.copy(card = CardState.Unreachable(view.error))
            is SpeakerView.Online -> build(view, canNext, holds, command)
            is SpeakerView.Stale -> build(view, canNext, holds, Problem.Failed(view.error))
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), ControlState.Initial)

    private fun build(
        view: SpeakerView,
        canNext: Boolean,
        holds: Holds,
        problem: Problem,
    ): ControlState {
        val snapshot = when (view) {
            is SpeakerView.Online -> view.snapshot
            is SpeakerView.Stale -> view.snapshot
            else -> return ControlState.Initial
        }
        val fresh = view is SpeakerView.Online
        var nowPlaying = snapshot.nowPlaying
        if (nowPlaying is NowPlaying.Music) {
            nowPlaying = nowPlaying.copy(positionS = holds.seek.or(nowPlaying.positionS))
        }
        return ControlState(
            card = CardState.Showing(nowPlaying),
            volume = VolumeState(snapshot.volume.scale.snap(holds.volume.or(snapshot.volume.pct)), snapshot.volume.scale, enabled = true),
            problem = problem,
            canNext = canNext,
            voice = voiceRowOf(snapshot, fresh, holds.wake, holds.ending),
        )
    }

    /** A finger is on the volume pill at [pct] percent. */
    fun onVolumeDrag(pct: Int) {
        val scale = state.value.volume.scale
        volumeHold.hold(scale.snap(pct))
        volumeSetter.set(pct, scale)
    }

    fun onVolumeRelease() {
        volumeSetter.release()
        volumeHold.releaseSoon()
    }

    fun onSeekDrag(positionS: Int) {
        seekHold.hold(positionS)
    }

    /** Sends the position once, on release. */
    fun onSeekRelease() {
        val held = seekHold.flow.value
        seekHold.releaseSoon()
        if (held is Held.Holding) run { transport.seek(held.value) }
    }

    fun onPlayPause() {
        val card = state.value.card
        if (card is CardState.Showing && card.nowPlaying is NowPlaying.Music) {
            run { transport.toggle(card.nowPlaying.phase) }
        }
    }

    /** Drawn only when the store can; the store decides again at the press. */
    fun onNext() = run { store.next() }

    /** The switch: the finger's value is shown until the R1 has taken it, and goes back at once if it refused. */
    fun onWakeWord(enabled: Boolean) {
        wakeHold.hold(enabled)
        scope.launch {
            val outcome = command { speaker.setWakeWord(enabled) }
            wakeHold.answered(enabled, taken = outcome is CommandOutcome.Done)
            commandProblem.report(outcome)
            feed.refresh()
        }
    }

    /**
     * `POST /api/voice/sleep`, guarded by the rule that draws the button: a
     * press over a reading that may be out of date must not stop a song. The
     * row says "Ending…" until the R1 has ended the session; a refusal brings
     * the button back at once, with the banner.
     */
    fun onEndSession() {
        val shown = state.value.voice as? VoiceRow.Shown ?: return
        if (!shown.canEndSession) return
        endHold.hold(true)
        scope.launch {
            val outcome = command { speaker.endVoiceSession() }
            endHold.answered(true, taken = outcome is CommandOutcome.Done)
            commandProblem.report(outcome)
            feed.refresh()
        }
    }

    fun onStop() = run { transport.stop() }

    /** The unreachable card's Try again: read the speaker now instead of at the next poll. */
    fun onRetry() = feed.refresh()

    private fun run(block: suspend () -> CommandOutcome) {
        scope.launch {
            commandProblem.report(block())
            feed.refresh()
        }
    }

    private companion object {
        const val HOLD_MILLIS = 1_500L
        const val END_HOLD_MILLIS = 4_000L
    }
}

/**
 * The level after a finger moved [dxPx] along a pill [widthPx] wide, from the
 * level [startPct] it had when the finger went down: the whole width is 0 to
 * 100. Relative, as Google Home's pill is, so where the finger lands sets nothing.
 */
fun volumeAfterDrag(startPct: Int, dxPx: Float, widthPx: Float): Int {
    if (widthPx <= 0f) return startPct.coerceIn(0, 100)
    return (startPct + dxPx / widthPx * 100f).roundToInt().coerceIn(0, 100)
}
