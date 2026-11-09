package com.r1control.app.domain.model

/**
 * What the assistant is doing. A news bulletin and a conversation both report
 * `Speaking`; the card does not tell them apart (decided in the spec).
 */
enum class VoiceActivity { Idle, Listening, Speaking }

enum class Sender { User, Assistant }

data class ChatMessage(val sender: Sender, val text: String, val timeMillis: Long)

/**
 * The R1's listening controls, as `/api/state` reports them. `wake_enabled`
 * present means this R1 build has both `/api/voice/sleep` and
 * `/api/wake/enable`; absent means an older build with neither. Decided once,
 * in the mapper, and named.
 */
sealed interface VoiceControls {
    /** An older R1 build: no wake-word switch and no "end the conversation" is drawn. */
    data object Absent : VoiceControls

    data class Present(val wakeWordOn: Boolean) : VoiceControls
}
