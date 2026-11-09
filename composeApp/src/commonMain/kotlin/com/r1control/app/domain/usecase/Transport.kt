package com.r1control.app.domain.usecase

import com.r1control.app.domain.model.NowPlaying
import com.r1control.app.domain.repository.SpeakerRepository

/**
 * The player's buttons, as one decision: what a press does depends on what the card shows.
 * Next is not here: `MusicStore.next` is the one Next, for both cards.
 */
class Transport(private val speaker: SpeakerRepository) {
    /** Play/pause: pauses a playing song, resumes a paused one, does nothing while it downloads. */
    suspend fun toggle(phase: NowPlaying.Phase): CommandOutcome = when (phase) {
        NowPlaying.Phase.Playing -> command { speaker.pause() }
        NowPlaying.Phase.Paused -> command { speaker.resume() }
        is NowPlaying.Phase.Downloading -> CommandOutcome.Done
    }

    suspend fun stop(): CommandOutcome = command { speaker.stop() }
    suspend fun seek(positionS: Int): CommandOutcome = command { speaker.seek(positionS.coerceAtLeast(0)) }
}
