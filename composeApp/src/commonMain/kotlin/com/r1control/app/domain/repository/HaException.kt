package com.r1control.app.domain.repository

/**
 * Why a call to Home Assistant did not go through. None of them holds a reply:
 * a reply can carry the household's entity names and tokens.
 */
sealed class HaException(message: String) : Exception(message) {
    /** There is no open, authenticated link right now. */
    class NotConnected : HaException("not connected to Home Assistant")

    /** Home Assistant answered and said no; [code] is its short error code (`not_found`, `unknown_error`), empty when it gave none. */
    class Refused(val code: String) : HaException("Home Assistant refused: $code")

    /** The link answered nothing in time. */
    class TimedOut : HaException("Home Assistant did not answer")
}
