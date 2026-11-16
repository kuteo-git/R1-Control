package com.r1control.app.domain.usecase

import com.r1control.app.domain.model.BluetoothState
import com.r1control.app.domain.model.SpeakerSettings
import com.r1control.app.domain.model.SpeakerSnapshot
import com.r1control.app.domain.repository.SpeakerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** Whether the app is on screen. The platform's lifecycle writes it, through the app shell. */
class Foreground {
    private val shown = MutableStateFlow(true)

    /** The same fact as a flow, for what must act when it changes rather than wait for it (the Home Assistant link). */
    val isShown: StateFlow<Boolean> = shown.asStateFlow()

    fun set(isShown: Boolean) {
        shown.value = isShown
    }

    suspend fun awaitShown() {
        shown.first { it }
    }
}

/**
 * The speaker, read only while the app is on screen. Every loop that polls (the
 * shared feed, the Bluetooth, Audio and News pages) waits here while the phone is locked or the
 * app is behind another, and reads at once when it comes back, so no screen has
 * to remember to stop its own loop. Commands are a person's taps and always go.
 */
class ForegroundSpeaker(
    private val speaker: SpeakerRepository,
    private val foreground: Foreground,
) : SpeakerRepository by speaker {
    override suspend fun snapshot(): SpeakerSnapshot {
        foreground.awaitShown()
        return speaker.snapshot()
    }

    override suspend fun settings(): SpeakerSettings {
        foreground.awaitShown()
        return speaker.settings()
    }

    override suspend fun bluetooth(): BluetoothState {
        foreground.awaitShown()
        return speaker.bluetooth()
    }
}
