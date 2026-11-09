package com.r1control.app.domain.repository

/**
 * Why a call to the speaker did not give an answer. Thrown by
 * [SpeakerRepository], carried by use cases, mapped to words by the screens.
 * None of them holds a response body: a body can carry secrets.
 */
sealed class SpeakerException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)

    /** No address has been saved yet. */
    class NotConfigured : SpeakerException("no R1 address saved")

    /** The request never got an answer: wrong address, off the wifi, timed out. */
    class Unreachable(cause: Throwable) :
        SpeakerException("could not reach the R1: ${cause.message}", cause)

    /** The R1 answered and said no; [reason] is its own short error, empty when it gave none. */
    class Refused(val reason: String) : SpeakerException("the R1 refused: $reason")

    /** The R1 answered with something this app cannot read. [what] names the call, never the body. */
    class BadReply(val what: String) : SpeakerException("unreadable reply to $what")
}
