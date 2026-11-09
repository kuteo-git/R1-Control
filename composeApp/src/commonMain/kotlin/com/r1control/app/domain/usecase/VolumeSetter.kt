package com.r1control.app.domain.usecase

import com.r1control.app.domain.model.VolumeScale
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.repository.SpeakerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sends volume while a finger drags, without flooding the R1.
 *
 * Trailing throttle: the first value goes out at once, then at most one value
 * per [intervalMillis], and it is always the newest. Because the newest value
 * is never dropped, the last one sent is the release value: call [set] on
 * every drag move, and [release] when the finger lifts. Within one drag a value
 * equal to the one last sent is not sent again; a new drag sends even a level
 * sent before, because the speaker may have been moved since (by voice, the
 * web panel). Values are snapped to the speaker's [scale] here.
 */
class VolumeSetter(
    scope: CoroutineScope,
    private val speaker: SpeakerRepository,
    private val intervalMillis: Long = 150,
    private val onFailure: (SpeakerException) -> Unit,
) {
    private data class Wanted(val pct: Int, val drag: Int)

    private val pending = Channel<Wanted>(Channel.CONFLATED)
    private var drag = 0

    init {
        scope.launch {
            var lastSent: Wanted? = null
            for (wanted in pending) {
                if (wanted != lastSent) {
                    try {
                        speaker.setVolume(wanted.pct)
                        lastSent = wanted
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: SpeakerException) {
                        onFailure(e)
                    }
                    delay(intervalMillis)
                }
            }
        }
    }

    fun set(pct: Int, scale: VolumeScale) {
        pending.trySend(Wanted(scale.snap(pct), drag))
    }

    /** The finger lifted: what it sent so far stays the last value, and the next drag starts afresh. */
    fun release() {
        drag++
    }

    fun close() {
        pending.close()
    }
}
