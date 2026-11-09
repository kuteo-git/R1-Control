package com.r1control.app.domain.usecase

import com.r1control.app.domain.repository.SpeakerException
import com.r1control.app.domain.repository.SpeakerRepository
import kotlinx.coroutines.CancellationException

/** The answer to a command the speaker either took or did not. */
sealed interface CommandOutcome {
    data object Done : CommandOutcome
    data class Failed(val error: SpeakerException) : CommandOutcome
}

internal suspend inline fun command(block: () -> Unit): CommandOutcome = try {
    block()
    CommandOutcome.Done
} catch (e: CancellationException) {
    throw e
} catch (e: SpeakerException) {
    CommandOutcome.Failed(e)
}

sealed interface SendOutcome {
    /** Nothing but whitespace: nothing was sent and the field should be left alone. */
    data object Blank : SendOutcome
    /** Sent; [text] is what went out (trimmed). */
    data class Sent(val text: String) : SendOutcome
    data class Failed(val error: SpeakerException) : SendOutcome
}

/** Says a typed line to the assistant, as if spoken. */
class SendChat(private val speaker: SpeakerRepository) {
    suspend operator fun invoke(text: String): SendOutcome {
        val line = text.trim()
        if (line.isEmpty()) return SendOutcome.Blank
        return when (val outcome = command { speaker.say(line) }) {
            CommandOutcome.Done -> SendOutcome.Sent(line)
            is CommandOutcome.Failed -> SendOutcome.Failed(outcome.error)
        }
    }
}
