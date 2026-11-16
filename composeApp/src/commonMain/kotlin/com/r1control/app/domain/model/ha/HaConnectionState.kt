package com.r1control.app.domain.model.ha

/** Where the link to Home Assistant stands. [AuthInvalid] is final until a new token is typed; the others retry. */
enum class HaConnectionState {
    Disconnected,
    Connecting,
    Connected,
    AuthInvalid,
    Unreachable,
}
