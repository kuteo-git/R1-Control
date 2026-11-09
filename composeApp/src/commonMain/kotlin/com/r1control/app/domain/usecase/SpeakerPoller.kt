package com.r1control.app.domain.usecase

import com.r1control.app.domain.model.MediaStatus
import com.r1control.app.domain.model.SpeakerSnapshot
import com.r1control.app.domain.model.VoiceActivity
import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.repository.SpeakerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** One tick of the poller. A failure does not end the flow: the next tick tries again. */
sealed interface PollResult {
    data class Fresh(val snapshot: SpeakerSnapshot) : PollResult
    data class Failed(val error: SpeakerException) : PollResult
}

/**
 * How long to wait before reading the speaker again, a pure function so the
 * poller and its tests agree: fast while the seek bar moves or the assistant
 * talks, slow when nothing changes, slower still after a failure.
 */
fun pollMillis(last: PollResult?): Long = when (last) {
    null -> 0L
    is PollResult.Failed -> POLL_AFTER_FAILURE_MS
    is PollResult.Fresh -> {
        val s = last.snapshot
        when {
            s.media.status == MediaStatus.Playing || s.media.status == MediaStatus.Downloading -> POLL_ACTIVE_MS
            s.voiceAwake || s.voice != VoiceActivity.Idle || s.bluetooth.isOpenForPairing -> POLL_ACTIVE_MS
            else -> POLL_IDLE_MS
        }
    }
}

const val POLL_ACTIVE_MS = 1_000L
const val POLL_IDLE_MS = 3_000L
const val POLL_AFTER_FAILURE_MS = 5_000L

/**
 * Reads the speaker over and over for whoever is collecting.
 *
 * The flow is cold: it reads when collected and stops when the collector is
 * gone, so a screen that shares it with `stateIn(scope, WhileSubscribed(…))`
 * polls only while somebody looks. Reads never overlap: the next waits for the
 * last to finish and then for [pollMillis].
 */
class SpeakerPoller(private val read: suspend () -> PollResult) {
    constructor(speaker: SpeakerRepository) : this({ speaker.read() })

    fun snapshots(): Flow<PollResult> = flow {
        var last: PollResult? = null
        while (true) {
            delay(pollMillis(last))
            val result = read()
            last = result
            emit(result)
        }
    }
}

/** One reading of the speaker, a failure as a value. */
suspend fun SpeakerRepository.read(): PollResult = try {
    PollResult.Fresh(snapshot())
} catch (e: CancellationException) {
    throw e
} catch (e: SpeakerException) {
    PollResult.Failed(e)
}
