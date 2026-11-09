package com.r1control.app.ui.settings

import com.r1control.app.domain.model.AudioSettings
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.repository.SpeakerRepository
import com.r1control.app.domain.usecase.POLL_IDLE_MS
import com.r1control.app.ui.Held
import com.r1control.app.ui.HeldValue
import com.r1control.app.ui.Loadable
import com.r1control.app.ui.Problem
import com.r1control.app.ui.afterGoodRead
import com.r1control.app.ui.or
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

data class AudioState(val audio: Loadable<AudioSettings>, val problem: Problem)

/**
 * Loudness and equaliser, as the R1 has them: read when the page opens and
 * every [pollMillis] while it is open. A slider moves the screen at once and
 * the R1 after the finger pauses ([debounceMillis]); lifting the finger writes
 * straight away, so leaving the page never loses a value.
 *
 * What the finger set is shown over the readings while it is down and for
 * [holdMillis] after the R1 took it, as on the Control tab. A write the R1 did
 * not take lets go at once, so the control shows the R1's value, not the one
 * that failed.
 */
class AudioViewModel(
    private val speaker: SpeakerRepository,
    private val scope: CoroutineScope,
    private val debounceMillis: Long = 400,
    private val pollMillis: Long = POLL_IDLE_MS,
    holdMillis: Long = 1_500,
) {
    private val flow = MutableStateFlow(AudioState(Loadable.Loading, Problem.None))
    private var reading: Loadable<AudioSettings> = Loadable.Loading
    private var readProblem: Problem = Problem.None
    private var writeProblem: Problem = Problem.None
    private val loudness = HeldValue<Int>(scope, holdMillis)
    private val eqEnabled = HeldValue<Boolean>(scope, holdMillis)
    private val gains = HeldValue<List<Int>>(scope, holdMillis)
    private var pendingLoudness: Job? = null
    private var pendingBands: Job? = null

    // Readings apply in the order they started: a poll that left before a write must not land over the read after it.
    private var started = 0L
    private var applied = 0L

    val state: StateFlow<AudioState> = flow.asStateFlow()

    init {
        scope.launch { merge(loudness.flow, eqEnabled.flow, gains.flow).collect { show() } }
        scope.launch {
            while (true) {
                read()
                delay(pollMillis)
            }
        }
    }

    /** The Try again under a failed first read. */
    fun load() {
        reading = Loadable.Loading
        show()
        scope.launch { read() }
    }

    fun onLoudness(mb: Int) {
        if (shown() == null) return
        val snapped = (mb / AudioSettings.LOUDNESS_STEP_MB) * AudioSettings.LOUDNESS_STEP_MB
        loudness.hold(snapped.coerceIn(AudioSettings.LOUDNESS_MIN_MB, AudioSettings.LOUDNESS_MAX_MB))
        show()
        pendingLoudness?.cancel()
        pendingLoudness = scope.launch {
            delay(debounceMillis)
            write(loudness, last = false) { speaker.setLoudness(it) }
        }
    }

    fun onLoudnessDone() {
        pendingLoudness?.cancel()
        scope.launch { write(loudness, last = true) { speaker.setLoudness(it) } }
    }

    fun onEqEnabled(enabled: Boolean) {
        if (shown() == null) return
        eqEnabled.hold(enabled)
        show()
        scope.launch { write(eqEnabled, last = true) { speaker.setEqEnabled(it) } }
    }

    fun onBand(index: Int, gainMb: Int) {
        val audio = shown() ?: return
        gains.hold(
            audio.bands.mapIndexed { i, band ->
                if (i == index) gainMb.coerceIn(audio.eqMinMb, audio.eqMaxMb) else band.gainMb
            },
        )
        show()
        pendingBands?.cancel()
        pendingBands = scope.launch {
            delay(debounceMillis)
            write(gains, last = false) { speaker.setEqBands(it) }
        }
    }

    fun onBandDone() {
        pendingBands?.cancel()
        scope.launch { write(gains, last = true) { speaker.setEqBands(it) } }
    }

    /** [last]: the finger is up, so the answer decides what the control shows; mid-drag it stays the finger's. */
    private suspend fun <T> write(held: HeldValue<T>, last: Boolean, send: suspend (T) -> Unit) {
        val value = (held.flow.value as? Held.Holding)?.value ?: return
        val taken = try {
            send(value)
            writeProblem = Problem.None
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: SpeakerException) {
            writeProblem = Problem.Failed(e)
            false
        }
        if (last) held.answered(value, taken)
        show()
        if (taken && last) read()
    }

    private suspend fun read() {
        val ticket = ++started
        try {
            val audio = speaker.settings().audio
            if (!applies(ticket)) return
            reading = Loadable.Loaded(audio)
            readProblem = Problem.None
            writeProblem = writeProblem.afterGoodRead()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SpeakerException) {
            if (!applies(ticket)) return
            if (reading is Loadable.Loaded) readProblem = Problem.Failed(e) else reading = Loadable.Failed(e)
        }
        show()
    }

    private fun applies(ticket: Long): Boolean {
        if (ticket <= applied) return false
        applied = ticket
        return true
    }

    private fun shown(): AudioSettings? {
        val audio = (reading as? Loadable.Loaded)?.value ?: return null
        val heldGains = gains.flow.value
        return audio.copy(
            loudnessMb = loudness.flow.value.or(audio.loudnessMb),
            eqEnabled = eqEnabled.flow.value.or(audio.eqEnabled),
            bands = if (heldGains is Held.Holding) {
                audio.bands.mapIndexed { i, band -> band.copy(gainMb = heldGains.value.getOrElse(i) { band.gainMb }) }
            } else {
                audio.bands
            },
        )
    }

    private fun show() {
        val audio = shown()
        flow.value = AudioState(
            audio = if (audio != null) Loadable.Loaded(audio) else reading,
            problem = if (writeProblem is Problem.Failed) writeProblem else readProblem,
        )
    }
}
